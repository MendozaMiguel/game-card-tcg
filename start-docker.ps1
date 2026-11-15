$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

if (-not (Test-Path (Join-Path $root ".env")) -and (Test-Path (Join-Path $root ".env.example"))) {
    Copy-Item (Join-Path $root ".env.example") (Join-Path $root ".env")
    Write-Host "[OK] Fichier .env cree depuis .env.example" -ForegroundColor Green
}

Write-Host "Build et demarrage de la stack Docker (arcane-db, arcane-server, arcane-web)..." -ForegroundColor Cyan
docker compose -f (Join-Path $root "docker-compose.yml") up -d --build
docker compose -f (Join-Path $root "docker-compose.yml") ps
