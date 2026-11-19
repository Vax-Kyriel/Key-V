try {
    $vmms = Get-Service -Name vmms -ErrorAction SilentlyContinue
    if ($null -eq $vmms) { Fail 'NO_HYPERV' }
    Start-Service vmms
    Emit ('LOG' + $t + 'SERVICE_STARTED')
    Emit 'OK'
} catch {
    $m = $_.Exception.Message
    if ($m -match 'denied|refus') { Fail 'ACCESS_DENIED' }
    Fail $m
}
