# GESEDU — Avance de la implementación

> Plan base: `docs/PLAN.md` v1.3. Plan de ejecución aprobado el 28/10/2026. Una rama por fase (`feat/fase-N`, creada desde la anterior). Un commit por tarea. Sin push ni cambios en `main`.

## Cómo retomar
1. `git checkout feat/fase-<N>` de la última fase en curso (ver tabla) y `git log --oneline` para ver la última tarea.
2. Primera tarea en estado «pendiente» de la tabla = siguiente trabajo.
3. Una tarea está terminada solo si pasan `cd backend; .\mvnw.cmd -B verify` y `cd frontend; npm run lint; npm run typecheck; npm test -- --run; npm run build`.
4. Docker Desktop debe estar encendido (Testcontainers). Tras editar migraciones: `.\scripts\reiniciar-bd.ps1`.

## Estado por fase
| Fase | Rama | Estado |
|---|---|---|
| 0 Base | `feat/fase-0` | **terminada** (0.1–0.11) |
| 1 Demo ampliada | `feat/fase-1` | en curso |
| 2 Matrícula y pagos | `feat/fase-2` | pendiente |
| 3 Académico y horarios | `feat/fase-3` | pendiente |
| 4 Asistencia | `feat/fase-4` | pendiente |
| 5 Evaluación | `feat/fase-5` | pendiente |
| 6 Cierre | `feat/fase-6` | pendiente |

