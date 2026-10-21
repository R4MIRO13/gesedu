# CLAUDE.md — GESEDU

Sistema web de gestión integral de un colegio de secundaria (PUCP, Ingeniería de Software 2026-2, 10 integrantes).
**Plan maestro:** `docs/PLAN.md` (fases, modelo de datos, contrato de la API, pantallas, contradicciones C-xx, supuestos S-xx y preguntas abiertas Q-xx). Léelo antes de implementar cualquier caso de uso.

## Fuentes y prioridad
`docs/arquitectura.pdf` > `docs/ers.pdf` > `docs/catalogo_requisitos.pdf` > `docs/wireframes.pdf` > resto (`modelo_conceptual.pdf`, `vision.pdf`, `procesos.pdf`, `matriz_trazabilidad.xlsx`).
- Si encuentras una contradicción, no la resuelvas en silencio: regístrala en `docs/PLAN.md` §11.1.
- Si algo no está definido, propón una opción, márcala como supuesto en §11.2 y avisa.
- Cita los IDs (UC-xx, RF-xx, RNF-xx, DA-xx) en commits, PR, comentarios de prueba y documentación.
- **Fuera de alcance:** SIAGIE, pasarela de pagos, generación automática de horarios, app móvil nativa y validación de IP o red en la marcación docente (C-01).

## Stack
- **Frontend** (`frontend/`): React 18 + TypeScript (strict) + Vite, React Router 6, TanStack Query 5, react-hook-form + zod, CSS propio con tokens (sin librería de UI), Vitest + Testing Library + MSW, Playwright (E2E).
- **Backend** (`backend/`): Java 21, Spring Boot 3 (Web, Security, Data JPA, AOP, Validation, Actuator), Maven (`./mvnw`), springdoc-openapi, Flyway, Caffeine, ShedLock, Apache POI (Excel), OpenPDF (PDF), uuid-creator (UUIDv7), Testcontainers, ArchUnit.
- **BD**: PostgreSQL 16, un esquema por módulo: `seguridad, auditoria, academico, horarios, matricula, pagos, asistencia, evaluacion` (+ `public.shedlock`).
- **Local**: `docker-compose.yml` con postgres:16 y Mailpit. Archivos en disco (`DiscoGestorArchivos`).
- **Producción (más adelante)**: S3 + CloudFront (SPA), Elastic Beanstalk (API), RDS, S3 prefirmado y SES (perfil `aws`).

## Estructura del backend y reglas de dependencia (las verifica ArchUnit)
Paquete raíz `pe.edu.gesedu`. Módulos: `seguridad, matricula, pagos, asistencia, academico, evaluacion, horarios, auditoria`, más `compartido` (excepciones, `@Auditable`, `UsuarioAutenticado`, paginación, reloj) y `transversal` (manejo de errores, OpenAPI, archivos, notificaciones, reportes, scheduling, caché).
Cada módulo tiene las capas `api` (controladores y DTO de entrada y salida), `aplicacion` (servicios transaccionales y casos de uso, DTO públicos), `dominio` (entidades, reglas y puertos) e `infraestructura` (JPA, adaptadores, jobs).
1. `api → aplicacion → dominio`; `infraestructura` implementa los puertos.
2. **Un módulo solo usa a otro a través de `pe.edu.gesedu.<otro>.aplicacion`**. Nunca importes el `dominio`, la `infraestructura` ni la `api` de otro módulo.
3. Todos pueden usar `compartido` y `transversal`. `compartido` no depende de nada.
4. Sin ciclos entre módulos. Grafo previsto: seguridad ← academico ← horarios ← asistencia; matricula → pagos; evaluacion → academico, horarios, matricula; auditoria no depende de ningún módulo.

