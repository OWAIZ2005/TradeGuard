# Uploads the synthetic sample CSV through the normal import API and prints the batch summary.
# Usage:  .\scripts\load-sample.ps1 [-Api http://localhost:8080] [-File sample-data\sample_trades.csv]
# Note: trade ids already imported are rejected (never overwritten), so a second run reports duplicates.
param(
    [string]$Api = "http://localhost:8080",
    [string]$File = (Join-Path $PSScriptRoot "..\sample-data\sample_trades.csv")
)
$ErrorActionPreference = "Stop"
$path = (Resolve-Path $File).Path
$json = curl.exe -s -S -F "file=@$path;type=text/csv" "$Api/api/imports"
$result = $json | ConvertFrom-Json
if ($result.batch) {
    $b = $result.batch
    Write-Host ("Batch #{0}: {1} rows, {2} valid, {3} invalid, {4} duplicates" -f $b.id, $b.totalRows, $b.validRows, $b.invalidRows, $b.duplicateRows) -ForegroundColor Green
    Write-Host $result.summary.headline
} else {
    Write-Host "Import rejected: $($result.code) - $($result.message)" -ForegroundColor Red
}
