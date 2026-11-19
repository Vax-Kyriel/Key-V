try {
    $reg = ('HKLM:/SOFTWARE/Microsoft/Windows NT/CurrentVersion').Replace([char]47, [char]92)
    $edition = [string](Get-ItemProperty -Path $reg -Name EditionID).EditionID
    Emit ('META' + $t + 'EDITION' + $t + $edition)

    $principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
    $admin = $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    Emit ('META' + $t + 'ADMIN' + $t + $admin.ToString().ToLower())

    $disks = @(Get-Disk | Where-Object {
        $_.Size -gt 0 -and $_.OperationalStatus -ne 'No Media' -and (Test-KeyVUsbDisk $_)
    })
    foreach ($disk in $disks) {
        $labels = New-Object System.Collections.Generic.List[string]
        $letters = New-Object System.Collections.Generic.List[string]
        $ventoy = $false
        $parts = @(Get-Partition -DiskNumber $disk.Number -ErrorAction SilentlyContinue)
        foreach ($p in $parts) {
            $vol = $null
            try { $vol = Get-Volume -Partition $p -ErrorAction SilentlyContinue } catch { $vol = $null }
            if ($null -eq $vol) { continue }
            $label = [string]$vol.FileSystemLabel
            if (-not [string]::IsNullOrWhiteSpace($label)) { [void]$labels.Add($label) }
            if ($vol.DriveLetter) { [void]$letters.Add([string]$vol.DriveLetter) }
            if ($label -match '^(?i)VTOYEFI$') { $ventoy = $true }
        }
        foreach ($letter in $letters) {
            $root = Join-Path ($letter + ':') 'ventoy'
            if (Test-Path -LiteralPath $root) { $ventoy = $true }
        }
        $size = ([math]::Round($disk.Size / 1GB, 1)).ToString('0.#', [System.Globalization.CultureInfo]::GetCultureInfo('fr-FR'))
        $status = [string]$disk.OperationalStatus
        if ($disk.IsOffline) { $status = 'Hors ligne' }
        elseif ($status -eq 'Online') { $status = 'En ligne' }
        $name = [string]$disk.FriendlyName
        if ([string]::IsNullOrWhiteSpace($name)) { $name = 'Disque USB' }
        $name = $name.Replace([char]9, ' ').Replace([char]10, ' ').Replace([char]13, ' ')
        $style = [string]$disk.PartitionStyle
        Emit ('USB' + $t + $disk.Number + $t + $size + $t + $name + $t + $status + $t + $ventoy.ToString().ToLower() + $t + ($labels -join ', ') + $t + ($letters -join ',') + $t + $style)
    }

    $vmms = Get-Service -Name vmms -ErrorAction SilentlyContinue
    if ($null -eq $vmms) {
        Emit ('META' + $t + 'HYPERV' + $t + 'missing')
    } elseif ($vmms.Status -ne 'Running') {
        Emit ('META' + $t + 'HYPERV' + $t + 'stopped')
    } else {
        Import-Module Hyper-V -ErrorAction Stop
        Emit ('META' + $t + 'HYPERV' + $t + 'ready')
        foreach ($vm in @(Get-VM)) {
            $owned = New-Object System.Collections.Generic.List[string]
            foreach ($drive in @(Get-VMHardDiskDrive -VMName $vm.Name)) {
                if ($null -ne $drive.DiskNumber) { [void]$owned.Add([string]$drive.DiskNumber) }
            }
            $vmName = ([string]$vm.Name).Replace([char]9, ' ').Replace([char]10, ' ').Replace([char]13, ' ')
            Emit ('VM' + $t + $vmName + $t + [string]$vm.State + $t + [string]$vm.Generation + $t + ($owned -join ','))
        }
    }
    Emit 'OK'
} catch {
    Fail $_.Exception.Message
}
