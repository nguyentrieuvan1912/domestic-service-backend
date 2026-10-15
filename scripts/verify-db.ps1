# Development verification: use after running all services with dev-seed.
$ErrorActionPreference = "Stop"
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $dbAdmin = (docker compose exec -T postgres printenv POSTGRES_USER).Trim()
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL is not running" }
    $expected = @{ identity_db = 16; catalog_db = 8; booking_db = 17; finance_db = 17; ai_db = 7 }
    foreach ($database in @("identity_db","catalog_db","booking_db","finance_db","ai_db")) {
        $module = @{ identity_db='identity-service'; catalog_db='catalog-service'; booking_db='booking-service'; finance_db='finance-service'; ai_db='ai-assistant-service' }[$database]
        $migrationFiles = Get-ChildItem "$module/src/main/resources/db/migration/V*__*.sql"
        $latestVersion = ($migrationFiles.Name | ForEach-Object { [int]([regex]::Match($_, '^V(\d+)__').Groups[1].Value) } | Measure-Object -Maximum).Maximum
        $latestApplied = docker compose exec -T postgres psql -U $dbAdmin -d $database -t -A -c "SELECT count(*) FROM flyway_schema_history WHERE success AND version='$latestVersion';"
        if ($LASTEXITCODE -ne 0 -or [int]$latestApplied -ne 1) { throw "Latest migration V$latestVersion is not applied in $database" }
        $expectedTables = @($migrationFiles | ForEach-Object {
            [regex]::Matches((Get-Content -Raw $_.FullName), '(?im)^CREATE TABLE\s+(\w+)') | ForEach-Object { $_.Groups[1].Value }
        } | Where-Object { $_ -ne 'service_schema_metadata' } | Sort-Object -Unique)
        $actualTables = @(docker compose exec -T postgres psql -U $dbAdmin -d $database -t -A -c "SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename NOT IN ('service_schema_metadata','flyway_schema_history') ORDER BY tablename;")
        if ($LASTEXITCODE -ne 0) { throw "Cannot read table inventory in $database" }
        if (Compare-Object $expectedTables $actualTables) { throw "Table names differ from migrations in $database" }
        $tableCount = docker compose exec -T postgres psql -U $dbAdmin -d $database -t -A -c "SELECT count(*) FROM pg_tables WHERE schemaname='public' AND tablename NOT IN ('service_schema_metadata','flyway_schema_history');"
        if ($LASTEXITCODE -ne 0 -or [int]$tableCount -ne $expected[$database]) { throw "Unexpected table count in $database" }
        $migrations = docker compose exec -T postgres psql -U $dbAdmin -d $database -t -A -c "SELECT count(DISTINCT coalesce(version,description)) FROM flyway_schema_history WHERE success AND (version IN ('2','3') OR description='demo data');"
        if ($LASTEXITCODE -ne 0 -or [int]$migrations -ne 3) { throw "Domain/numeric-ID migration or demo seed missing in $database" }
        $uuidColumns = docker compose exec -T postgres psql -U $dbAdmin -d $database -t -A -c "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND data_type='uuid' AND column_name <> 'hold_token';"
        if ($LASTEXITCODE -ne 0 -or [int]$uuidColumns -ne 0) { throw "Entity IDs are not numeric in $database" }
        Get-Content -Raw scripts/db-checks/numeric-ids.sql |
            docker compose exec -T postgres psql -U $dbAdmin -d $database -v ON_ERROR_STOP=1 -q
        if ($LASTEXITCODE -ne 0) { throw "Numeric identity sequence checks failed in $database" }
        Write-Output "$database : $tableCount domain tables, migration and demo seed OK"
    }
    foreach ($service in @("identity","booking","finance")) {
        Get-Content -Raw "scripts/db-checks/$service.sql" |
            docker compose exec -T postgres psql -U $dbAdmin -d "${service}_db" -v ON_ERROR_STOP=1 -q
        if ($LASTEXITCODE -ne 0) { throw "Database constraints failed for $service" }
        Write-Output "$service constraints OK (test changes rolled back)"
    }
    Get-Content -Raw scripts/db-checks/community-posts.sql |
        docker compose exec -T postgres psql -U $dbAdmin -d identity_db -v ON_ERROR_STOP=1 -q
    if ($LASTEXITCODE -ne 0) { throw "CommunityPost persistence/ownership checks failed" }
    Write-Output "CommunityPost persistence and ownership OK (test changes rolled back)"
    $notificationTables = @(docker compose exec -T postgres psql -U $dbAdmin -d notification_db -At -c "SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename NOT IN ('service_schema_metadata','flyway_schema_history') ORDER BY tablename;")
    if ($LASTEXITCODE -ne 0) { throw 'notification_db is missing; prepare it and start notification-service' }
    if (Compare-Object @('notification_deliveries','notification_devices','notification_events','notifications') $notificationTables) { throw 'Unexpected notification table inventory' }
    $notificationMigrations = docker compose exec -T postgres psql -U $dbAdmin -d notification_db -At -c "SELECT count(*) FROM flyway_schema_history WHERE success AND version IN ('1','2');"
    if ($LASTEXITCODE -ne 0 -or [int]$notificationMigrations -ne 2) { throw 'Notification Flyway migrations are missing' }
    foreach ($check in @('numeric-ids','notification')) {
        Get-Content -Raw "scripts/db-checks/$check.sql" |
            docker compose exec -T postgres psql -U $dbAdmin -d notification_db -v ON_ERROR_STOP=1 -q
        if ($LASTEXITCODE -ne 0) { throw "Notification $check checks failed" }
    }
    Write-Output 'notification_db : 4 domain tables, Flyway, persistence, deduplication and device ownership OK (rolled back)'
} finally { Pop-Location }