## Convenciones obligatorias
- **Identificadores**: `String` con UUIDv7 generado en Java, en columnas `VARCHAR(36)`.
- **Nombres**: dominio, tablas y enums en español, siguiendo el modelo conceptual (`asistencia_alumno`, `PUNTUAL|TARDANZA|FALTA`).
- **Dinero**: `BigDecimal` con escala 2 y `RoundingMode.HALF_UP` en Java, `NUMERIC(12,2)` en BD y string decimal en JSON (`"350.00"`). El frontend nunca calcula montos (RNF-11).
- **Tiempo**: inyecta `Clock` (`RelojInstitucional`, zona `America/Lima`); está prohibido `now()` sin reloj. Se usa la hora del servidor, nunca la del dispositivo.
- **Seguridad**: pon `@PreAuthorize("hasAuthority('<PERMISO>')")` en los métodos públicos de los servicios de aplicación. Además valida el **alcance** en el servicio (apoderado → sus hijos, tutor → su sección…). La UI solo oculta; decide el backend (RF-49). Los permisos están en `compartido.seguridad.Permisos` y en la migración V3.
- **Auditoría**: toda operación crítica (pagos, matrícula, notas, publicación de horarios, cuentas, roles, cambios de asistencia, bloqueos) lleva `@Auditable`. Se escribe en la **misma transacción**. Las tablas de auditoría son de solo inserción (trigger + permisos). Usa `AuditoriaContexto` para registrar los cambios de valores.
- **Concurrencia**:
  - Vacantes: `UPDATE … WHERE vacantes_disponibles > 0` (RNF-09).
  - Pagos: `SELECT … FOR UPDATE` en un solo `@Transactional` (RNF-10).
  - Borrador de horario: `@Version` + `If-Match` → 409.
  - Además, siempre hay una restricción de BD como última defensa.
- **API**:
  - Prefijo `/api/v1`, recursos en plural.
  - Errores RFC 7807 (`ProblemDetail`) con la propiedad `codigo` (ver `docs/PLAN.md` §4.1).
  - Paginación `page/size/sort` → `PaginaRespuesta`.
  - Fechas ISO 8601 con offset `-05:00`.
- **Migraciones Flyway**: `backend/src/main/resources/db/migration/V<n>__<descripcion>.sql`. **Nunca edites una migración ya integrada en `main`**; corrige con una nueva. Los datos de demostración no van en Flyway: los carga `DemoDataSeeder` (perfil `demo`).
- **Configuración**: todo por variables de entorno (ver `.env.example`), sin secretos ni valores institucionales fijos en el código. La asistencia usa `@ConfigurationProperties` validado al arrancar (`gesedu.asistencia.*`: `teacherLateThresholdMinutes`, `studentLateCutoffTime`, `schoolDayClosingTime`, `timeZone`).
- **BD**: Flyway corre con `gesedu_owner` y la app con `gesedu_app` (sin UPDATE/DELETE sobre `auditoria`).
- **Frontend**:
  - Rutas exactamente como en los wireframes (`/login`, `/intranet/...`, ver `docs/PLAN.md` §5).
  - El access token vive en memoria y el refresh en una cookie HttpOnly.
  - Los tipos de la API se generan con `npm run api:types`; no los escribas a mano.
  - El menú se arma según los permisos de `/auth/me`.

## Cómo correr
```bash
cp .env.example .env            # completar JWT_SECRET y DEMO_PASSWORD
docker compose up -d            # postgres:16 + mailpit (UI en http://localhost:8025)
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local,demo
cd frontend && npm ci && npm run dev    # http://localhost:5173 (proxy /api → :8080)
```
Swagger: http://localhost:8080/swagger-ui.html (solo en el perfil `local`).

## Cómo probar
- Backend: `cd backend && ./mvnw verify` corre las unitarias (`*Test`), la integración con Testcontainers (`*IT`, requiere Docker) y ArchUnit.
- Frontend: `cd frontend && npm run lint && npm run typecheck && npm test`.
- Lo que hay que probar en cada módulo está en `docs/PLAN.md` §8:
  - Dominio puro: estrategias de consolidación, cruces, clasificación de asistencia y penalidades.
  - Integración: vacantes concurrentes, rollback de pagos e inmutabilidad de la auditoría.
  - Endpoints: 401, 403, 409 y 422 con problem+json.

## Flujo de trabajo
- Una rama por historia: `feat/<modulo>-uc<nn>-<desc>`. Un PR con al menos 1 revisión y CI en verde.
- Commits con el UC o RF entre paréntesis, por ejemplo `feat(matricula): confirmar matrícula con descuento atómico (UC-02, RNF-09)`.
- Antes de tocar `compartido`, `transversal` o el contrato de `*.aplicacion` de otro módulo, coordina con el equipo dueño (`docs/PLAN.md` §7).
