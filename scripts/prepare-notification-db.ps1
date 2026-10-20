$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
$previousUser = $env:NOTIFICATION_DB_USER
$previousPassword = $env:NOTIFICATION_DB_PASSWORD
try {
    $settings = @{ NOTIFICATION_DB_USER='notification_app'; NOTIFICATION_DB_PASSWORD='local_notification_change_me' }
    if (Test-Path .env) {
        foreach ($line in Get-Content .env) {
            if ($line -match '^\s*(NOTIFICATION_DB_USER|NOTIFICATION_DB_PASSWORD)=(.*)$') {
                $settings[$Matches[1]] = $Matches[2].Trim().Trim('"').Trim("'")
            }
        }
    }
    $env:NOTIFICATION_DB_USER = $settings.NOTIFICATION_DB_USER
    $env:NOTIFICATION_DB_PASSWORD = $settings.NOTIFICATION_DB_PASSWORD
    docker compose cp docker/postgres/create-notification-database.sh postgres:/tmp/create-notification-database.sh
    if ($LASTEXITCODE -ne 0) { throw 'Cannot copy notification provisioning script' }
    docker compose exec -T -e NOTIFICATION_DB_USER -e NOTIFICATION_DB_PASSWORD postgres bash /tmp/create-notification-database.sh
    if ($LASTEXITCODE -ne 0) { throw 'Notification database provisioning failed; existing owner/credentials must match configuration' }
    Write-Output 'notification_db provisioned; existing service databases were preserved'
} finally {
    $env:NOTIFICATION_DB_USER = $previousUser
    $env:NOTIFICATION_DB_PASSWORD = $previousPassword
    Pop-Location
}
