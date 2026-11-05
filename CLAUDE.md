# CLAUDE.md — Convenciones del proyecto GESEDU

Sistema web de gestión integral de un colegio de secundaria (PUCP, Ingeniería de Software 2026-2, 10 integrantes).
El plan vigente está en `docs/PLAN.md`: fases, modelo de datos, contrato de la API, contradicciones (C-xx), supuestos (S-xx) y preguntas (P-xx). **Léelo antes de implementar cualquier fase.**

## Prioridad de documentos
`docs/arquitectura.pdf` (v2.0) > `docs/ers.pdf` (v3) > `docs/catalogo_requisitos.pdf` (v4) > `docs/wireframes.pdf` > el resto (modelo conceptual, visión, procesos, matriz).
- Cita siempre RF, RNF, UC y DA con la **numeración del Catálogo v4** (RF-12 = correo de matrícula; RF-52 = inicio de sesión y recuperación).
- Si encuentras una contradicción, no la resuelvas en silencio: anótala en `docs/PLAN.md` § 13.
- Fuera de alcance: SIAGIE, pasarela de pagos, generación automática de horarios, **gestión de aulas (no existe la entidad Aula)**, app móvil, validación de IP o red en la marcación docente, consolidación mensual guardada.

## Stack y versiones
| Capa | Tecnología |
|---|---|
| Backend | Java 25 (Corretto/Temurin), **Spring Boot 4.1.x** (Spring Framework 7, Security 7, Hibernate 7, Jackson 3), Maven con wrapper |
| API docs | springdoc-openapi **3.1.x** (`springdoc-openapi-starter-webmvc-ui`); la 2.x no sirve |
| BD | PostgreSQL 18 (imagen `postgres:18`, volumen en `/var/lib/postgresql`), Flyway administrado por Spring Boot + `flyway-database-postgresql` |
| Jobs | Spring Scheduling + ShedLock 7.x (`shedlock-provider-jdbc-template`) |
| Archivos y correo | AWS SDK for Java 2.x (S3 presigner, SES) con el perfil `aws`; en local, Mailpit (SMTP) y `GestorArchivosDisco` |
| Frontend | Node 22 LTS, React 19, TypeScript estricto, Vite, TanStack Router 1.x (plugin de rutas por archivo), TanStack Query 5, react-hook-form + zod |
| Pruebas | JUnit 5, Spring Boot Test (`RestTestClient`), ArchUnit, Testcontainers 2.x (postgres:18); Vitest + Testing Library + MSW |

Particularidades de Spring Boot 4: starters modulares (`spring-boot-starter-webmvc`, `-aspectj`, `-flyway`, starters de prueba por módulo); Jackson 3 en `tools.jackson.*` (las anotaciones siguen en `com.fasterxml.jackson.annotation`); Flyway exige `spring-boot-starter-flyway`. Las versiones exactas de los parches se fijan en `backend/pom.xml` y `frontend/package.json`: no subas versiones mayores sin acordarlo.

## Estructura
```
backend/   pe.edu.gesedu.{seguridad, matricula, pagos, asistencia, academico, evaluacion, horarios, auditoria}
           cada módulo con las capas {api, aplicacion, dominio, infraestructura}
           + compartido (núcleo: errores, Dinero, NivelLogro, @Auditable, paginación)
           + transversal (config, planificador, archivos, notificaciones, reportes)
           resources/db/migration (Flyway), db/demo (solo con el perfil demo)
frontend/  src/routes (TanStack Router por archivo), src/modulos/<modulo>, src/compartido
docs/      documentos fuente, PLAN.md, api/, demo/, despliegue/
infra/     postgres/init (roles gesedu_migrador y gesedu_app)
.run/      configuraciones compartidas de IntelliJ
```

