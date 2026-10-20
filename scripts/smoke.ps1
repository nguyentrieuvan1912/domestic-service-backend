param([string]$BaseUrl = "http://localhost:8080", [int]$TimeoutSeconds = 180,
      [string]$NotificationUrl = "http://localhost:8087")
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
# Schema-only service is not routed through Gateway until API cutover.
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
do {
    try {
        $response = Invoke-RestMethod -Uri "$NotificationUrl/api/v1/notification/system/info" -TimeoutSec 5
        $health = Invoke-RestMethod -Uri "$NotificationUrl/actuator/health" -TimeoutSec 5
        if ($response.service -ne 'notification-service' -or $health.status -ne 'UP') { throw 'Unexpected notification status' }
        Write-Output "notification OK ($($response.stage), database health UP)"
        break
    } catch {
        if ((Get-Date) -ge $deadline) { throw "Notification smoke check failed: $_" }
        Start-Sleep -Seconds 2
    }
} while ($true)
