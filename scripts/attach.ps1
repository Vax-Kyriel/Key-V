function Slot-Key([int]$controller, [int]$location) {
    return ([string]$controller + ':' + [string]$location)
}

function Get-FreeIdeSlot([string]$Name) {
    $used = @{}
    foreach ($d in @(Get-VMHardDiskDrive -VMName $Name -ControllerType IDE -ErrorAction SilentlyContinue)) {
        $used[(Slot-Key ([int]$d.ControllerNumber) ([int]$d.ControllerLocation))] = $true
    }
    foreach ($d in @(Get-VMDvdDrive -VMName $Name -ErrorAction SilentlyContinue)) {
        $used[(Slot-Key ([int]$d.ControllerNumber) ([int]$d.ControllerLocation))] = $true
    }
    foreach ($slot in @('0:1', '1:0', '1:1')) {
        if (-not $used.ContainsKey($slot)) { return $slot }
    }
    return $null
}

function Move-IdeDvd([string]$Name, $dvd) {
    $free = Get-FreeIdeSlot $Name
    if ($null -eq $free) { throw 'IDE_FULL' }
    $parts = $free.Split(':')
    $path = [string]$dvd.Path
    Remove-VMDvdDrive -VMName $Name -ControllerNumber ([int]$dvd.ControllerNumber) -ControllerLocation ([int]$dvd.ControllerLocation)
    if ([string]::IsNullOrWhiteSpace($path)) {
        Add-VMDvdDrive -VMName $Name -ControllerNumber ([int]$parts[0]) -ControllerLocation ([int]$parts[1]) | Out-Null
    } else {
        Add-VMDvdDrive -VMName $Name -ControllerNumber ([int]$parts[0]) -ControllerLocation ([int]$parts[1]) -Path $path | Out-Null
    }
    Emit ('LOG' + $t + 'DVD_MOVED')
}

function Set-Gen1BootDisk([string]$Name, [int]$Number) {
    $ide = @(Get-VMHardDiskDrive -VMName $Name -ControllerType IDE -ErrorAction SilentlyContinue)
    $dvds = @(Get-VMDvdDrive -VMName $Name -ErrorAction SilentlyContinue)
    $slot00 = $null
    $slot00Kind = ''
    foreach ($d in $dvds) {
        if ([int]$d.ControllerNumber -eq 0 -and [int]$d.ControllerLocation -eq 0) {
            $slot00 = $d
            $slot00Kind = 'dvd'
        }
    }
    foreach ($d in $ide) {
        if ([int]$d.ControllerNumber -eq 0 -and [int]$d.ControllerLocation -eq 0) {
            $slot00 = $d
            $slot00Kind = 'disk'
        }
    }

    if ($slot00Kind -eq 'dvd') {
        Move-IdeDvd $Name $slot00
        $slot00Kind = ''
        $slot00 = $null
    }

    if ($slot00Kind -eq '') {
        Add-KeyVPhysicalDisk $Name 'IDE' 0 0 $Number
        return
    }

    $numberText = [string]$slot00.DiskNumber
    if (-not [string]::IsNullOrWhiteSpace($numberText)) { throw 'IDE_PHYSICAL' }
    $vhdPath = [string]$slot00.Path
    if ([string]::IsNullOrWhiteSpace($vhdPath)) { throw 'IDE_FULL' }

    $free = Get-FreeIdeSlot $Name
    if ($null -eq $free) { throw 'IDE_FULL' }
    $parts = $free.Split(':')

    Remove-VMHardDiskDrive -VMName $Name -ControllerType IDE -ControllerNumber 0 -ControllerLocation 0
    try {
        Add-KeyVPhysicalDisk $Name 'IDE' 0 0 $Number
    } catch {
        try {
            Add-VMHardDiskDrive -VMName $Name -ControllerType IDE -ControllerNumber 0 -ControllerLocation 0 -Path $vhdPath
        } catch { }
        throw
    }
    try {
        Add-VMHardDiskDrive -VMName $Name -ControllerType IDE -ControllerNumber ([int]$parts[0]) -ControllerLocation ([int]$parts[1]) -Path $vhdPath
        Emit ('LOG' + $t + 'MOVED_VHD' + $t + $free + $t + $vhdPath)
    } catch {
        Emit ('LOG' + $t + 'VHD_REATTACH_FAIL' + $t + $vhdPath)
    }
}

