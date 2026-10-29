$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$work = Join-Path $projectRoot '.local-notes/person1/sepay-tunnel'
New-Item -ItemType Directory -Path $work -Force | Out-Null
$proxyScript = Join-Path $PSScriptRoot 'sepay-webhook-proxy.mjs'
$proxyLog = Join-Path $work 'proxy.log'
$tunnelLog = Join-Path $work 'tunnel.log'
$stateFile = Join-Path $work 'state.json'
if (Test-Path -LiteralPath $stateFile) {
    $state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
    $proxy = Get-Process -Id $state.proxyId -ErrorAction SilentlyContinue
    $tunnel = Get-Process -Id $state.tunnelId -ErrorAction SilentlyContinue
    if ($proxy -and $tunnel -and $proxy.StartTime.ToUniversalTime().Ticks -eq $state.proxyStarted -and $tunnel.StartTime.ToUniversalTime().Ticks -eq $state.tunnelStarted) {
        Write-Host ('Webhook URL: ' + $state.url + '/api/v1/finance/webhooks/sepay'); return
    }
    & (Join-Path $PSScriptRoot 'Stop-SepayWebhook.ps1')
}
if (Get-NetTCPConnection -LocalPort 8092 -State Listen -ErrorAction SilentlyContinue) { throw 'Port 8092 is already used. No process was stopped.' }
$cloudflared = Join-Path $work 'cloudflared.exe'
if (!(Test-Path -LiteralPath $cloudflared)) {
    Write-Host 'Downloading cloudflared from the official Cloudflare GitHub release...'
    Invoke-WebRequest 'https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe' -OutFile $cloudflared
}
$nodePath = (Get-Command node -ErrorAction Stop).Source
$proxy = Start-Process -FilePath $nodePath -ArgumentList ('"' + $proxyScript + '"') -WindowStyle Hidden -PassThru -RedirectStandardOutput $proxyLog -RedirectStandardError (Join-Path $work 'proxy-error.log')
try {
    for ($attempt=0; $attempt -lt 10; $attempt++) {
        try { $health = Invoke-RestMethod 'http://127.0.0.1:8092/health' -TimeoutSec 1; if ($health.service -eq 'cleanmaster-sepay-webhook') { break } } catch {}
        Start-Sleep -Seconds 1
    }
    if (!$health -or $health.service -ne 'cleanmaster-sepay-webhook') { throw 'Webhook proxy did not start' }
    $tunnel = Start-Process -FilePath $cloudflared -ArgumentList 'tunnel','--no-autoupdate','--url','http://127.0.0.1:8092','--protocol','http2' -WindowStyle Hidden -PassThru -RedirectStandardError $tunnelLog -RedirectStandardOutput (Join-Path $work 'tunnel-output.log')
    $publicUrl = ''
    for ($attempt=0; $attempt -lt 45; $attempt++) {
        if (Test-Path -LiteralPath $tunnelLog) {
            $logText = Get-Content -LiteralPath $tunnelLog -Raw -ErrorAction SilentlyContinue
            if ($logText) {
                $match = [regex]::Match($logText, 'https://[a-z0-9-]+\.trycloudflare\.com')
                if ($match.Success) { $publicUrl = $match.Value; break }
            }
        }
        if ($tunnel.HasExited) { throw 'Cloudflare tunnel exited. Inspect the tunnel log.' }
        Start-Sleep -Seconds 1
    }
    if (!$publicUrl) { throw 'No public URL returned. Inspect the tunnel log.' }
    $state = @{proxyId=$proxy.Id;tunnelId=$tunnel.Id;proxyStarted=$proxy.StartTime.ToUniversalTime().Ticks;tunnelStarted=$tunnel.StartTime.ToUniversalTime().Ticks;url=$publicUrl}
    $state | ConvertTo-Json | Set-Content -LiteralPath $stateFile -Encoding utf8
    $envFile = Join-Path $projectRoot '.env'
    $text = [IO.File]::ReadAllText($envFile)
    if ($text -match '(?m)^SEPAY_PUBLIC_URL=') { $text = [regex]::Replace($text, '(?m)^SEPAY_PUBLIC_URL=.*\r?$', "SEPAY_PUBLIC_URL=$publicUrl") }
    else { $text += "`r`nSEPAY_PUBLIC_URL=$publicUrl`r`n" }
    [IO.File]::WriteAllText($envFile,$text,[Text.UTF8Encoding]::new($false))
    Write-Host ('Webhook URL: ' + $publicUrl + '/api/v1/finance/webhooks/sepay')
    Write-Host 'Only this webhook path is exposed. Admin APIs are blocked by the proxy. The temporary URL lasts while these processes run.'
} catch {
    if ($proxy -and !$proxy.HasExited) { Stop-Process -Id $proxy.Id }
    if ($tunnel -and !$tunnel.HasExited) { Stop-Process -Id $tunnel.Id }
    throw
}
