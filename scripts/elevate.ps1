try {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $env:KEYV_JAVA
    $psi.Arguments = $env:KEYV_ARGS
    $psi.WorkingDirectory = $env:KEYV_WORKDIR
    $psi.UseShellExecute = $true
    $psi.Verb = 'runas'
    [void][System.Diagnostics.Process]::Start($psi)
    Emit 'OK'
} catch {
    Fail 'ELEVATION_CANCELLED'
}