function Get-FreeScsiSlot([string]$Name) {
    $controllers = @(Get-VMScsiController -VMName $Name -ErrorAction SilentlyContinue)
    if ($controllers.Count -eq 0) {
        Add-VMScsiController -VMName $Name | Out-Null
        $controllers = @(Get-VMScsiController -VMName $Name)
    }
    foreach ($ctrl in $controllers) {
        $used = @{}
        $ctrlNo = [int]$ctrl.ControllerNumber
        foreach ($d in @(Get-VMHardDiskDrive -VMName $Name -ControllerType SCSI -ControllerNumber $ctrlNo -ErrorAction SilentlyContinue)) {
            $used[[int]$d.ControllerLocation] = $true
        }
        foreach ($d in @(Get-VMDvdDrive -VMName $Name -ErrorAction SilentlyContinue)) {
            if ([string]$d.ControllerType -eq 'SCSI' -and [int]$d.ControllerNumber -eq $ctrlNo) {
                $used[[int]$d.ControllerLocation] = $true
            }
        }
        for ($loc = 0; $loc -lt 64; $loc++) {
            if (-not $used.ContainsKey($loc)) { return (Slot-Key $ctrlNo $loc) }
        }
    }
    Add-VMScsiController -VMName $Name | Out-Null
    $controllers = @(Get-VMScsiController -VMName $Name)
    return (Slot-Key ([int]$controllers[-1].ControllerNumber) 0)
}

function Add-KeyVPhysicalDisk([string]$Name, [string]$ControllerType, [int]$Controller, [int]$Location, [int]$Number) {
    try {
        Add-VMHardDiskDrive -VMName $Name -ControllerType $ControllerType -ControllerNumber $Controller -ControllerLocation $Location -DiskNumber $Number
        return
    } catch {
        $detail = $_.Exception.Message
        if ($detail -notmatch 'in use|being used|utilis|online|en ligne|verrou|not supported|80070032|removable|amovible|prise en charge') { throw }
        Set-UsbOffline $Number
        Add-VMHardDiskDrive -VMName $Name -ControllerType $ControllerType -ControllerNumber $Controller -ControllerLocation $Location -DiskNumber $Number
    }
}

function Add-ScsiDisk([string]$Name, [int]$Number) {
    $slot = Get-FreeScsiSlot $Name
    $parts = $slot.Split(':')
    Add-KeyVPhysicalDisk $Name 'SCSI' ([int]$parts[0]) ([int]$parts[1]) $Number
    return $slot
}

function Test-ControllerRefusal([string]$message) {
    return $message -match 'not supported|80070032|removable|amovible|prise en charge|n''est pas prise'
}

function Set-Gen1BootOrder([string]$Name) {
    $bios = Get-VMBios -VMName $Name
    $order = @($bios.StartupOrder | ForEach-Object { [string]$_ })
    if ($order.Count -eq 0 -or $order[0] -eq 'IDE') { return }
    $rest = @($order | Where-Object { $_ -ne 'IDE' })
    Set-VMBios -VMName $Name -StartupOrder (@('IDE') + $rest)
}

function Set-Gen2BootFirst([string]$Name, [string]$slot) {
    $parts = $slot.Split(':')
    $controller = [int]$parts[0]
    $location = [int]$parts[1]
    $fresh = $null
    for ($try = 0; $try -lt 6; $try++) {
        Start-Sleep -Milliseconds 250
        $fresh = @(Get-VMHardDiskDrive -VMName $Name -ControllerType SCSI -ControllerNumber $controller -ControllerLocation $location) | Select-Object -First 1
        if ($null -ne $fresh) { break }
    }
    if ($null -eq $fresh) { throw 'NOT_ADDED' }
    for ($try = 0; $try -lt 3; $try++) {
        $firmware = Get-VMFirmware -VMName $Name
        $order = @($firmware.BootOrder)
        $match = $null
        foreach ($entry in $order) {
            $device = $entry.Device
            if ($null -eq $device) { continue }
            if ($device.GetType().Name -notmatch 'HardDisk') { continue }
            if ([int]$device.ControllerNumber -eq $controller -and [int]$device.ControllerLocation -eq $location) {
                $match = $entry
                break
            }
        }
        if ($null -ne $match) {
            $rest = New-Object System.Collections.Generic.List[object]
            foreach ($entry in $order) {
                if (-not [object]::ReferenceEquals($entry, $match)) { [void]$rest.Add($entry) }
            }
            Set-VMFirmware -VMName $Name -BootOrder (@($match) + $rest.ToArray())
            return
        }
        try {
            Set-VMFirmware -VMName $Name -FirstBootDevice $fresh
            return
        } catch {
            if ($try -ge 2) { throw }
            Start-Sleep -Milliseconds 300
        }
    }
}

function Disable-KeyVSecureBoot([string]$Name, [bool]$force) {
    if (-not $force -and $env:KEYV_DISABLE_SB -ne '1') { return }
    $fw = Get-VMFirmware -VMName $Name
    $sb = [string]$fw.SecureBoot
    $template = ([string]$fw.SecureBootTemplate).Replace([char]9, ' ')
    Emit ('PREVSB' + $t + $sb + $t + $template)
    if ($sb -eq 'On') {
        $script:prevSb = $sb
        $script:prevTemplate = $template
        Set-VMFirmware -VMName $Name -EnableSecureBoot Off
        Emit ('LOG' + $t + 'SB_OFF')
    }
}

