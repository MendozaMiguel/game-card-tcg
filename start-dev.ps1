param(
    [switch]$SkipDb
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

if (-not (Test-Path (Join-Path $root ".env")) -and (Test-Path (Join-Path $root ".env.example"))) {
    Copy-Item (Join-Path $root ".env.example") (Join-Path $root ".env")
    Write-Host "[OK] Fichier .env cree depuis .env.example" -ForegroundColor Green
}

if (-not $SkipDb) {
    Write-Host "[1/3] Demarrage de PostgreSQL 17 (arcane-db)..." -ForegroundColor Cyan
    docker compose -f (Join-Path $root "docker-compose.yml") up -d arcane-db
}

Write-Host "[2/3] Lancement du Back-End Spring Boot (fenetre dediee)..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$root\backend'; mvn spring-boot:run"

Write-Host "[3/3] Lancement du Front-End Angular 22 (fenetre dediee)..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$root\frontend'; npm.cmd start"

Write-Host "Application locale prete sur http://localhost:4200 (proxy -> http://localhost:8080)" -ForegroundColor Green
