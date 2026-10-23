# Borra el volumen de PostgreSQL y vuelve a crear la base de datos vacía (roles incluidos).
# Úsalo cuando alguien edite una migración V1..V11 antes del primer despliegue compartido (docs/PLAN.md § 4.4).
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

docker compose down -v
docker compose up -d --wait postgres
Write-Host 'Base de datos reiniciada. Arranca el backend para que Flyway aplique las migraciones.'
