param([string]$BaseUrl = "http://localhost:8080", [int]$TimeoutSeconds = 180)
$ErrorActionPreference = "Stop"
foreach ($service in @("identity", "catalog", "booking", "finance", "ai-assistant")) {
    $uri = "$BaseUrl/api/v1/$service/system/info"
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $response = Invoke-RestMethod -Uri $uri -TimeoutSec 5
            if ($response.service -ne "$service-service") { throw "Unexpected response from $uri" }
            Write-Output "$service OK ($($response.stage))"
            break
        } catch {
            if ((Get-Date) -ge $deadline) { throw "Smoke check failed for $uri : $_" }
            Start-Sleep -Seconds 2
        }
    } while ($true)
}