## Reglas de dependencia (verificadas con ArchUnit en `./mvnw verify`)
- Dentro de un módulo: `api → aplicacion → {dominio, infraestructura}`, `infraestructura → dominio`. `dominio` no depende de otras capas. `api` no usa `infraestructura`.
- **Un módulo solo usa a otro a través de su capa `aplicacion`** (servicios y `record` públicos). Nunca accede a entidades, repositorios ni controladores ajenos.
- Entre módulos, las relaciones JPA son ids `String`, no asociaciones (en la BD sí hay FK entre esquemas).
- Grafo acíclico: seguridad ← academico ← pagos ← matricula; evaluacion → {academico, matricula, seguridad}; horarios → {academico, seguridad, evaluacion}; asistencia → {horarios, academico, matricula, seguridad}; auditoria → seguridad. Las dependencias inversas van por puertos SPI (por ejemplo, `seguridad.aplicacion.VerificadorVinculoAcademico` implementado en `matricula`).
- `compartido` no depende de ningún módulo. `@Auditable` vive en `compartido.auditoria`; el aspecto, en el módulo `auditoria`.

## Convenciones de código
- Dominio, paquetes, clases, tablas y endpoints en **español** sin tildes en los identificadores (`anio_escolar`, `AsistenciaDocente`). Enums en mayúsculas (`PUNTUAL`, `TARDANZA`, `FALTA`).
- Ids: `String` con UUIDv7 (`@UuidGenerator(style = VERSION_7)`), columna `VARCHAR(36)`. Tablas con `created_at` y `updated_at`; `version` (`@Version`) donde haya edición concurrente.
- DTO como `record`; los controladores nunca devuelven entidades. Validación con Bean Validation en los DTO de entrada.
- `@Transactional` solo en servicios de `aplicacion`. Las operaciones que cruzan módulos (matrícula → obligaciones) corren en una única transacción.
- Montos: `BigDecimal` con escala 2 y `RoundingMode.HALF_UP` (clase `Dinero`), `NUMERIC(12,2)` en la BD; viajan como string decimal en JSON. La SPA no calcula montos.
- Fechas y horas: inyecta `Clock` (zona `gesedu.asistencia.time-zone`, America/Lima); nunca uses `LocalDate.now()` ni `Instant.now()` sin reloj. JSON en ISO 8601 con `-05:00`.
- Errores: lanza `ExcepcionNegocio(CodigoError.X)`; el manejador global responde RFC 9457 (`application/problem+json`) con la propiedad `codigo`. Catálogo de códigos en `docs/api/errores.md`.
- Seguridad: `@PreAuthorize("hasAuthority('PERMISO')")` en cada endpoint y verificación de **alcance** (mi sección, mis hijos) en el servicio. La SPA solo oculta opciones.
- Auditoría: anota con `@Auditable` toda transacción crítica (pagos, matrícula, notas, horarios, cuentas, roles, bloqueos); se escribe en la misma transacción. Las tablas `auditoria.*` son de solo inserción.
- Concurrencia: vacantes con UPDATE condicional; pagos con `SELECT … FOR UPDATE`; borrador de horario con `@Version` + `If-Match` (409 si la versión está desactualizada).
- Jobs `@Scheduled` + `@SchedulerLock`, siempre idempotentes y ejecutados como usuario «Sistema».
- Configuración por variables de entorno y perfiles (`local`, `demo`, `aws`); nada de secretos ni URLs fijas en el código. Ver `.env.example`.
- Migraciones de Flyway (`V<n>__descripcion.sql`): **hasta el primer despliegue compartido (QA o AWS) V1 a V11 se pueden editar**; avisa en el PR y que cada integrante ejecute `.\scripts
einiciar-bd.ps1` / `./scripts/reiniciar-bd.sh` (Flyway falla con un checksum distinto). **Desde la etiqueta `esquema-congelado` aplica «solo hacia adelante»**: no se modifica una migración integrada y los cambios van en `V12+`. Los datos de demo (`db/demo`) se pueden editar siempre. Ver `docs/PLAN.md` § 4.4 y S-26.
- Frontend: rutas en `src/routes` siguiendo los wireframes (`/login`, `/intranet/...`); acceso a la API solo mediante hooks de TanStack Query en `src/modulos/<modulo>/api.ts`; formularios con react-hook-form + zod; tipos de la API generados con `scripts/generar-tipos-api.*`; textos de la UI en español, como en los wireframes.
- Todo script `.sh` necesita su equivalente `.ps1`. Fin de línea LF, salvo `*.cmd` y `*.ps1` (`.gitattributes`).

