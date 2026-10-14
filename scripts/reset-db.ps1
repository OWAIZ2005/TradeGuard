# Deletes ONLY this project's PostgreSQL volume (tradeguard-pgdata), then restarts the stack.
# Flyway recreates the schema and, when TRADEGUARD_DEMO_LOAD=true, the synthetic sample file is imported again.
# Usage:  .\scripts\reset-db.ps1        (asks for confirmation)   |   .\scripts\reset-db.ps1 -Force
param([switch]$Force)
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

if (-not $Force) {
    $answer = Read-Host "This permanently deletes all TradeGuard data in the local database. Type 'reset' to continue"
    if ($answer -ne "reset") { Write-Host "Cancelled."; exit 0 }
}
docker compose down -v
docker compose up -d
Write-Host "Database reset. Migrations run automatically on backend start." -ForegroundColor Green
