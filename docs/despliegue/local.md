# Despliegue local

Es el entorno de desarrollo y de la demo. Requisitos: Docker Desktop (WSL 2), JDK 25, Node 22 LTS.

1. `Copy-Item .env.example .env` (no hace falta cambiar nada para correr en local).
2. Servicios: `docker compose up -d --wait postgres mailpit` (PostgreSQL 18 con los roles `gesedu_migrador` y `gesedu_app` creados por `infra/postgres/init`; Mailpit en http://localhost:8025).
3. Backend: `cd backend; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"`. Con datos de la demo: `…profiles=local,demo` (reloj propio de la demo: lunes 15/03/2027 07:40; cuentas con la contraseña `Gesedu2027!`). Flyway migra al arrancar.
4. Frontend: `cd frontend; npm ci; npm run dev` → http://localhost:5173 (Vite hace proxy de `/api` al :8080). Swagger UI: http://localhost:8080/swagger-ui.html.
5. Reiniciar la base: `.\scripts\reiniciar-bd.ps1` (o `./scripts/reiniciar-bd.sh`). Hace falta tras editar una migración V1–V11 antes del congelado del esquema y cuando se agrega una migración que cambia la semilla de la demo.

Archivos subidos: se guardan en `backend/datos-archivos/` (`GESEDU_ARCHIVOS_TIPO=disco`). El correo sale por SMTP a Mailpit (`GESEDU_NOTIFICACIONES_TIPO=smtp`). Las claves de `.env.example` son de desarrollo y solo valen en local.

Pruebas: `cd backend; .\mvnw.cmd -B clean verify` (necesita Docker para Testcontainers) y `cd frontend; npm run lint; npm run typecheck; npm test -- --run; npm run build`. En un equipo con poca memoria usa `$env:MAVEN_OPTS='-Xmx1g'` y `-DforkCount=1`, y no dejes corriendo el backend ni el servidor de desarrollo durante un `verify`.
