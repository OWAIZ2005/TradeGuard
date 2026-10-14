# Stops the stack but keeps the database volume (your data survives).
Set-Location (Join-Path $PSScriptRoot "..")
docker compose down
