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
| 0 Base | `feat/fase-0` | en curso |
| 1 Demo ampliada | `feat/fase-1` | pendiente |
| 2 Matrícula y pagos | `feat/fase-2` | pendiente |
| 3 Académico y horarios | `feat/fase-3` | pendiente |
| 4 Asistencia | `feat/fase-4` | pendiente |
| 5 Evaluación | `feat/fase-5` | pendiente |
| 6 Cierre | `feat/fase-6` | pendiente |

## Tareas
| Tarea | Descripción | Estado | Commit |
|---|---|---|---|
| 0.1 | Higiene: restaurar `routeTree.gen.ts`, tag local `demo-23-10`, AVANCE y README | hecha | (ver `git log`) |
| 0.2 | CI GitHub Actions (frontend con matriz Node 22 y 24) + script `typecheck`. Nota: el paso `npm test` del CI falla hasta que 0.11 agregue Vitest y el script `test` | hecha | ver `git log` |
| 0.3 | Dependencias fijadas: ShedLock 7.10.1, AWS SDK 2.57.5 (s3, sesv2), POI 5.5.1, OpenPDF 3.0.5 (paquete `org.openpdf`), mail, cache, Caffeine | hecha | ver `git log` |
| 0.4 | `compartido`: EntidadBase, Dinero, NivelLogro, paginación, ETag, CodigoError | pendiente | |
| 0.5 | `AsistenciaProperties` (DA-10) y zona horaria unificada | pendiente | |
| 0.6 | Migraciones V2–V8, V10 completas | pendiente | |
| 0.7 | Refresh token, cierre de sesión, caché de permisos, límite de peticiones | pendiente | |
| 0.8 | `GestorArchivos` y `Notificador` | pendiente | |
| 0.9 | ShedLock, usuario «Sistema», `GeneradorExcel` | pendiente | |
| 0.10 | ArchUnit completo | pendiente | |
| 0.11 | Frontend base | pendiente | |
| 1.1–1.9 | Ver plan aprobado (fase 1) | pendiente | |
| 2.1–2.10 | Ver plan aprobado (fase 2, incluye RF-02/RF-05/RF-07 «actualizar») | pendiente | |
| 3.1–3.7 | Fase 3 | pendiente | |
| 4.1–4.6 | Fase 4 | pendiente | |
| 5.1–5.6 | Fase 5 | pendiente | |
| 6.1–6.6 | Fase 6 (6.6 = tabla de cobertura RF-01…52 y UC-01…25) | pendiente | |

## Supuestos tomados
- **S-28** El cronograma de cobros modificado no crea obligaciones retroactivas ni toca las obligaciones no PENDIENTES. La edición de datos personales reutiliza `USUARIO_GESTIONAR` y el cambio de sección `MATRICULA_REGISTRAR` (sin permisos nuevos).
- El tag `demo-23-10` no existía en el repositorio; se creó localmente sobre `8f876c6` (no se hizo push).

## Contradicciones nuevas (C-32 a C-43)
Resoluciones aprobadas en el plan de ejecución; se trasladan a `docs/PLAN.md` § 13 en la tarea 0.1/0.2.
C-32 versiones de frontend (Vite 8, TS 6, oxlint se mantienen) · C-33 Mailpit sin perfil · C-34 `gesedu.asistencia.time-zone` · C-35 `registro_auditoria_id` se mantiene · C-36 tag creado · C-37 validaciones por tipo de estudiante · C-38 Directivo = permiso, no cargo · C-39 asistencia idempotente con 409 + registro existente · C-40 advertencia de disponibilidad en UC-22 · C-41 aviso de UC-05 por correo y pantalla · C-42 códigos de error faltantes · C-43 **resuelta**: RF-02, RF-05 y RF-07 «actualizar» dentro de alcance (tareas 2.8–2.10).

## Cobertura RF / UC
(Se completa en la tarea 6.6.)