## Tareas
| Tarea | Descripción | Estado | Commit |
|---|---|---|---|
| 0.1 | Higiene: restaurar `routeTree.gen.ts`, tag local `demo-23-10`, AVANCE y README | hecha | (ver `git log`) |
| 0.2 | CI GitHub Actions (frontend con matriz Node 22 y 24) + script `typecheck` (el script `test` llegó con 0.11) | hecha | ver `git log` |
| 0.3 | Dependencias fijadas: ShedLock 7.10.1, AWS SDK 2.57.5 (s3, sesv2), POI 5.5.1, OpenPDF 3.0.5 (paquete `org.openpdf`), mail, cache, Caffeine | hecha | ver `git log` |
| 0.4 | `compartido`: `EntidadBase` (Usuario, AnioEscolar y BloqueHorario ya la heredan), `Dinero`, `NivelLogro`, `ParametrosPaginacion`, `Etags`, `CodigoError` completo + `errores.md`. Pruebas: `NucleoCompartidoTest`, `ProblemDetailsIT` | hecha | ver `git log` |
| 0.5 | `AsistenciaProperties` validado (DA-10), propiedades `gesedu.asistencia.*` con valores 5 / 08:30 / 15:30, zona horaria unificada en `gesedu.asistencia.time-zone` (C-34). Prueba: `AsistenciaPropertiesTest` | hecha | ver `git log` |
| 0.6 | Migraciones completas: V2 (docente, apoderado, alumno, refresh_token, token_recuperacion), V3 (bimestre con EXCLUDE, grado, seccion, curso, competencia, CHECK de 10–12 meses), V4 pagos, V5 matrícula, V6 horarios completo con triggers de inmutabilidad, V7 evaluación, V8 asistencia (+ `cierre_diario`), V10 shedlock. Prueba: `MigracionesIT` (13 casos). **Cada integrante debe ejecutar `.\scripts
einiciar-bd.ps1`** (checksums cambiados, S-26) | hecha | ver `git log` |
| 0.7 | Backend: refresh token rotativo en cookie HttpOnly/Secure/SameSite=Strict (`/auth/refresco`, `/auth/cierre-sesion`, reutilización revoca la familia), caché Caffeine de cuentas y permisos (`CacheCuentas`), límite de 30 peticiones/min por IP en `/auth/*` (429). Pruebas: `RefreshTokenIT`, `CacheCuentasIT`, `LimiteAuthIT`, `JwtServiceTest`, `TokensOpacosTest`. El consumo desde la SPA (refresco transparente, logout) llega en 0.11 y 1.1 | hecha | ver `git log` |
| 0.8 | `GestorArchivos` (`GestorArchivosDisco` con URL HMAC de 5 min y endpoint `/api/v1/archivos-locales/{token}`; `GestorArchivosS3` con URL prefirmadas), `ReglasArchivo` (PDF/JPG/PNG ≤ 5 MB), `Notificador` (SMTP/SES), `Plantillas` HTML y `Notificaciones` (después del commit, asíncrono, 3 intentos). Mailpit sin perfil de compose (C-33). Pruebas: `ReglasYDiscoTest`, `GestorArchivosS3Test`, `ArchivosLocalesIT`, `PlantillasYSesTest`, `NotificacionesIT`, `NotificadorSmtpIT` (Mailpit en Testcontainers) | hecha | ver `git log` |
| 0.9 | `ConfiguracionPlanificador` (`@EnableScheduling`, ShedLock con `JdbcTemplateLockProvider` sobre `transversal.shedlock` y hora de la BD), actor «Sistema» en jobs (auditoría con `usuario_id` nulo), `GeneradorExcel` (POI; zona institucional; sin AWT). Pruebas: `ShedLockIT`, `GeneradorExcelTest` | hecha | ver `git log` |
| 0.10 | ArchUnit completo: capas, módulos solo vía `aplicacion`, sin ciclos, `@Transactional` solo en `aplicacion`, controladores sin entidades (retorno ni parámetros), entidades en `dominio`, repositorios en `infraestructura`, tiempo solo desde `Clock` y comprobación de que las reglas tienen código que revisar. Se eliminó `archunit.properties` (`failOnEmptyShould=false`): ya no hace falta | hecha | ver `git log` |
| 0.11 | Frontend base: `strict` + `noUncheckedIndexedAccess`; Vitest 5 + Testing Library + MSW 3; react-hook-form + zod + Prettier instalados; sesión solo en memoria (`sesion.ts`) con `SesionProvider` que la restaura con la cookie de refresh; cliente `api` con refresco transparente de una sola petición compartida, `ApiError` con `codigo`/`errores`/propiedades y soporte de cabeceras (ETag); guardias `exigirSesion/exigirPermiso`; `Layout` con menú lateral y barra inferior móvil; `tokens.css` y `responsive.css`; `scripts/generar-tipos-api.ps1/.sh` + `tipos.gen.ts` generado y comprobado por `tsc`. Pruebas: `cliente.test.ts`, `guardias.test.ts`, `SesionProvider.test.tsx`, `Layout.test.tsx`, `login.test.tsx`, `contrato.test.ts`. Prueba de humo real: backend `local,demo` arrancado con Flyway V1–V11 + V1000, login/refresh/reutilización verificados con curl | hecha | ver `git log` |
| 1.1–1.9 | Ver plan aprobado (fase 1) | pendiente | |
| 2.1–2.10 | Ver plan aprobado (fase 2, incluye RF-02/RF-05/RF-07 «actualizar») | pendiente | |
| 3.1–3.7 | Fase 3 | pendiente | |
| 4.1–4.6 | Fase 4 | pendiente | |
| 5.1–5.6 | Fase 5 | pendiente | |
| 6.1–6.6 | Fase 6 (6.6 = tabla de cobertura RF-01…52 y UC-01…25) | pendiente | |

## Supuestos tomados
- **S-28** El cronograma de cobros modificado no crea obligaciones retroactivas ni toca las obligaciones no PENDIENTES. La edición de datos personales reutiliza `USUARIO_GESTIONAR` y el cambio de sección `MATRICULA_REGISTRAR` (sin permisos nuevos).
- El tag `demo-23-10` no existía en el repositorio; se creó localmente sobre `8f876c6` (no se hizo push).

- **S-29** El diagnóstico legible de DA-10 lo da el `FailureAnalyzer` estándar de Spring Boot (`BindValidationFailureAnalyzer`), que nombra la propiedad y el mensaje de la restricción; no se escribe un analizador propio.

- **S-30** Se agrega la tabla `asistencia.cierre_diario` (fechas procesadas por el cierre diario, UC-12) y la columna `pagos.obligacion_pago.anio_escolar_id` (para RF-07, el job y las deudas de años anteriores); el plan no las nombra. `asignacion_docente` no lleva `anio_escolar_id` (se deriva de la sección).
- El CHECK de duración del año escolar (UC-13, 10–12 meses) obligó a corregir las fechas de la fixture de `BloquesHorariosIT` (2088-03-01 → 2088-02-01); la intención de la prueba no cambia.

