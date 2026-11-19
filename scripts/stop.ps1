try {
    Require-HyperV
    $vm = Get-KeyVVm $env:KEYV_VM
    if ($vm.State -eq 'Off') {
        Emit ('LOG' + $t + 'ALREADY_OFF')
        Emit 'OK'
        exit 0
    }
    if ($vm.State -eq 'Saved') {
        Remove-VMSavedState -VMName $vm.Name
        Emit ('LOG' + $t + 'SAVED_CLEARED')
        Emit 'OK'
        exit 0
    }
    Stop-VM -Name $vm.Name -Force
    Emit ('LOG' + $t + 'VM_STOPPED')
    Emit 'OK'
} catch {
    $m = $_.Exception.Message
    if ($m -match 'denied|refus') { Fail 'ACCESS_DENIED' }
    Fail $m
}
