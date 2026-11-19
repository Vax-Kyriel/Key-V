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

function Assert-UsbDisk([int]$diskNumber) {
    $disk = Get-Disk -Number $diskNumber
    if (-not (Test-KeyVUsbDisk $disk)) { Fail 'NOT_USB' }
    if ($disk.IsBoot -or $disk.IsSystem) { Fail 'IS_SYSTEM' }
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

function Set-UsbOffline([int]$diskNumber) {
    Dismount-UsbVolumes $diskNumber
    $disk = Get-Disk -Number $diskNumber
    if ($disk.IsReadOnly) {
        try { Set-Disk -Number $diskNumber -IsReadOnly $false -ErrorAction Stop } catch { }
    }
    Invoke-Diskpart ("select disk " + $diskNumber + "`r`nattributes disk clear readonly`r`noffline disk`r`n")
    $ready = $false
    $last = ''
    for ($try = 0; $try -lt 6; $try++) {
        try {
            Set-Disk -Number $diskNumber -IsOffline $true -ErrorAction Stop
        } catch {
            $last = $_.Exception.Message
        }
        Start-Sleep -Milliseconds 350
        $again = Get-Disk -Number $diskNumber
        if ($again.IsOffline) {
            $ready = $true
            break
        }
        Dismount-UsbVolumes $diskNumber
    }
    if (-not $ready) {
        if ($last -match 'denied|refus') { Fail 'ACCESS_DENIED' }
        if ($last -match 'in use|being used|utilis|autre processus|verrou') { Fail 'IN_USE' }
        if ([string]::IsNullOrWhiteSpace($last)) { Fail 'IN_USE' }
        Fail $last
    }
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
