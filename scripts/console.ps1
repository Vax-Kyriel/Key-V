try {
    Require-HyperV
    $vm = Get-KeyVVm $env:KEYV_VM
    Open-KeyVConsole $vm.Name
    Emit 'OK'
} catch {
    Fail $_.Exception.Message
}
