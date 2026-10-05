# Development verification: use after running all services with dev-seed.
$ErrorActionPreference = "Stop"
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $dbAdmin = (docker compose exec -T postgres printenv POSTGRES_USER).Trim()
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL is not running" }
    $expected = @{ identity_db = 7; catalog_db = 8; booking_db = 15; finance_db = 16; ai_db = 7 }
    foreach ($database in @("identity_db","catalog_db","booking_db","finance_db","ai_db")) {
        $tableCount = docker compose exec -T postgres psql -U $dbAdmin -d $database -t -A -c "SELECT count(*) FROM pg_tables WHERE schemaname='public' AND tablename NOT IN ('service_schema_metadata','flyway_schema_history');"
        if ($LASTEXITCODE -ne 0 -or [int]$tableCount -ne $expected[$database]) { throw "Unexpected table count in $database" }
        $migrations = docker compose exec -T postgres psql -U $dbAdmin -d $database -t -A -c "SELECT count(DISTINCT coalesce(version,description)) FROM flyway_schema_history WHERE success AND (version='2' OR description='demo data');"
        if ($LASTEXITCODE -ne 0 -or [int]$migrations -ne 2) { throw "Domain migration/demo seed missing in $database" }
        Write-Output "$database : $tableCount domain tables, migration and demo seed OK"
    }
    foreach ($service in @("identity","booking","finance")) {
        Get-Content -Raw "scripts/db-checks/$service.sql" |
            docker compose exec -T postgres psql -U $dbAdmin -d "${service}_db" -v ON_ERROR_STOP=1 -q
        if ($LASTEXITCODE -ne 0) { throw "Database constraints failed for $service" }
        Write-Output "$service constraints OK (test changes rolled back)"
    }
} finally { Pop-Location }