$script:prevSb = $null
$script:prevTemplate = $null
try {
    Require-HyperV
    if ($env:KEYV_DISK -notmatch '^\d+$') { Fail 'BAD_DISK' }
    $diskNumber = [int]$env:KEYV_DISK
    $vmName = $env:KEYV_VM
    $vm = Get-KeyVVm $vmName
    $disk = Assert-UsbDisk $diskNumber

    if ([string]$vm.State -eq 'Saved') {
        Remove-VMSavedState -VMName $vmName
        Emit ('LOG' + $t + 'SAVED_CLEARED')
        $vm = Get-KeyVVm $vmName
    }
    if ([string]$vm.State -ne 'Off') { Fail 'VM_NOT_OFF' }

    $owners = New-Object System.Collections.Generic.List[string]
    foreach ($other in @(Get-VM)) {
        foreach ($drive in @(Get-VMHardDiskDrive -VMName $other.Name)) {
            if ([string]$drive.DiskNumber -eq [string]$diskNumber) { [void]$owners.Add([string]$other.Name) }
        }
    }
    if ($owners.Count -gt 0) { Fail ('ALREADY_ATTACHED' + $t + ($owners -join ', ')) }

    Emit ('LOG' + $t + 'OFFLINE')
    Set-UsbOffline $diskNumber

    $added = $false
    $addedSlot = $null
    try {
        $gpt = Test-GptStyle ([string]$disk.PartitionStyle)
        if ([int]$vm.Generation -eq 2) {
            Disable-KeyVSecureBoot $vmName $gpt
            $addedSlot = Add-ScsiDisk $vmName $diskNumber
            $added = $true
            try {
                Set-Gen2BootFirst $vmName $addedSlot
                if ($gpt) {
                    Emit ('LOG' + $t + 'ATTACHED_GPT')
                } else {
                    Emit ('LOG' + $t + 'MBR_GEN2')
                    Emit ('LOG' + $t + 'ATTACHED_SCSI')
                }
            } catch {
                $detail = $_.Exception.Message.Replace([char]9, ' ').Replace([char]10, ' ').Replace([char]13, ' ')
                Emit ('LOG' + $t + 'BOOT_SKIPPED' + $t + $detail)
                if ($gpt) { Emit ('LOG' + $t + 'ATTACHED_GPT') } else { Emit ('LOG' + $t + 'ATTACHED_SCSI') }
            }
        } else {
            $onScsi = $false
            try {
                Set-Gen1BootDisk $vmName $diskNumber
                $added = $true
                $addedSlot = '0:0'
            } catch {
                $detail = $_.Exception.Message
                if ($detail -match 'IDE_PHYSICAL') { throw $detail }
                if ($detail -notmatch 'IDE_DVD|IDE_FULL' -and -not (Test-ControllerRefusal $detail)) { throw }
                $addedSlot = Add-ScsiDisk $vmName $diskNumber
                $added = $true
                $onScsi = $true
                Emit ('LOG' + $t + 'ATTACHED_SCSI_GEN1')
            }
            if (-not $onScsi) {
                try {
                    Set-Gen1BootOrder $vmName
                } catch {
                    $detail = $_.Exception.Message.Replace([char]9, ' ').Replace([char]10, ' ').Replace([char]13, ' ')
                    Emit ('LOG' + $t + 'BIOS_KEPT' + $t + $detail)
                }
                if ($gpt) { Emit ('LOG' + $t + 'ATTACHED_GPT_IDE') } else { Emit ('LOG' + $t + 'ATTACHED_IDE') }
            }
        }
    } catch {
        if ($script:prevSb -eq 'On') {
            $tpl = $script:prevTemplate
            if ([string]::IsNullOrWhiteSpace($tpl)) { $tpl = 'MicrosoftWindows' }
            try { Set-VMFirmware -VMName $vmName -EnableSecureBoot On -SecureBootTemplate $tpl } catch { }
        }
        if ($added) {
            Get-VMHardDiskDrive -VMName $vmName | Where-Object { [string]$_.DiskNumber -eq [string]$diskNumber } | Remove-VMHardDiskDrive
        }
        Set-UsbOnline $diskNumber | Out-Null
        $m = $_.Exception.Message
        if ($m -match 'checkpoint|point de contrôle|snapshot') { Fail 'CHECKPOINTS' }
        if (Test-ControllerRefusal $m) { Fail 'REMOVABLE' }
        if ($m -match 'denied|refus') { Fail 'ACCESS_DENIED' }
        Fail $m
    }

    Emit ('LOG' + $t + 'CHECKPOINT')
    if ($env:KEYV_START -eq '1') {
        try {
            Start-VM -Name $vmName
            Emit ('LOG' + $t + 'VM_STARTED')
            Open-KeyVConsole $vmName
        } catch {
            $detail = $_.Exception.Message.Replace([char]9, ' ').Replace([char]10, ' ').Replace([char]13, ' ')
            Emit ('LOG' + $t + 'START_FAIL' + $t + $detail)
        }
    }
    Emit 'OK'
} catch {
    $m = $_.Exception.Message
    if ($m -match 'checkpoint|point de contrôle|snapshot') { Fail 'CHECKPOINTS' }
    if (Test-ControllerRefusal $m) { Fail 'REMOVABLE' }
    if ($m -match 'denied|refus') { Fail 'ACCESS_DENIED' }
    Fail $m
}
