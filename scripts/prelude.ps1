$ErrorActionPreference = 'Stop'
$t = [char]9

function Emit([string]$line) {
    $enc = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::AppendAllText($env:KEYV_OUT, ($line + [Environment]::NewLine), $enc)
}

function Fail([string]$msg) {
    if ($null -eq $msg) { $msg = 'Erreur inconnue.' }
    $clean = $msg.Replace("`r", ' ').Replace("`n", ' ')
    Emit ('ERR' + $t + $clean)
    exit 1
}

function Require-HyperV {
    $vmms = Get-Service -Name vmms -ErrorAction SilentlyContinue
    if ($null -eq $vmms) { Fail 'NO_HYPERV' }
    if ($vmms.Status -ne 'Running') { Fail 'HYPERV_STOPPED' }
    Import-Module Hyper-V -ErrorAction Stop
}

function Get-KeyVVm([string]$vmName) {
    if ([string]::IsNullOrWhiteSpace($vmName)) { Fail 'NO_VM' }
    return Get-VM -Name $vmName
}

function Test-KeyVUsbDisk($disk) {
    if ($null -eq $disk) { return $false }
    if ([string]$disk.BusType -eq 'USB') { return $true }
    $wmi = Get-CimInstance -ClassName Win32_DiskDrive -Filter ("Index=" + [int]$disk.Number) -ErrorAction SilentlyContinue
    if ($null -eq $wmi) { return $false }
    $pnp = [string]$wmi.PNPDeviceID
    $iface = [string]$wmi.InterfaceType
    if ($iface -eq 'USB') { return $true }
    if ($pnp -like 'USB*' -or $pnp -like '*USBSTOR*') { return $true }
    return $false
}

