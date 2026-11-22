$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$envFile = Join-Path $root ".env"
$composeFile = Join-Path $root "docker-compose.yml"

function Ensure-DockerPath {
    if (Get-Command docker -ErrorAction SilentlyContinue) {
        return
    }
    $fallbackDir = Join-Path $env:ProgramFiles "Docker\Docker\resources\bin"
    if (Test-Path (Join-Path $fallbackDir "docker.exe")) {
        $env:PATH = "$fallbackDir;$env:PATH"
        return
    }
    throw "Docker CLI introuvable. Installez ou reparez Docker Desktop puis relancez ce script."
}

function Test-DockerDaemonReady {
    $previousPref = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $null = docker info --format "{{.ServerVersion}}" 2>$null
        return ($LASTEXITCODE -eq 0)
    } finally {
        $ErrorActionPreference = $previousPref
    }
}

function Ensure-DockerDaemonRunning {
    if (Test-DockerDaemonReady) {
        return
    }
    Write-Host "[Docker] Demarrage de Docker Desktop..." -ForegroundColor Yellow
    docker desktop start | Out-Null

    $deadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $deadline) {
        if (Test-DockerDaemonReady) {
            Write-Host "[OK] Moteur Docker pret." -ForegroundColor Green
            return
        }
        Start-Sleep -Seconds 2
    }
    throw "Docker Desktop n'a pas repondu dans les 90s. Verifiez Docker Desktop puis reessayez."
}

function Read-DotEnvValue {
    param([string]$Path, [string]$Key, [string]$DefaultValue = "")
    if (-not (Test-Path $Path)) {
        return $DefaultValue
    }
    foreach ($line in Get-Content $Path) {
        if ($line -match "^\s*$([regex]::Escape($Key))=(.*)$") {
            return $Matches[1].Trim()
        }
    }
    return $DefaultValue
}

Ensure-DockerPath

if (-not (Test-Path $envFile) -and (Test-Path (Join-Path $root ".env.example"))) {
    Copy-Item (Join-Path $root ".env.example") $envFile
    Write-Host "[OK] Fichier .env cree depuis .env.example" -ForegroundColor Green
}

Ensure-DockerDaemonRunning

Write-Host "Build et demarrage de la stack Docker (arcane-db, arcane-server, arcane-web)..." -ForegroundColor Cyan
docker compose -f $composeFile up -d --build
docker compose -f $composeFile ps

$webPort = Read-DotEnvValue -Path $envFile -Key "WEB_PORT" -DefaultValue "80"
$allowedOrigins = Read-DotEnvValue -Path $envFile -Key "ARCANE_ALLOWED_ORIGINS" -DefaultValue ""
$portSuffix = if ($webPort -eq "80") { "" } else { ":$webPort" }

Write-Host ""
Write-Host "Jeu accessible localement : http://localhost$portSuffix/" -ForegroundColor Green

$lanIp = (Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object {
        $_.InterfaceAlias -notmatch "Loopback|vEthernet" -and
        $_.IPAddress -notlike "169.254.*" -and
        $_.PrefixOrigin -ne "WellKnown"
    } |
    Select-Object -First 1 -ExpandProperty IPAddress)

if ($lanIp) {
    $lanOrigin = "http://$lanIp$portSuffix"
    Write-Host "Acces reseau local / mobile : $lanOrigin/" -ForegroundColor Cyan
    if ($allowedOrigins -and ($allowedOrigins -notlike "*$lanOrigin*")) {
        Write-Host "[ATTENTION] $lanOrigin est absent de ARCANE_ALLOWED_ORIGINS dans .env (requis pour WebSocket mobile)." -ForegroundColor Yellow
    }
}

$publicWifi = Get-NetConnectionProfile -ErrorAction SilentlyContinue | Where-Object { $_.NetworkCategory -eq "Public" }
if ($publicWifi) {
    Write-Host "[INFO] Profil reseau Windows en mode 'Public' : passez le Wi-Fi en 'Prive' ou autorisez TCP $webPort pour tester depuis un telephone." -ForegroundColor Yellow
}
