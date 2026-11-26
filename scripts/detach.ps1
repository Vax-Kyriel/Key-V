try {
    Require-HyperV
    if ($env:KEYV_DISK -notmatch '^\d+$') { Fail 'BAD_DISK' }
    $diskNumber = [int]$env:KEYV_DISK
    $vmName = $env:KEYV_VM
    $vm = Get-KeyVVm $vmName
    if ($vm.State -ne 'Off') { Fail 'VM_NOT_OFF' }

    $drive = @(Get-VMHardDiskDrive -VMName $vmName | Where-Object { $_.DiskNumber -eq $diskNumber }) | Select-Object -First 1
    if ($null -eq $drive) { Fail 'NOT_ATTACHED' }

    if ($vm.Generation -eq 2) {
        $others = @(Get-VMHardDiskDrive -VMName $vmName | Where-Object { $_.DiskNumber -ne $diskNumber })
        if ($others.Count -eq 0) {
            $others = @(Get-VMDvdDrive -VMName $vmName)
        }
        if ($others.Count -gt 0) {
            try { Set-VMFirmware -VMName $vmName -FirstBootDevice $others[0] } catch { }
        }
    }

    $drive | Remove-VMHardDiskDrive
    $online = Set-UsbOnline $diskNumber

    if ($vm.Generation -eq 1) {
        try {
            $ide = @(Get-VMHardDiskDrive -VMName $vmName -ControllerType IDE)
            $at00 = $ide | Where-Object { $_.ControllerNumber -eq 0 -and $_.ControllerLocation -eq 0 } | Select-Object -First 1
            if ($null -eq $at00) {
                $vhd = $ide | Where-Object { $_.Path } | Select-Object -First 1
                if ($null -ne $vhd) {
                    $path = [string]$vhd.Path
                    Remove-VMHardDiskDrive -VMName $vmName -ControllerType IDE -ControllerNumber $vhd.ControllerNumber -ControllerLocation $vhd.ControllerLocation
                    Add-VMHardDiskDrive -VMName $vmName -ControllerType IDE -ControllerNumber 0 -ControllerLocation 0 -Path $path
                    Emit ('LOG' + $t + 'VHD_RESTORED')
                }
            }
        } catch {
            $detail = $_.Exception.Message.Replace([char]9, ' ').Replace([char]10, ' ').Replace([char]13, ' ')
            Emit ('LOG' + $t + 'VHD_RESTORE_FAIL' + $t + $detail)
        }
    }

    if ($online) { Emit ('LOG' + $t + 'DETACHED') } else { Emit ('LOG' + $t + 'ONLINE_FAIL') }

    if ($vm.Generation -eq 2 -and $env:KEYV_RESTORE_SB -eq 'On') {
        try {
            $template = $env:KEYV_RESTORE_TEMPLATE
            if ([string]::IsNullOrWhiteSpace($template)) { $template = 'MicrosoftWindows' }
            Set-VMFirmware -VMName $vmName -EnableSecureBoot On -SecureBootTemplate $template
            Emit ('LOG' + $t + 'SB_ON' + $t + $template)
        } catch {
            $detail = $_.Exception.Message.Replace([char]9, ' ').Replace([char]10, ' ').Replace([char]13, ' ')
            Emit ('LOG' + $t + 'SB_RESTORE_FAIL' + $t + $detail)
        }
    }
    Emit 'OK'
} catch {
    $m = $_.Exception.Message
    if ($m -match 'denied|refus') { Fail 'ACCESS_DENIED' }
    Fail $m
}
