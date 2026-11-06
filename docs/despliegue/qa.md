# Despliegue de QA

**Estado: no existe todavía un entorno de QA compartido.** Este documento fija cómo debería armarse; mientras no exista, QA es la máquina de cada integrante (ver `local.md`) y el CI de GitHub.

## Qué hay hoy
- `.github/workflows/ci.yml` corre en cada PR: backend (`./mvnw -B verify`, con Testcontainers) y frontend (lint, typecheck, pruebas y build en Node 22 y 24). `main` exige ambos en verde.
- Todavía **no** se ha puesto la etiqueta `esquema-congelado`: hasta entonces las migraciones V1–V11 se pueden editar (CLAUDE.md, S-26). **Al primer despliegue compartido (QA o AWS) se pone la etiqueta y las migraciones pasan a «solo hacia adelante»** (V12 en adelante).

## Cómo armar QA (propuesta)
1. Una VM o un contenedor con `docker compose` (postgres:18 + backend + frontend servido por un proxy), o el mismo diseño de `aws.md` en una cuenta de pruebas.
2. Perfil `local,demo` si se quiere la semilla de la demo; sin `demo`, una base con solo el catálogo.
3. Variables obligatorias fuera de local: `GESEDU_JWT_SECRET` (≥ 32 caracteres y distinto del de `.env.example`), `GESEDU_ARCHIVOS_SECRETO`, contraseñas de los dos roles de base de datos, `GESEDU_WEB_ORIGIN`, `GESEDU_COOKIE_SECURE=true` (HTTPS) y, si hace falta, `GESEDU_LIMITE_AUTH_POR_MINUTO` (30 por omisión; detrás de un proxy `forward-headers-strategy=framework` toma la IP real).
4. Servir la SPA y la API bajo el mismo origen (`/api` hacia el backend): así no hace falta CORS y la cookie de refresco funciona con `SameSite=Strict`. Poner en el proxy las cabeceras de la SPA (CSP, HSTS); la API ya envía las suyas (S-85).
5. Prueba de humo tras cada despliegue: `/actuator/health` en `UP`, inicio de sesión de las cuatro cuentas de la demo y recorrido de `docs/demo/guion.md`.

## Pendientes antes del primer despliegue compartido
Verificación de arranque que rechace la clave de desarrollo del JWT fuera de `local` (S-85) y la decisión sobre el rendimiento de UC-01 (`carga/README.md`).
