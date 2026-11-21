$ErrorActionPreference = 'Stop'

$secretsPath = if ($env:LOOKI_ENV_FILE) { $env:LOOKI_ENV_FILE } else { Join-Path $env:LOCALAPPDATA 'Looki\postgres.env' }
if (-not (Test-Path $secretsPath)) {
    throw "Local settings file not found: $secretsPath. Copy local.env.example there and fill it in."
}

foreach ($line in Get-Content $secretsPath) {
    if ($line -match '^\s*([^#=]+)=(.*)$') {
        [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
    }
}

foreach ($name in @('POSTGRES_DB', 'POSTGRES_USER', 'POSTGRES_PASSWORD', 'JWT_SECRET')) {
    if (-not [Environment]::GetEnvironmentVariable($name, 'Process')) {
        throw "Missing $name in $secretsPath"
    }
}

$containerStatus = & docker inspect --format '{{.State.Running}}' looki-postgres 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host 'Creating the looki-postgres container...'
    & docker run -d --name looki-postgres --restart unless-stopped `
        -e "POSTGRES_DB=$env:POSTGRES_DB" -e "POSTGRES_USER=$env:POSTGRES_USER" `
        -e "POSTGRES_PASSWORD=$env:POSTGRES_PASSWORD" `
        -p 127.0.0.1:5432:5432 -v looki-postgres-data:/var/lib/postgresql/data `
        postgres:16-alpine | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not create the local PostgreSQL container. Check that Docker Desktop is running.'
    }
} elseif ($containerStatus.Trim() -ne 'true') {
    & docker start looki-postgres | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not start the local PostgreSQL container.'
    }
}

$databaseReady = $false
for ($attempt = 0; $attempt -lt 30; $attempt++) {
    & docker exec looki-postgres pg_isready -U $env:POSTGRES_USER -d $env:POSTGRES_DB *> $null
    if ($LASTEXITCODE -eq 0) {
        $databaseReady = $true
        break
    }
    Start-Sleep -Seconds 2
}
if (-not $databaseReady) {
    throw 'PostgreSQL did not become ready. Check that Docker Desktop is running.'
}

$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://127.0.0.1:5432/$env:POSTGRES_DB"
$env:SPRING_DATASOURCE_USERNAME = $env:POSTGRES_USER
$env:SPRING_DATASOURCE_PASSWORD = $env:POSTGRES_PASSWORD
$env:SERVER_ADDRESS = '127.0.0.1'
$env:PORT = '8081'

Set-Location $PSScriptRoot
if (Get-Command mvn -ErrorAction SilentlyContinue) {
    & mvn spring-boot:run
} else {
    & .\mvnw.cmd spring-boot:run
}
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
