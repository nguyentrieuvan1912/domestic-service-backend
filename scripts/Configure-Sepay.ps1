param([switch]$NoRestart)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $projectRoot '.env'
if (!(Test-Path -LiteralPath $envFile)) { throw 'Missing backend .env' }
$content = [IO.File]::ReadAllText($envFile)
function Read-Setting([string]$Name) {
    $matched = [regex]::Match($script:content, '(?m)^' + [regex]::Escape($Name) + '=(.*)\r?$')
    if ($matched.Success) { return $matched.Groups[1].Value.Trim().Trim('"').Trim("'") }
    return ''
}
function Set-Setting([string]$Name, [string]$Value) {
    if ($Value.Contains("`n") -or $Value.Contains("`r")) { throw 'Configuration must be a single line' }
    $pattern = '(?m)^' + [regex]::Escape($Name) + '=.*\r?$'
    if ([regex]::IsMatch($script:content, $pattern)) {
        $script:content = [regex]::Replace($script:content, $pattern, [System.Text.RegularExpressions.MatchEvaluator]{ param($match) "$Name=$Value" })
    } else { $script:content += "`r`n$Name=$Value" }
}
$token = Read-Setting 'SEPAY_API_TOKEN'
if (!$token) { throw 'Fill SEPAY_API_TOKEN in backend .env first. Do not paste it into chat.' }
try {
    $result = Invoke-RestMethod 'https://userapi.sepay.vn/v2/bank-accounts?per_page=100' -Headers @{ Authorization = "Bearer $token" } -TimeoutSec 15
} catch { throw 'Cannot read SePay Live accounts. Check the Live API Token and connection on SePay.' }
$account = Read-Setting 'SEPAY_SOURCE_ACCOUNT'
$banks = @($result.data | Where-Object { ($_.bank_short_name -in @('TPBank','TPB') -or $_.bank_brand_name -in @('TPBank','TPB')) -and (!$account -or $_.account_number -eq $account) })
if ($banks.Count -ne 1) { throw 'Need exactly one linked TPBank account. If multiple, set SEPAY_SOURCE_ACCOUNT in .env, then run again.' }
$bank = $banks[0]
if ($bank.active -ne 1) { throw 'The TPBank connection is inactive. Reconnect it on SePay first.' }
Set-Setting 'SEPAY_BANK_ACCOUNT_ID' ([string]$bank.id)
Set-Setting 'SEPAY_SOURCE_ACCOUNT' ([string]$bank.account_number)
Set-Setting 'SEPAY_SOURCE_NAME' ([string]$bank.account_holder_name)
if (!(Read-Setting 'SEPAY_WEBHOOK_SECRET')) {
    $secretBytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($secretBytes) } finally { $rng.Dispose() }
    Set-Setting 'SEPAY_WEBHOOK_SECRET' ([BitConverter]::ToString($secretBytes).Replace('-', '').ToLowerInvariant())
}
$tkp = Read-Setting 'SEPAY_TKP'
if ($tkp -and $tkp -notmatch '^TKP[A-Z0-9]{3}$') { throw 'SEPAY_TKP is optional. Leave it empty, or use the actual TKP code assigned on SePay.' }
Set-Setting 'SEPAY_ENABLED' 'true'
[IO.File]::WriteAllText($envFile, $content, [Text.UTF8Encoding]::new($false))
Write-Host 'Verified TPBank source and saved account ID/name from SePay. Secret values are not printed.'
if (!$NoRestart) {
    Push-Location $projectRoot
    try {
        docker compose up -d --no-deps finance-service
        if ($LASTEXITCODE -ne 0) { throw 'Finance restart failed' }
        $healthy = $false
        for ($attempt = 0; $attempt -lt 45; $attempt++) {
            try { $health = Invoke-RestMethod 'http://127.0.0.1:8084/actuator/health' -TimeoutSec 2; if ($health.status -eq 'UP') { $healthy = $true; break } } catch {}
            Start-Sleep -Seconds 1
        }
        if (!$healthy) { throw 'Finance is not healthy yet. Inspect finance-service logs.' }
        Write-Host 'Finance health: UP. In Admin, open Finance and select Check connection/source.'
    } finally { Pop-Location }
}
