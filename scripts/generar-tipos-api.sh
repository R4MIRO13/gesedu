#!/usr/bin/env bash
# Genera los tipos TypeScript de la API (frontend/src/compartido/api/tipos.gen.ts) con openapi-typescript
# a partir de http://localhost:8080/v3/api-docs. El backend tiene que estar corriendo (perfil local).
set -euo pipefail
cd "$(dirname "$0")/.."

if ! curl -fsS --max-time 5 http://localhost:8080/v3/api-docs > /dev/null; then
  echo "El backend no responde en http://localhost:8080. Arráncalo primero: cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local" >&2
  exit 1
fi

(cd frontend && npm run gen:tipos)
echo "Tipos generados en frontend/src/compartido/api/tipos.gen.ts"
