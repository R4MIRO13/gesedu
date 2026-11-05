#!/usr/bin/env bash
# Borra el volumen de PostgreSQL y vuelve a crear la base de datos vacía (roles incluidos).
# Úsalo cuando alguien edite una migración V1..V11 antes del primer despliegue compartido (docs/PLAN.md § 4.4).
set -euo pipefail
cd "$(dirname "$0")/.."

docker compose down -v
docker compose up -d --wait postgres mailpit
echo 'Base de datos reiniciada (postgres y mailpit en marcha). Arranca el backend para que Flyway aplique las migraciones.'