## Cómo correr
Requisitos: Docker Desktop (WSL 2), JDK 25, Node 22 LTS. Primero, copia `.env.example` a `.env`.

| Acción | PowerShell | bash |
|---|---|---|
| Servicios (BD + correo) | `docker compose up -d postgres mailpit` | igual |
| Backend (local) | `cd backend; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"` | `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local` |
| Backend con datos de demo | `… "-Dspring-boot.run.profiles=local,demo"` | `… -Dspring-boot.run.profiles=local,demo` |
| Frontend | `cd frontend; npm ci; npm run dev` | `cd frontend && npm ci && npm run dev` |
| Reiniciar la BD | `.\scripts\reiniciar-bd.ps1` | `./scripts/reiniciar-bd.sh` |
| Tipos de la API para el frontend | `.\scripts\generar-tipos-api.ps1` | `./scripts/generar-tipos-api.sh` |

URL: SPA http://localhost:5173 (proxy `/api` → :8080) · Swagger http://localhost:8080/swagger-ui.html · Mailpit http://localhost:8025.
IntelliJ: configuraciones en `.run/`: `Docker Compose`, `Backend (local)`, `Backend (demo)`, `Frontend dev`, `Todas las pruebas`.

## Cómo probar
| Acción | PowerShell | bash |
|---|---|---|
| Backend completo (unitarias, Testcontainers, ArchUnit) | `cd backend; .\mvnw.cmd -B verify` | `cd backend && ./mvnw -B verify` |
| Una clase de prueba | `.\mvnw.cmd -Dtest=VacantesConcurrentesIT verify` | `./mvnw -Dtest=VacantesConcurrentesIT verify` |
| Frontend | `cd frontend; npm run lint; npm run typecheck; npm test -- --run; npm run build` | `cd frontend && npm run lint && npm run typecheck && npm test -- --run && npm run build` |

- Las pruebas de integración usan Testcontainers con `postgres:18` y los roles reales (`gesedu_migrador` para Flyway y `gesedu_app` para la aplicación). Si aparece «client version too old» con Docker Engine 29, sube el parche de Testcontainers 2.x.
- CI (`.github/workflows/ci.yml`) ejecuta los dos jobs en cada PR; `main` exige ambos en verde.

## Demo del 23/10/2026 (alcance reducido; `docs/PLAN.md` § 7.0)
Solo el esqueleto vertical: monorepo + Docker + `.run/`; backend con los 10 paquetes y capas (implementados: seguridad, auditoria, horarios); Flyway V1, V2, V3, V6, V9, V11 + semilla `db/demo/V1000`; login JWT + BCrypt, `@PreAuthorize`, bloqueo tras 5 intentos y errores RFC 9457 (sin refresh token ni recuperación); bloques horarios (listar: cualquier rol; crear: Administrador, `@Auditable`); consulta de auditoría; Swagger UI; frontend con login, inicio por rol, bloques y auditoría; `docs/demo/guion.md`. El resto sigue el plan después de la demo.

## Prioridades de la fase 1 (demo ampliada, después del 23/10)
- **Corte mínimo (P1):** inicio de sesión con los 4 roles + bloqueo, UC-21 completo, auditoría con exportación (UC-25) y Swagger UI.
- **Segunda prioridad (P2):** UC-02 con su correo y la recuperación de contraseña (UC-24 FA-5). No se empiezan hasta que P1 esté en `main` y ensayado.
- Desde la fase 2, I2 (Plataforma backend) apoya a Asistencia junto con I10.

## Flujo de trabajo
- Ramas `feat/<modulo>-<uc>` (por ejemplo, `feat/horarios-uc21`); PR a `main` con CI en verde y la revisión de otro equipo.
- Cada PR indica qué UC, RF y RNF cubre y qué criterios de aceptación de la ERS (flujos básicos y alternativos) se probaron.