- **S-31** `BaseIT` sube el límite de peticiones a `/auth/*` (100000/min) para que las pruebas puedan iniciar sesión muchas veces; `LimiteAuthIT` lo baja a 3. Las pruebas que modifican cuentas directamente en la base ahora invalidan la caché (`invalidarCuentas()`), como lo hará el servicio de usuarios.
- **S-32** La cookie de refresh tiene `Path=/api/v1/auth` y `Max-Age` de 7 días; `gesedu.seguridad.cookie-secure=true` también en local.

- **S-33** El indicador de salud del correo está desactivado (`management.health.mail.enabled=false`): un SMTP o SES caído no debe sacar la instancia del balanceador; el envío ya se reintenta.
- **S-34** Las plantillas de correo reales (recuperación de contraseña, resultado de matrícula) se agregan junto con sus casos de uso (1.6, 1.7); el motor ya está probado con una plantilla de prueba.

- **S-35** Versiones de las herramientas nuevas del frontend (las más recientes publicadas, compatibles con Vite 8): Vitest 5.0, MSW 3.0 (la opción de peticiones sin handler es `onUnhandledFrame`), jsdom 29, Testing Library 16, jest-dom 7, react-hook-form 7.91, @hookform/resolvers 6, zod 4.6. El plan citaba Vitest 3/4 y MSW 2 (C-32). `lint` sigue siendo `oxlint`.
- **S-36** `openapi-typescript` 7.13 declara `typescript@^5` como peer y el repo usa TS 6: se fuerza con `overrides` en `package.json` (solo herramienta de generación).
- **S-37** Los esquemas OpenAPI generados marcan todas las propiedades como opcionales (springdoc sin `@Schema(required)`), por lo que los tipos del frontend se mantienen a mano y `contrato.test.ts` comprueba con `tsc` que coinciden con el contrato generado.
- **S-38** La SPA ya no guarda nada en el navegador: el token de acceso vive en memoria y se recupera tras recargar con la cookie de refresh (`SesionProvider`). La pantalla de login con react-hook-form + zod, el mensaje de bloqueo y el enlace de recuperación quedan para la tarea 1.1.

## Contradicciones nuevas (C-32 a C-43)
Resoluciones aprobadas en el plan de ejecución; se trasladan a `docs/PLAN.md` § 13 en la tarea 0.1/0.2.
C-32 versiones de frontend (Vite 8, TS 6, oxlint se mantienen) · C-33 Mailpit sin perfil · C-34 `gesedu.asistencia.time-zone` · C-35 `registro_auditoria_id` se mantiene · C-36 tag creado · C-37 validaciones por tipo de estudiante · C-38 Directivo = permiso, no cargo · C-39 asistencia idempotente con 409 + registro existente · C-40 advertencia de disponibilidad en UC-22 · C-41 aviso de UC-05 por correo y pantalla · C-42 códigos de error faltantes · C-43 **resuelta**: RF-02, RF-05 y RF-07 «actualizar» dentro de alcance (tareas 2.8–2.10).

## Resumen de fases cerradas
### Fase 0 — Base (`feat/fase-0`, 28/10/2026)
Entregado: CI, dependencias, núcleo compartido, `AsistenciaProperties` (DA-10), esquema completo V1–V11, seguridad con refresh token rotativo / cierre de sesión revocable / caché / límite de peticiones, Gestor de Archivos (disco y S3), Notificador (SMTP y SES), ShedLock, `GeneradorExcel`, ArchUnit completo y frontend base. Backend: `./mvnw -B verify` en verde (124 pruebas); frontend: lint, typecheck, 30 pruebas y build en verde. Las contradicciones C-32 a C-43 están en `docs/PLAN.md` § 13.
RF/RNF: RF-01 (sin DELETE de usuarios), RF-48, RF-49, RF-50, RF-52, RNF-01, RNF-06, RNF-07, RNF-08 y la base de RNF-03.

## Cobertura RF / UC
(Se completa en la tarea 6.6.)
