# Starts PostgreSQL + backend + frontend with Docker Compose and waits until the API is healthy.
# Usage (from the repo root):  .\scripts\start.ps1
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    Write-Host "Created .env from .env.example. Edit POSTGRES_PASSWORD if you like." -ForegroundColor Yellow
}

docker compose up --build -d
if ($LASTEXITCODE -ne 0) { throw "docker compose failed" }

$envVars = @{}
Get-Content ".env" | Where-Object { $_ -match "^\s*[A-Z_]+=" } | ForEach-Object {
    $k, $v = $_ -split "=", 2
    $envVars[$k.Trim()] = $v.Trim()
}
$api = "http://localhost:" + $(if ($envVars.BACKEND_PORT) { $envVars.BACKEND_PORT } else { "8080" })
$ui = "http://localhost:" + $(if ($envVars.FRONTEND_PORT) { $envVars.FRONTEND_PORT } else { "3000" })

Write-Host "Waiting for the API at $api ..."
for ($i = 0; $i -lt 60; $i++) {
    try {
        $h = Invoke-RestMethod "$api/actuator/health" -TimeoutSec 3
        if ($h.status -eq "UP") { break }
    } catch { Start-Sleep -Seconds 3 }
}
Write-Host "TradeGuard is running:" -ForegroundColor Green
Write-Host "  UI      $ui"
Write-Host "  API     $api/api/analytics/overview"
Write-Host "  Health  $api/actuator/health"
