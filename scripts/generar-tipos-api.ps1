# Genera los tipos TypeScript de la API (frontend/src/compartido/api/tipos.gen.ts) con openapi-typescript
# a partir de http://localhost:8080/v3/api-docs. El backend tiene que estar corriendo (perfil local).
$ErrorActionPreference = 'Stop'
$raiz = Join-Path $PSScriptRoot '..'

try {
    Invoke-WebRequest -Uri 'http://localhost:8080/v3/api-docs' -UseBasicParsing -TimeoutSec 5 | Out-Null
} catch {
    Write-Error 'El backend no responde en http://localhost:8080. Arráncalo primero: cd backend; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"'
    exit 1
}

Push-Location (Join-Path $raiz 'frontend')
try {
    npm run gen:tipos
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}
Write-Host 'Tipos generados en frontend/src/compartido/api/tipos.gen.ts'
