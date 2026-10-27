$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$work=Join-Path $projectRoot '.local-notes/person1/sepay-tunnel'
$stateFile=Join-Path $work 'state.json'
if (!(Test-Path -LiteralPath $stateFile)) { Write-Host 'No recorded SePay tunnel.'; return }
$state=Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
foreach ($kind in @('proxy','tunnel')) {
    $processId=$state.($kind+'Id')
    $expectedTicks=$state.($kind+'Started')
    $process=Get-Process -Id $processId -ErrorAction SilentlyContinue
    if (!$process) { continue }
    $command=Get-CimInstance Win32_Process -Filter "ProcessId=$processId"
    $expectedPath=if ($kind -eq 'proxy') { Join-Path $PSScriptRoot 'sepay-webhook-proxy.mjs' } else { Join-Path $work 'cloudflared.exe' }
    if ($process.StartTime.ToUniversalTime().Ticks -eq $expectedTicks -and $command.CommandLine.Contains($expectedPath)) {
        Stop-Process -Id $processId
    } else { throw 'Process identity changed. Refusing to stop an unrelated process.' }
}
Remove-Item -LiteralPath $stateFile
Write-Host 'Stopped the recorded SePay tunnel/proxy only. Backend, Metro and web remain running.'