function Test-SameDrive([string]$systemDrive, [string]$letter) {
    if ([string]::IsNullOrWhiteSpace($systemDrive) -or [string]::IsNullOrWhiteSpace($letter)) { return $false }
    $wanted = $systemDrive.TrimEnd('\').ToUpperInvariant()
    $drive = $letter.Substring(0, 1).ToUpperInvariant() + ':'
    return $drive -eq $wanted
}

function Test-RunningWindowsDisk($disk) {
    $system = $env:SystemDrive
    foreach ($part in @(Get-Partition -DiskNumber $disk.Number -ErrorAction SilentlyContinue)) {
        if (Test-SameDrive $system ([string]$part.DriveLetter)) { return $true }
    }
    return $false
}

function Test-GptStyle([string]$style) {
    return $style -eq 'GPT'
}

function Test-BootableMarkers([bool]$isBoot, [bool]$isSystem, [bool]$ventoy, [bool]$active, [string]$gptType, [string]$partType) {
    if ($ventoy -or $isBoot -or $isSystem -or $active) { return $true }
    if (-not [string]::IsNullOrWhiteSpace($gptType) -and $gptType.ToLowerInvariant().Contains('c12a7328-f81f-11d2-ba4b-00a0c93ec93b')) { return $true }
    if ($partType -match '(?i)^(System|EFI)$') { return $true }
    return $false
}

function Test-KeyVBootableDisk($disk, [bool]$ventoy) {
    if ($ventoy) { return $true }
    if ($null -eq $disk) { return $false }
    if (Test-GptStyle ([string]$disk.PartitionStyle)) { return $true }
    $isBoot = $false
    $isSystem = $false
    try { $isBoot = [bool]$disk.IsBoot } catch { $isBoot = $false }
    try { $isSystem = [bool]$disk.IsSystem } catch { $isSystem = $false }
    if (Test-BootableMarkers $isBoot $isSystem $false $false '' '') { return $true }
    foreach ($p in @(Get-Partition -DiskNumber $disk.Number -ErrorAction SilentlyContinue)) {
        $active = $false
        try { $active = [bool]$p.IsActive } catch { $active = $false }
        $gpt = ''
        try { $gpt = [string]$p.GptType } catch { $gpt = '' }
        $type = ''
        try { $type = [string]$p.Type } catch { $type = '' }
        if (Test-BootableMarkers $false $false $false $active $gpt $type) { return $true }
    }
    return $false
}

function Assert-UsbDisk([int]$diskNumber) {
    $disk = Get-Disk -Number $diskNumber
    if (-not (Test-KeyVUsbDisk $disk)) { Fail 'NOT_USB' }
    if (Test-RunningWindowsDisk $disk) { Fail 'IS_SYSTEM' }
    return $disk
}

function Dismount-UsbVolumes([int]$diskNumber) {
    $parts = @(Get-Partition -DiskNumber $diskNumber -ErrorAction SilentlyContinue)
    foreach ($part in $parts) {
        foreach ($path in @($part.AccessPaths)) {
            if ([string]::IsNullOrWhiteSpace([string]$path)) { continue }
            if ([string]$path -notmatch '^[A-Za-z]:\\$') { continue }
            $mount = ([string]$path).TrimEnd('\')
            & mountvol.exe $mount /p | Out-Null
        }
    }
}

function Invoke-Diskpart([string]$script) {
    $file = [System.IO.Path]::GetTempFileName()
    try {
        [System.IO.File]::WriteAllText($file, $script, [System.Text.Encoding]::ASCII)
        & diskpart.exe /s $file | Out-Null
    } finally {
        Remove-Item -LiteralPath $file -Force -ErrorAction SilentlyContinue
    }
}

function Remove-UsbMounts([int]$diskNumber) {
    $parts = @(Get-Partition -DiskNumber $diskNumber -ErrorAction SilentlyContinue)
    foreach ($part in $parts) {
        foreach ($path in @($part.AccessPaths)) {
            $text = [string]$path
            if ($text -notmatch '^[A-Za-z]:\\$') { continue }
            if (Test-SameDrive $env:SystemDrive $text.Substring(0, 1)) { continue }
            try {
                Remove-PartitionAccessPath -DiskNumber $diskNumber -PartitionNumber $part.PartitionNumber -AccessPath $text -ErrorAction Stop
            } catch { }
            & mountvol.exe ($text.TrimEnd('\')) /p | Out-Null
        }
    }
}

function Set-UsbOffline([int]$diskNumber) {
    $current = Get-Disk -Number $diskNumber
    if ($current.IsOffline) { return }
    Remove-UsbMounts $diskNumber
    Dismount-UsbVolumes $diskNumber
    if ($current.IsReadOnly) {
        try { Set-Disk -Number $diskNumber -IsReadOnly $false -ErrorAction Stop } catch { }
    }
    $script = "select disk " + $diskNumber + "`r`nattributes disk clear readonly`r`noffline disk`r`n"
    Invoke-Diskpart $script
    $ready = $false
    $last = ''
    for ($try = 0; $try -lt 8; $try++) {
        try {
            Set-Disk -Number $diskNumber -IsOffline $true -ErrorAction Stop
        } catch {
            $last = $_.Exception.Message
        }
        Start-Sleep -Milliseconds 300
        $again = Get-Disk -Number $diskNumber
        if ($again.IsOffline) {
            $ready = $true
            break
        }
        Remove-UsbMounts $diskNumber
        Dismount-UsbVolumes $diskNumber
        Invoke-Diskpart $script
    }
    if ($ready) { return }
    if ($last -match 'denied|refus') { Fail 'ACCESS_DENIED' }
    if ($last -match 'not supported|80070032|removable|amovible|prise en charge') {
        Emit ('LOG' + $t + 'STILL_ONLINE')
        return
    }
    if ($last -match 'in use|being used|utilis|autre processus|verrou') { Fail 'IN_USE' }
    if ([string]::IsNullOrWhiteSpace($last)) { Fail 'IN_USE' }
    Fail $last
}

function Set-UsbOnline([int]$diskNumber) {
    try {
        Set-Disk -Number $diskNumber -IsOffline $false
        return $true
    } catch {
        return $false
    }
}

function Open-KeyVConsole([string]$vmName) {
    $vmconnect = Join-Path (Join-Path $env:SystemRoot 'System32') 'vmconnect.exe'
    if (-not (Test-Path -LiteralPath $vmconnect)) {
        Emit ('LOG' + $t + 'NO_VMCONNECT')
        return
    }
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $vmconnect
    $psi.Arguments = 'localhost "' + ($vmName -replace '"', '') + '"'
    $psi.UseShellExecute = $true
    [void][System.Diagnostics.Process]::Start($psi)
    Emit ('LOG' + $t + 'CONSOLE_OPENED')
}
