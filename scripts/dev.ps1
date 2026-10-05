param(
    [ValidateSet("infra", "person1", "person2", "all", "check", "stop")]
    [string]$Target = "infra"
)
$ErrorActionPreference = "Stop"
$repoRoot = Split-Path $PSScriptRoot -Parent
Push-Location $repoRoot
try {
    if (-not (Test-Path .env)) { Copy-Item .env.example .env }
    switch ($Target) {
        "infra" { docker compose up -d --wait postgres }
        "person1" { docker compose up -d --build postgres api-gateway identity-service catalog-service finance-service }
        "person2" { docker compose up -d --build postgres api-gateway identity-service catalog-service booking-service ai-assistant-service }
        "all" { docker compose --profile backend up -d --build }
        "check" { & .\mvnw.cmd -B --no-transfer-progress verify }
        "stop" { docker compose --profile backend down }
    }
    if ($LASTEXITCODE -ne 0) { throw "Command failed with exit code $LASTEXITCODE" }
} finally { Pop-Location }
