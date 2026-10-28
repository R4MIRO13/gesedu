# GESEDU

Sistema web de gestión integral de un colegio de secundaria (PUCP, Ingeniería de Software 2026-2).

- Plan y convenciones: `docs/PLAN.md` y `CLAUDE.md`.
- Estado de la implementación: `docs/AVANCE.md`.
- Demo: `docs/demo/guion.md`.

## Arranque rápido (PowerShell)
```powershell
Copy-Item .env.example .env
docker compose up -d postgres mailpit
cd backend; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local,demo"   # terminal 1
cd frontend; npm ci; npm run dev                                                  # terminal 2
```
SPA http://localhost:5173 · Swagger http://localhost:8080/swagger-ui.html · Mailpit http://localhost:8025

## Pruebas
```powershell
cd backend; .\mvnw.cmd -B verify
cd frontend; npm run lint; npm run typecheck; npm test -- --run; npm run build
```
