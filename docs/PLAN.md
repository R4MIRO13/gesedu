# GESEDU — Plan de implementación (v1, aprobado 21/10/2026)

> Plan aprobado. Las convenciones de trabajo están en `CLAUDE.md`. Las preguntas abiertas (§11.3) siguen pendientes de respuesta.
> Fuentes: `docs/arquitectura.pdf` (Arq v1.0), `docs/ers.pdf` (ERS v3), `docs/catalogo_requisitos.pdf` (Cat v3), `docs/modelo_conceptual.pdf` (MC v1, 6 diagramas), `docs/wireframes.pdf` (35 pantallas), `docs/vision.pdf` (v3), `docs/procesos.pdf`, `docs/matriz_trazabilidad.xlsx`.
> Prioridad ante contradicciones: **Arq > ERS > Catálogo > Wireframes > resto**. Las contradicciones están en §11.1 (C-xx), los supuestos en §11.2 (S-xx) y las preguntas en §11.3 (Q-xx).

## Contexto

El repositorio solo contiene `docs/` (un commit inicial). Hay que construir GESEDU desde cero con el stack fijado en la arquitectura (DA-01 a DA-11). La primera entrega es una demo en localhost que atraviesa todas las capas: login con los 4 roles y bloqueo (UC-24), elaboración de horario con detección de cruces (UC-21), registro de matrícula con descuento atómico de vacantes (UC-02) y auditoría consultable y exportable (UC-25). Luego se completan los 25 casos de uso por fases y por módulo, con 10 personas trabajando en paralelo.

---

## 1. Resumen de entendimiento

### 1.1 Módulos funcionales (Arq §1.2, ERS §1.2) → paquetes y esquemas
| Módulo funcional | Paquete(s) Java | Esquema(s) PG | UC | RF |
|---|---|---|---|---|
| Gestión de Matrícula y Pagos | `matricula`, `pagos` | `matricula`, `pagos` | UC-01 a UC-06 | RF-02, RF-04 a RF-11 |
| Control de Asistencia | `asistencia` | `asistencia` | UC-07 a UC-12 | RF-12 a RF-20 |
| Gestión Académica y Motor de Evaluación | `academico`, `evaluacion` | `academico`, `evaluacion` | UC-13 a UC-19 | RF-03, RF-21 a RF-37 |
| Gestión y Matriz Interactiva de Horarios | `horarios` | `horarios` | UC-20 a UC-23 | RF-38 a RF-46 |
| Seguridad, Auditoría y Transparencia | `seguridad`, `auditoria` | `seguridad`, `auditoria` | UC-24, UC-25 | RF-01, RF-47 a RF-50 |
| Transversal (no funcional) | `compartido`, `transversal` | (`public.shedlock`) | — | — |

### 1.2 Actores (ERS §2.1.1, Arq §6.1)
- **Administrador**: usuarios y roles, evaluación y confirmación de matrículas, validación de pagos, elaboración y publicación de horarios, reportes y auditoría. Incluye al "Personal Administrativo" de los procesos.
- **Directivo**: es un Administrador con `cargo` directivo (no es un perfil nuevo). Crea el año escolar (UC-13), configura cursos y competencias (UC-14) y consolida bimestres (UC-18).
- **Docente**: marca su asistencia (UC-07), registra su disponibilidad (UC-20), logros (UC-15), la regla de consolidación (UC-16) y las notas finales (UC-17). Si es **tutor** de una sección (asignación, no subclase), también hace UC-08, UC-09 y UC-10.
- **Alumno**: consulta cursos, notas, libreta, horario y su asistencia.
- **Apoderado**: solicita matrícula (UC-01), consulta obligaciones (UC-03), presenta comprobantes (UC-04) y consulta notas, libretas y horario de sus hijos. Recibe correos.
- **Tiempo**: dispara el cierre diario (UC-12) y los vencimientos y penalidades (RF-09, RF-10, DA-08).
- Sistemas externos: servicio de correo (SES; Mailpit en local). La entidad financiera no tiene integración. No hay SIAGIE.

### 1.3 Casos de uso (ERS v3 §3.1)
UC-01 Solicitar matrícula · UC-02 Registrar matrícula · UC-03 Consultar matrícula y obligaciones · UC-04 Presentar comprobante · UC-05 Validar y registrar pago · UC-06 Historial de pagos · UC-07 Asistencia de docente · UC-08 Asistencia de alumnos · UC-09 Actualizar estado (FALTA→TARDANZA) · UC-10 Exportar reportes de asistencia · UC-11 Calcular resumen mensual (incluido en UC-10, sin persistir) · UC-12 Faltas de docentes (job) · UC-13 Año escolar · UC-14 Cursos y competencias · UC-15 Registrar logros · UC-16 Regla de consolidación · UC-17 Notas finales · UC-18 Consolidar bimestre · UC-19 Consultar notas y libreta · UC-20 Disponibilidad docente · UC-21 Elaborar horario · UC-22 Publicar horario · UC-23 Consultar horario · UC-24 Gestionar la cuenta (incluye el login y el bloqueo) · UC-25 Auditoría (registro, consulta y exportación).
Casos de uso arquitectónicamente significativos (Arq tabla 4): UC-01, 02, 05, 07, 12, 16, 17, 18, 21, 24, 25.

### 1.4 Decisiones de arquitectura (Arq §3.2), en mis palabras
- **DA-01** SPA React 18 + TS + Vite, con React Router y TanStack Query (Arq §7.1). Solo valida formatos; las reglas de negocio están en el backend (Arq §4.1).
- **DA-02** Monolito modular Java 21 / Spring Boot 3. Cada módulo tiene las capas api/aplicacion/dominio/infraestructura y solo se comunica con otro a través de su capa `aplicacion`. Las operaciones entre módulos (matrícula → obligaciones) se resuelven en una sola transacción ACID local.
- **DA-03** PostgreSQL 16, un esquema por módulo, FK entre esquemas permitidas, NUMERIC exacto, índices únicos parciales y bloqueo de filas.
- **DA-04** Access token JWT de 15 min + refresh token revocable en una cookie HttpOnly/Secure/SameSite=Strict, guardado como hash en BD. BCrypt con costo 12 (Arq §4.3).
- **DA-05** RBAC: `RolAcceso` agrupa `Permiso` (código, módulo y acción). Se aplica con `@PreAuthorize` en el backend y una caché de permisos con invalidación (RNF-07 < 1 s).
- **DA-06** Aspecto `@Auditable` en la **misma transacción** de negocio. `RegistroAuditoria` y `CambioAuditoria` son de solo inserción: el usuario de la app tiene solo INSERT/SELECT y un trigger rechaza UPDATE/DELETE.
- **DA-07** Archivos en S3 privado con URL prefirmadas de 5 min, detrás del **Gestor de Archivos**. En local se usa una implementación en disco.
- **DA-08** `@Scheduled` + ShedLock con jobs idempotentes: cierre diario de asistencia (UC-12) y vencimientos y penalidades (RF-09, RF-10).
- **DA-09** Motor de evaluación con Strategy: `PromedioPonderado`, `Moda` y `UltimoRegistro`. El mapa de estrategias va indexado por `TipoRegla`, con AD=4, A=3, B=2 y C=1 (RF-37). El cálculo es idempotente (reemplaza, no duplica).
- **DA-10** Configuración de asistencia tipada (`@ConfigurationProperties`) y validada al arrancar, sin pantalla de edición. Se cambia con un reinicio y no reclasifica lo ya guardado.
- **DA-11** AWS: CloudFront + S3 (SPA), ALB + Elastic Beanstalk de 2 a 4 instancias, RDS Multi-AZ, SES, Secrets Manager y Parameter Store. En esta etapa solo preparamos el código (§10).
- Tácticas clave: vacantes con `UPDATE … WHERE vacantes_disponibles > 0`, `CHECK ≥ 0` e índice único parcial (RNF-09). Pagos con `@Transactional` + `SELECT … FOR UPDATE` + `BigDecimal` escala 2 HALF_UP + `NUMERIC(12,2)` + número de operación único (RNF-10, RNF-11). Horarios con un endpoint de validación previa, revalidación en la transacción, 3 índices únicos anticruce, `@Version` en el borrador (409) y un índice parcial para el horario vigente (RF-40 a RF-43).
- API: `/api/v1`, sustantivos en plural, RFC 7807 con `codigo` de negocio, paginación `page/size/sort`, fechas ISO 8601 con `-05:00`, montos como decimal de 2 cifras y OpenAPI con springdoc (Arq §7.3).
- Asistencia (ERS §3.4): la hora y la fecha siempre vienen del servidor con `America/Lima`. **No se valida la IP ni la red** (ERS §2.3 y §3.5.1, Arq §4.8). Solo se guardan los registros diarios (`asistencia_alumno` y `asistencia_docente`, únicos por persona y fecha). Los reportes se calculan bajo demanda, sin acumulados.

---

## 2. Estructura del repositorio

Monorepo (ver C-02):
```
gesedu/
├─ CLAUDE.md                      # convenciones (se crea al aprobar)
├─ README.md                      # cómo levantar y probar
├─ docker-compose.yml             # postgres:16, mailpit; perfil "full" con backend+frontend
├─ .env.example                   # todas las variables (sin secretos reales)
├─ .github/workflows/
│  ├─ backend.yml                 # mvn verify (Testcontainers, ArchUnit), path filter backend/**
│  └─ frontend.yml                # npm ci, lint, typecheck, vitest, build; path filter frontend/**
├─ infra/local/postgres/init/01-roles.sql   # crea gesedu_owner y gesedu_app (solo local)
├─ docs/  (PDFs existentes + PLAN.md + adr/ + api/openapi.json exportado en CI)
├─ backend/
│  ├─ pom.xml  (Maven wrapper mvnw)
│  ├─ Dockerfile
│  └─ src/
│     ├─ main/java/pe/edu/gesedu/
│     │  ├─ GeseduApplication.java
│     │  ├─ compartido/                 # sin dependencias hacia módulos
│     │  │  ├─ dominio/        Ids (generador UUIDv7), RangoFechas
│     │  │  ├─ excepciones/    NegocioException(codigo), ConflictoException, NoEncontradoException, AccesoDenegadoException
│     │  │  ├─ auditoria/      @Auditable, AuditoriaContexto (colector de CambioAuditoria)
│     │  │  ├─ seguridad/      UsuarioAutenticado (record del principal), Permisos (constantes de códigos)
│     │  │  ├─ paginacion/     PaginaRespuesta<T>
│     │  │  └─ tiempo/         RelojInstitucional (Clock con zona configurada)
│     │  ├─ transversal/
│     │  │  ├─ web/            ManejadorErrores (ProblemDetail RFC 7807), OpenApiConfig, JacksonConfig
│     │  │  ├─ archivos/       GestorArchivos (interfaz), DiscoGestorArchivos (@Profile local), S3GestorArchivos (@Profile aws)
│     │  │  ├─ notificaciones/ Notificador (interfaz), SmtpNotificador (Mailpit), SesNotificador; @Async
│     │  │  ├─ reportes/       ExcelExporter (Apache POI), PdfGenerator (OpenPDF)
│     │  │  ├─ programacion/   SchedulingConfig + ShedLock (JdbcTemplateLockProvider)
│     │  │  └─ cache/          CacheConfig (Caffeine)
│     │  ├─ seguridad/   { api, aplicacion, dominio, infraestructura }
│     │  ├─ auditoria/   { api, aplicacion, dominio, infraestructura }   # el aspecto AuditableAspect vive en infraestructura
│     │  ├─ academico/   { … }
│     │  ├─ horarios/    { … }
│     │  ├─ matricula/   { … }
│     │  ├─ pagos/       { … }
│     │  ├─ asistencia/  { … }
│     │  └─ evaluacion/  { … }
│     ├─ main/resources/
│     │  ├─ application.yml           # común, todo con ${VAR:default-seguro-no-secreto}
│     │  ├─ application-local.yml     # disco, SMTP Mailpit, swagger-ui on, logs legibles
│     │  ├─ application-aws.yml       # s3, ses, forward-headers, cookie secure, swagger-ui off
│     │  ├─ application-demo.yml      # activa DemoDataSeeder
│     │  └─ db/migration/V*.sql       # Flyway (§3.3)
│     └─ test/java/pe/edu/gesedu/
│        ├─ arquitectura/ReglasModulosTest.java  (ArchUnit)
│        ├─ <modulo>/dominio/…Test (unitarias)
│        ├─ <modulo>/…IT (Testcontainers)
│        └─ soporte/ (PostgresContainer compartido, fixtures, JwtDePrueba)
└─ frontend/
   ├─ package.json, vite.config.ts (proxy /api → backend, misma-origen como CloudFront), tsconfig.json, .eslintrc
   ├─ index.html
   └─ src/
      ├─ main.tsx
      ├─ app/          App.tsx, router.tsx (rutas §5), providers.tsx, queryClient.ts
      ├─ api/          http.ts (fetch + Bearer + refresh único en 401 + parseo problem+json),
      │                schema.d.ts (generado con openapi-typescript desde /v3/api-docs), <modulo>.ts (hooks TanStack)
      ├─ auth/         AuthProvider, useAuth, RequierePermiso, RutaProtegida
      ├─ layout/       IntranetLayout, MenuLateral (según permisos), NavInferiorMovil, Encabezado, Migas
      ├─ components/   Boton, Campo, Tabla, Paginacion, EstadoBadge, Alerta, Modal, Stepper, CargaArchivo,
      │                MatrizHorario, SelectorPerspectiva, VacioEstado, Dinero, Fecha
      ├─ modules/
      │  ├─ seguridad/   pages/LoginPage, UsuariosPage, components/FormUsuario
      │  ├─ inicio/      pages/InicioPage (tarjetas por rol)
      │  ├─ matricula/   pages/SolicitudNuevaPage, BandejaSolicitudesPage, EvaluarSolicitudPage
      │  ├─ pagos/       pages/MatriculaPagosPage, PresentarComprobantePage, ValidarComprobantesPage, HistorialFinancieroPage
      │  ├─ asistencia/  pages/MiAsistenciaPage, RegistrarAsistenciaPage, RegistroDelDiaPage, ReportesAsistenciaPage, ResumenMensualPage, AsistenciaDocentesPage
      │  ├─ academico/   pages/NuevoAnioEscolarPage, CursosCompetenciasPage, ConsolidarNotasPage, config/*
      │  ├─ evaluacion/  pages/RegistroNotasPage, ConfiguracionCursoPage, NotasFinalesPage, MisNotasPage
      │  ├─ horarios/    pages/DisponibilidadPage, ElaborarHorarioPage, PublicarHorarioPage, MiHorarioPage, config/*
      │  └─ auditoria/   pages/AuditoriaPage
      ├─ styles/       tokens.css (colores, espaciado, breakpoints 600/1024 px), global.css
      └─ utils/        fechas (America/Lima), formato S/ desde string decimal
```
**Reglas de dependencia** (ArchUnit, Arq §9.1):
1. `X.api` → `X.aplicacion`. `X.aplicacion` → `X.dominio` (+ `Y.aplicacion` de otros módulos). `X.infraestructura` implementa los puertos de `X.dominio` y `X.aplicacion`.
2. Un módulo **solo** importa `pe.edu.gesedu.Y.aplicacion..` de otro módulo, nunca su `dominio`, `infraestructura` ni `api`. Los DTO que cruzan módulos viven en `Y.aplicacion` (records).
3. Todos pueden usar `compartido` y `transversal`. `compartido` no depende de nadie.
4. Sin ciclos entre módulos (`slices().should().beFreeOfCycles()`).

Grafo previsto (acíclico): `seguridad` ← `academico` ← `horarios` ← `asistencia`; `academico`, `seguridad` ← `matricula` → `pagos` (`pagos` no depende de `matricula`: recibe los datos por parámetro); `evaluacion` → `academico`, `horarios`, `matricula`, `seguridad`; `auditoria` no depende de ningún módulo (lee el principal desde `compartido`).

---

## 3. Modelo de datos

Convenciones: PK `id VARCHAR(36)` con UUIDv7 generado en Java (`uuid-creator`). Columnas técnicas `created_at` y `updated_at TIMESTAMPTZ`. Montos `NUMERIC(12,2)` con `CHECK (>= 0)`. Enums como `VARCHAR` + `CHECK IN (…)`. FK entre esquemas permitidas (Arq §3.1). Los nombres de tablas y valores siguen el MC (en español, ver C-03).

### 3.1 Tablas por esquema (origen MC, salvo las marcadas como S-xx)
**seguridad** (MC p.1 y p.6)
- `usuario` (id, nombres, apellidos, correo UNIQUE, numero_documento UNIQUE NULL [S-01], hash_contrasena, activo, intentos_fallidos INT DEFAULT 0 CHECK ≥0, bloqueado_hasta, ultimo_acceso, rol_acceso_id FK NOT NULL). Índices: `lower(correo)` UNIQUE.
- `administrador` (id, usuario_id UNIQUE FK, cargo) · `docente` (id, usuario_id UNIQUE FK, codigo_docente UNIQUE, especialidad)
- `alumno` (id, usuario_id UNIQUE NULL, codigo_alumno UNIQUE, documento_identidad UNIQUE, nombres, apellidos, fecha_nacimiento [S-02], apoderado_id FK NOT NULL) — Apoderado 1 → 1..* Alumno.
- `apoderado` (id, usuario_id UNIQUE NULL, documento_identidad UNIQUE, nombres, apellidos, correo, numero_telefono, parentesco [S-02])
- `rol_acceso` (id, nombre UNIQUE, descripcion, perfil CHECK IN (ADMINISTRADOR, DOCENTE, ALUMNO, APODERADO) [S-03]) · `permiso` (id, codigo UNIQUE, modulo, accion, descripcion) · `rol_permiso` (rol_acceso_id, permiso_id, PK compuesta)
- `refresh_token` (id, usuario_id FK, token_hash UNIQUE, emitido_en, expira_en, revocado_en NULL, user_agent). Índice (usuario_id) WHERE revocado_en IS NULL.

**auditoria** (MC p.6, Arq §4.4)
- `registro_auditoria` (id, fecha_hora TIMESTAMPTZ DEFAULT now(), usuario_id NULL (NULL = "Sistema"), usuario_nombre, rol, accion, modulo, tipo_entidad, id_entidad_afectada, descripcion). Índices: (fecha_hora DESC), (usuario_id, fecha_hora DESC), (modulo, fecha_hora DESC), (accion, fecha_hora DESC).
- `cambio_auditoria` (id, registro_auditoria_id FK, campo, valor_anterior, valor_nuevo)
- **Inmutabilidad**: la función `auditoria.rechazar_modificacion()` hace `RAISE EXCEPTION` en triggers `BEFORE UPDATE OR DELETE … FOR EACH ROW` y `BEFORE TRUNCATE … FOR EACH STATEMENT` sobre ambas tablas. Además `REVOKE ALL … FROM gesedu_app; GRANT SELECT, INSERT … TO gesedu_app` (DA-06, RNF-08).

**academico** (MC p.4)
- `anio_escolar` (id, anio INT UNIQUE, fecha_inicio, fecha_fin, estado CHECK IN (PLANIFICADO, ACTIVO, CERRADO), creado_por). CHECK `fecha_fin > fecha_inicio`. La duración de 10 a 12 meses se valida en el dominio (UC-13). Índice único parcial `(estado) WHERE estado='ACTIVO'` (un solo año activo) [S-04].
- `bimestre` (id, anio_escolar_id FK, numero CHECK 1..4, fecha_inicio, fecha_fin). UNIQUE (anio_escolar_id, numero). Sin traslape: `EXCLUDE USING gist (anio_escolar_id WITH =, daterange(fecha_inicio, fecha_fin, '[]') WITH &&)` con `btree_gist` (RF-24).
- `grado` (id, anio_escolar_id FK, numero_nivel CHECK 1..5). UNIQUE (anio_escolar_id, numero_nivel).
- `seccion` (id, grado_id FK, letra_identificadora, capacidad INT [S-05], vacantes_disponibles INT, tutor_docente_id FK NULL → seguridad.docente). UNIQUE (grado_id, letra). **CHECK (vacantes_disponibles >= 0 AND vacantes_disponibles <= capacidad)** (RNF-09). Índice único parcial (tutor_docente_id) WHERE tutor_docente_id IS NOT NULL (un docente tutor de 0..1 secciones). Como `grado` cuelga del año, cada año tiene sus secciones.
- `curso` (id, anio_escolar_id FK, nombre, horas_lectivas INT CHECK > 0, habilitado BOOL). UNIQUE (anio_escolar_id, lower(nombre)). Aplica a todos los grados (RF-22) [S-06].
- `competencia` (id, curso_id FK, nombre, nivel_esperado_descripcion [S-07], orden). UNIQUE (curso_id, lower(nombre)).
- (`aula` pertenece al paquete horarios según la Arq tabla 7, así que va en el esquema **horarios**.)

**horarios** (MC p.5)
- `aula` (id, nombre UNIQUE, habilitado)
- `bloque_horario` (id, anio_escolar_id FK, dia_semana CHECK IN (LUNES…DOMINGO), hora_inicio, hora_fin). CHECK fin > inicio. Sin traslape en el mismo día: `EXCLUDE USING gist (anio_escolar_id WITH =, dia_semana WITH =, tsrange-equivalente con int4range(minutos) WITH &&)`. El recreo no es un bloque (C-17).
- `periodo_registro_disponibilidad` (id, anio_escolar_id UNIQUE FK, fecha_hora_inicio, fecha_hora_fin, estado CHECK IN (HABILITADO, CERRADO))
- `vinculacion_docente` (id, docente_id FK, anio_escolar_id FK, habilitado, carga_semanal_referencia INT). UNIQUE (docente_id, anio_escolar_id).
- `asociacion_docente_curso` (id, vinculacion_docente_id FK, curso_id FK, + docente_id denormalizado). UNIQUE (vinculacion_docente_id, curso_id). UNIQUE (id, docente_id, curso_id) para una FK compuesta.
- `disponibilidad_docente` (id, vinculacion_docente_id UNIQUE FK, fecha_hora_confirmacion, observaciones) — solo la última versión confirmada (RF-45).
- `bloque_disponible` (id, disponibilidad_docente_id FK, bloque_horario_id FK). UNIQUE (disponibilidad_docente_id, bloque_horario_id).
- `carga_horaria_curso` (id, grado_id FK, curso_id FK, bloques_semanales INT CHECK ≥ 0). UNIQUE (grado_id, curso_id) [C-13].
- `horario_borrador` (id, anio_escolar_id UNIQUE FK, fecha_hora_creacion, fecha_hora_actualizacion, **version BIGINT** para `@Version`)
- `asignacion_horario` (id, horario_borrador_id FK, bloque_horario_id FK, seccion_id FK, asociacion_docente_curso_id, docente_id, curso_id, aula_id FK). FK compuesta (asociacion_docente_curso_id, docente_id, curso_id) → asociacion. **UNIQUE (horario_borrador_id, bloque_horario_id, docente_id)**, **UNIQUE (…, aula_id)**, **UNIQUE (…, seccion_id)** (Arq §9.3).
- `horario_publicado` (id, anio_escolar_id FK, numero_version INT, fecha_hora_publicacion, vigente BOOL, publicado_por FK → administrador). UNIQUE (anio_escolar_id, numero_version). **Índice único parcial (anio_escolar_id) WHERE vigente** (Arq §9.3).
- `asignacion_publicada` (id, horario_publicado_id FK, bloque_horario_id, seccion_id, docente_id, curso_id, aula_id) con los mismos 3 UNIQUE por versión. Las filas son inmutables (solo inserción).

**matricula** (MC p.2)
- `solicitud_matricula` (id, codigo_seguimiento UNIQUE (formato `MAT-AAAA-NNNNN`, secuencia por año) [S-08], anio_escolar_id, alumno_id FK, apoderado_id FK, grado_id FK, fecha_solicitud, tipo_estudiante CHECK (NUEVO_INGRESO, RECURRENTE), estado_documentacion CHECK (COMPLETA, INCOMPLETA), estado_admision CHECK (ADMITIDO, NO_ADMITIDO, NO_APLICA) NULL, estado CHECK (PENDIENTE_REVISION, APROBADA, RECHAZADA), motivo_rechazo CHECK (NO_ADMITIDO, DUPLICIDAD, DEUDAS, SIN_VACANTE, VOLUNTAD_APODERADO) NULL [S-09], observacion, evaluada_por NULL). Índice único parcial **(alumno_id, anio_escolar_id) WHERE estado='PENDIENTE_REVISION'** (UC-01 FA-2).
- `documento_solicitud` (id, solicitud_id FK, tipo CHECK (CERTIFICADO_ESTUDIOS, FICHA_MATRICULA_ANTERIOR, DNI, OTRO), clave_archivo, nombre_original, content_type, tamanio_bytes) — metadatos del Gestor de Archivos.
- `matricula` (id, solicitud_id UNIQUE FK, alumno_id, anio_escolar_id, seccion_id FK, fecha_matricula, estado CHECK (CONFIRMADA, ANULADA), confirmada_por). **Índice único parcial (alumno_id, anio_escolar_id) WHERE estado='CONFIRMADA'** (RNF-09, RF-05).

**pagos** (MC p.2 + S-10)
- `politica_cobranza` (id, anio_escolar_id UNIQUE, porcentaje_penalidad NUMERIC(5,2) CHECK 0..100) [S-10] · `cronograma_cobro` (id, politica_cobranza_id FK, concepto CHECK (MATRICULA, PENSION), periodo (ej. "2027-03"), fecha_vencimiento, monto NUMERIC(12,2)). UNIQUE (politica_cobranza_id, concepto, periodo) (RF-06).
- `obligacion_pago` (id, matricula_id FK, alumno_id, apoderado_id (denormalizados para consultas por alcance), concepto, periodo, monto_base, fecha_vencimiento, monto_penalidad DEFAULT 0, estado CHECK (PENDIENTE, PAGADA, VENCIDA), penalidad_aplicada_en DATE NULL). UNIQUE (matricula_id, concepto, periodo). Índices: (apoderado_id, estado, fecha_vencimiento), (estado, fecha_vencimiento) para el job.
- `comprobante_pago` (id, obligacion_pago_id FK, apoderado_id FK, numero_operacion, medio_pago [S-11], fecha_pago, monto_presentado, referencia_comprobante (clave del archivo), estado_revision CHECK (PENDIENTE, APROBADO, OBSERVADO, RECHAZADO), observacion, revisado_por, fecha_revision). **Índice único parcial (numero_operacion) WHERE estado_revision IN ('PENDIENTE','APROBADO')** (RF-08) [S-12]. Índice único parcial (obligacion_pago_id) WHERE estado_revision='PENDIENTE' (un comprobante en revisión por obligación).
- `registro_pago` (= boleta) (id, obligacion_pago_id UNIQUE FK, comprobante_pago_id UNIQUE FK, numero_boleta UNIQUE (`B001-NNNNN`, secuencia) [S-13], fecha_registro, monto_total, registrado_por).

**asistencia** (MC p.3, ERS §3.4)
- `asistencia_alumno` (id, alumno_id FK, fecha DATE, estado CHECK (PUNTUAL, TARDANZA, FALTA), created_at, updated_at). **UNIQUE (alumno_id, fecha)**.
- `asistencia_docente` (id, docente_id FK, fecha, hora_registro TIMESTAMPTZ NULL, estado, created_at, updated_at). **UNIQUE (docente_id, fecha)**. CHECK `(estado='FALTA') = (hora_registro IS NULL)`.

**evaluacion** (MC p.4)
- `asignacion_docente` (id, docente_id, curso_id, seccion_id, regla_consolidacion CHECK (PROMEDIO_PONDERADO, MODA, ULTIMO_REGISTRO) NULL (NULL ⇒ PROMEDIO_PONDERADO por defecto, UC-16 FA-2)). UNIQUE (curso_id, seccion_id) [C-21].
- `registro_logro` (id, asignacion_docente_id FK, alumno_id, competencia_id, bimestre_id, nivel_logro CHECK (AD, A, B, C), fecha_registro TIMESTAMPTZ). Índice (asignacion_docente_id, bimestre_id). Granularidad: ver Q-01.
- `peso_consolidacion` (asignacion_docente_id, bimestre_id, peso NUMERIC(5,2) DEFAULT 1, PK compuesta) — pesos iguales por defecto (Arq §9.2).
- `consolidacion_bimestral` (id, bimestre_id UNIQUE, fecha_consolidacion, estado CHECK (EJECUTADA), ejecutada_por FK → administrador)
- `libreta` (id, alumno_id, anio_escolar_id, fecha_actualizacion, estado CHECK (PARCIAL, FINAL)). UNIQUE (alumno_id, anio_escolar_id).
- `nota_consolidada` (id, libreta_id FK, asignacion_docente_id, competencia_id, bimestre_id NULL, tipo CHECK (BIMESTRAL, FINAL), nivel_logro). Índices únicos parciales: (libreta_id, competencia_id, bimestre_id) WHERE tipo='BIMESTRAL'; (libreta_id, competencia_id) WHERE tipo='FINAL'. Recalcular hace UPSERT (idempotente).

**public**: `shedlock` (name PK, lock_until, locked_at, locked_by).

### 3.2 Roles de base de datos
- `gesedu_owner`: dueño de los esquemas; lo usa **solo Flyway** (`spring.flyway.user/password`).
- `gesedu_app`: lo usa la aplicación (datasource). Tiene `USAGE` en los esquemas y `SELECT, INSERT, UPDATE, DELETE` en todas las tablas **excepto** las de auditoría (`SELECT, INSERT`). Se configura con `ALTER DEFAULT PRIVILEGES` en cada migración.
- En local, los dos roles los crea `infra/local/postgres/init/01-roles.sql`. En AWS los crea un script de arranque que ejecuta el DBA una sola vez (documentado; no se escribe IaC ahora).

### 3.3 Migraciones Flyway (en orden; cada fase añade las suyas)
| # | Archivo | Contenido | Fase |
|---|---|---|---|
| V1 | `V1__extensiones_y_esquemas.sql` | `btree_gist`; esquemas seguridad, auditoria, academico, horarios, matricula, pagos, asistencia, evaluacion; grants base y default privileges con el placeholder `${appUser}` | 0 |
| V2 | `V2__seguridad.sql` | usuario, administrador, docente, apoderado, alumno, rol_acceso, permiso, rol_permiso, refresh_token | 0 |
| V3 | `V3__seguridad_roles_permisos.sql` | datos de referencia: 5 roles (S-03) y catálogo de permisos (§4.2) | 0 |
| V4 | `V4__auditoria.sql` | registro_auditoria, cambio_auditoria, índices, función y triggers de inmutabilidad, REVOKE/GRANT | 0 |
| V5 | `V5__shedlock.sql` | public.shedlock | 0 |
| V6 | `V6__academico.sql` | anio_escolar, bimestre (+EXCLUDE), grado, seccion (+CHECK vacantes), curso, competencia | 1 |
| V7 | `V7__horarios.sql` | aula, bloque_horario, periodo, vinculacion, asociacion, disponibilidad, bloque_disponible, carga_horaria, borrador, asignacion_horario (+3 UNIQUE), horario_publicado (+parcial vigente), asignacion_publicada | 1 |
| V8 | `V8__matricula.sql` | solicitud_matricula (+parcial pendiente), documento_solicitud, matricula (+parcial confirmada), secuencias de código | 1 |
| V9 | `V9__pagos.sql` | politica_cobranza, cronograma_cobro, obligacion_pago, comprobante_pago (+parciales), registro_pago, secuencia de boleta | 1 (obligaciones) / 3 (resto) |
| V10 | `V10__asistencia.sql` | asistencia_alumno, asistencia_docente | 5 |
| V11 | `V11__evaluacion.sql` | asignacion_docente, registro_logro, peso_consolidacion, consolidacion_bimestral, libreta, nota_consolidada | 6 |
| V12+ | ajustes por fase | índices por rendimiento (RNF-03) tras medir | 7 |

Regla: una migración ya integrada en `main` **no se edita**; se corrige con una nueva versión. Los datos de demostración **no** van en Flyway: los carga `DemoDataSeeder` (perfil `demo`, idempotente, contraseña desde `DEMO_PASSWORD`).

---

## 4. Contrato de la API

### 4.1 Convenciones (Arq §7.3)
- Base `/api/v1`. JSON. Fechas `2027-03-01`, instantes `2027-03-01T07:43:12-05:00`, montos como **string decimal** `"350.00"` (para que el frontend no haga aritmética monetaria, RNF-11).
- **Errores RFC 7807** (`application/problem+json`): `{type, title, status, detail, instance, codigo, errores?: [{campo, mensaje}], conflictos?: [...] }`. Estados: 400 `DATOS_INVALIDOS`, 401 `NO_AUTENTICADO` / `CREDENCIALES_INVALIDAS` / `CUENTA_BLOQUEADA` / `CUENTA_INACTIVA`, 403 `ACCESO_DENEGADO`, 404 `NO_ENCONTRADO` / `HORARIO_NO_PUBLICADO`, 409 `CONFLICTO_CONCURRENCIA` (versión, unicidad) / `SIN_VACANTE`, 422 regla de negocio (`MATRICULA_DUPLICADA`, `DEUDA_VENCIDA`, `CONFLICTO_HORARIO`, `MONTO_NO_COINCIDE`, `OPERACION_DUPLICADA`, `JORNADA_CERRADA`, …). Violaciones de unicidad de BD → 409 mediante un traductor de `DataIntegrityViolationException` por nombre de constraint.
- **Paginación**: `?page=0&size=20&sort=campo,asc` → `{contenido:[], pagina, tamanio, totalElementos, totalPaginas}`. `size` máx 100.
- Seguridad: `Authorization: Bearer <jwt>`. El refresh viaja en la cookie `gesedu_rt` (HttpOnly, Secure configurable, SameSite=Strict, Path=/api/v1/auth). Concurrencia optimista: header `If-Match: "<version>"` → 409 si no coincide.
- Alcance (además del permiso): el servicio verifica la pertenencia (apoderado → sus alumnos; alumno → él mismo; tutor → su sección; docente → sus asignaciones). Si falla → 403.

### 4.2 Catálogo de permisos (datos de referencia, RF-47, DA-05) y roles semilla
`USUARIO_CONSULTAR, USUARIO_GESTIONAR, USUARIO_DESBLOQUEAR, ROL_GESTIONAR, AUDITORIA_CONSULTAR, AUDITORIA_EXPORTAR, ESTRUCTURA_GESTIONAR, ANIO_ESCOLAR_GESTIONAR, CURSO_GESTIONAR, MATRICULA_SOLICITAR, MATRICULA_EVALUAR, MATRICULA_REGISTRAR, MATRICULA_CONSULTAR, MATRICULA_CONSULTAR_PROPIA, COBRANZA_GESTIONAR, OBLIGACION_CONSULTAR_PROPIA, COMPROBANTE_PRESENTAR, COMPROBANTE_VALIDAR, PAGO_HISTORIAL_CONSULTAR, ASISTENCIA_DOCENTE_MARCAR, ASISTENCIA_ALUMNO_REGISTRAR, ASISTENCIA_REPORTE_SECCION, ASISTENCIA_REPORTE_GENERAL, ASISTENCIA_CONSULTAR_PROPIA, LOGRO_REGISTRAR, REGLA_DEFINIR, NOTA_FINAL_CALCULAR, NOTA_CONSOLIDAR_BIMESTRE, NOTA_CONSULTAR_PROPIA, HORARIO_CONFIGURAR, HORARIO_GESTIONAR, HORARIO_PUBLICAR, HORARIO_CONSULTAR, DISPONIBILIDAD_REGISTRAR`.
Roles: **ADMINISTRADOR** (todo lo administrativo), **DIRECTIVO** (perfil Administrador: ANIO_ESCOLAR/CURSO/NOTA_CONSOLIDAR + consultas) [S-03, C-14], **DOCENTE**, **ALUMNO** y **APODERADO** (sus permisos propios). La matriz exacta rol × permiso se fija en V3 y se prueba en `PermisosPorRolIT`.

### 4.3 Endpoints por módulo
Columnas: Método · Ruta · Permiso · UC · RF/RNF.

**Seguridad**
| M | Ruta | Permiso | UC | RF/RNF |
|---|---|---|---|---|
| POST | /auth/login `{usuario (DNI o correo), contrasena}` → `{accessToken, expiraEn, usuario}` + cookie | público, con límite de peticiones | UC-24 | RNF-01, RNF-06 |
| POST | /auth/refresh (rota el refresh) | cookie | UC-24 | DA-04 |
| POST | /auth/logout (revoca) | autenticado | UC-24 | DA-04 |
| GET | /auth/me → perfil, rol, permisos, datos de perfil (sección, hijos…) | autenticado | UC-24 | RF-49 |
| GET | /usuarios?rol&estado&q&page | USUARIO_CONSULTAR | UC-24 | RF-01, RF-47 |
| POST | /usuarios | USUARIO_GESTIONAR | UC-24 (FA-1, FA-2) | RF-01, RF-02, RF-47 |
| GET / PUT | /usuarios/{id} | USUARIO_CONSULTAR / USUARIO_GESTIONAR | UC-24 | RF-01 |
| POST | /usuarios/{id}/desactivar · /activar | USUARIO_GESTIONAR | UC-24 | RF-01 (C-15) |
| POST | /usuarios/{id}/desbloquear | USUARIO_DESBLOQUEAR | UC-24 FA-3 | RNF-06 |
| GET | /roles · /permisos | ROL_GESTIONAR | UC-24 | RF-47 |
| PUT | /roles/{id}/permisos (invalida la caché) | ROL_GESTIONAR | UC-24 | RF-47, RNF-07 |
| GET | /alumnos/mios (apoderado: hijos; alumno: él mismo) | OBLIGACION_CONSULTAR_PROPIA o NOTA_CONSULTAR_PROPIA | UC-03, UC-19 FA-1 | RF-32 |

**Auditoría**
| GET | /auditoria?usuarioId&modulo&accion&desde&hasta&page | AUDITORIA_CONSULTAR | UC-25 | RF-50, RNF-03 |
| GET | /auditoria/{id} (con cambios) | AUDITORIA_CONSULTAR | UC-25 | RF-50 |
| GET | /auditoria/exportar?mismos filtros → `.xlsx` (máx. 50 000 filas, streaming SXSSF) | AUDITORIA_EXPORTAR | UC-25 | RF-50 |

**Académico**
| GET / POST | /anios-escolares (POST incluye 4 bimestres) | ANIO_ESCOLAR_GESTIONAR (GET: autenticado) | UC-13 (FA-1, FA-2) | RF-03, RF-24 |
| GET / PUT | /anios-escolares/{id} · POST /{id}/activar · /{id}/cerrar | ANIO_ESCOLAR_GESTIONAR | UC-13 | RF-03 (S-04) |
| GET / POST | /anios-escolares/{id}/grados · /grados/{id}/secciones | ESTRUCTURA_GESTIONAR | — (config) | RF-03, RF-04 |
| PUT | /secciones/{id} (capacidad, tutor) | ESTRUCTURA_GESTIONAR | — | RF-04, RF-13 |
| GET | /anios-escolares/{id}/secciones?conVacantes | MATRICULA_EVALUAR / ESTRUCTURA_GESTIONAR | UC-02 | RF-04 |
| GET / POST | /anios-escolares/{id}/cursos | CURSO_GESTIONAR (GET: ACADEMICO autenticado) | UC-14 | RF-22 |
| PUT / DELETE | /cursos/{id} | CURSO_GESTIONAR | UC-14 | RF-22 |
| POST / PUT / DELETE | /cursos/{id}/competencias · /competencias/{id} | CURSO_GESTIONAR | UC-14 | RF-23 |
| POST | /anios-escolares/{id}/cursos/copia `{anioOrigenId}` | CURSO_GESTIONAR | UC-14 FA-1 | RF-22, RF-23 |

**Horarios**
| GET / POST / PUT / DELETE | /aulas | HORARIO_CONFIGURAR | — | RF-46 |
| GET / PUT | /anios-escolares/{id}/bloques (reemplazo del conjunto) | HORARIO_CONFIGURAR | — | RF-45 |
| GET / PUT | /anios-escolares/{id}/periodo-disponibilidad | HORARIO_CONFIGURAR | UC-20 | RF-45 |
| GET / POST / PUT | /anios-escolares/{id}/vinculaciones (docente, habilitado, carga ref., cursos asociados) | HORARIO_CONFIGURAR | UC-21 pre | RF-46 |
| GET / PUT | /anios-escolares/{id}/cargas-horarias | HORARIO_CONFIGURAR | UC-22 pre | RF-42 |
| GET | /disponibilidades/mia?anioId (matriz + carga + aviso de insuficiencia) | DISPONIBILIDAD_REGISTRAR | UC-20 | RF-45 |
| PUT | /disponibilidades/mia `{anioId, bloqueIds[], observaciones}` | DISPONIBILIDAD_REGISTRAR | UC-20 (FA-1, FA-2, FE-1) | RF-45 |
| GET | /disponibilidades?anioId (consolidado + docentes sin registro) | HORARIO_GESTIONAR | — | RF-45 |
| GET | /horarios/borrador?anioId → `{id, version}` (lo crea si no existe) | HORARIO_GESTIONAR | UC-21 paso 2 | RF-38, RF-43 |
| GET | /horarios/{id}/asignaciones?perspectiva=SECCION\|DOCENTE\|AULA&recursoId | HORARIO_GESTIONAR | UC-21 FA-3 | RF-38 |
| GET | /horarios/{id}/opciones?bloqueId&seccionId&cursoId&docenteId → cursos, docentes asociados con su disponibilidad y ocupación, aulas libres | HORARIO_GESTIONAR | UC-21 paso 4 | RF-46 |
| POST | /horarios/{id}/asignaciones/validacion (no persiste) → `{valida, conflictos:[{tipo: DOCENTE_NO_ASOCIADO\|DOCENTE_NO_DISPONIBLE\|CRUCE_DOCENTE\|CRUCE_AULA\|CRUCE_SECCION\|RECURSO_NO_HABILITADO, recurso, asignacionExistente:{seccion, curso, docente, aula, dia, bloque}}]}` | HORARIO_GESTIONAR | UC-21 paso 6 | RF-40, RF-41, RF-46 |
| POST | /horarios/{id}/asignaciones (If-Match) → 201, 409 versión/unicidad, 422 `CONFLICTO_HORARIO` + conflictos | HORARIO_GESTIONAR | UC-21 (FE-1, FE-2) | RF-39, RF-40, RF-41 |
| PUT / DELETE | /horarios/{id}/asignaciones/{asigId} (If-Match) | HORARIO_GESTIONAR | UC-21 FA-1, FA-2 | RF-39 |
| GET | /horarios/{id}/completitud → por sección y curso: asignadas, requeridas, diferencia | HORARIO_GESTIONAR | UC-22 paso 2, UC-21 (panel) | RF-42 |
| POST | /horarios/publicaciones `{anioId, versionBorrador}` → 201 o 422 `HORARIO_INCOMPLETO` | HORARIO_PUBLICAR | UC-22 (FA-1) | RF-42, RF-43 |
| GET | /horarios/vigente?anioId&perspectiva&recursoId → matriz o 404 `HORARIO_NO_PUBLICADO` | HORARIO_CONSULTAR (+alcance) | UC-23 (FA-1, FA-2) | RF-38, RF-44 |
| GET | /horarios/vigente/asignaciones/{id} (detalle) | HORARIO_CONSULTAR | UC-23 paso 6 | RF-38 |

**Matrícula**
| POST | /solicitudes-matricula/documentos/carga → URL prefirmada + clave | MATRICULA_SOLICITAR | UC-01 | DA-07, RNF-12 |
| POST | /solicitudes-matricula | MATRICULA_SOLICITAR | UC-01 (FA-1, FA-2) | RF-02 |
| GET | /solicitudes-matricula/mias | MATRICULA_CONSULTAR_PROPIA | UC-01 post | — |
| GET | /solicitudes-matricula?estado&q&page (bandeja) | MATRICULA_EVALUAR | UC-02 paso 1 | RF-05 |
| GET | /solicitudes-matricula/{codigo} → datos, documentos (URL de descarga), **validaciones** (matrícula activa, deudas, admisión, vacantes), secciones con vacantes y resumen de costos | MATRICULA_EVALUAR | UC-02 pasos 2–7 | RF-02, RF-04, RF-05, RF-06 |
| PUT | /solicitudes-matricula/{id}/evaluacion `{tipoEstudiante, estadoAdmision}` | MATRICULA_EVALUAR | UC-02 pasos 3–4 | RF-05 |
| POST | /solicitudes-matricula/{id}/rechazo `{motivo, observacion}` | MATRICULA_EVALUAR | UC-02 FA-1…FA-5 | RF-05 |
| POST | /matriculas `{solicitudId, seccionId, costosAceptados:true}` → 201 o 409 `SIN_VACANTE` / 422 `MATRICULA_DUPLICADA`, `DEUDA_VENCIDA`, `NO_ADMITIDO` | MATRICULA_REGISTRAR | UC-02 pasos 9–13 | RF-04, RF-05, RF-07, RNF-09, RNF-10 |
| GET | /matriculas?anioId&seccionId&page | MATRICULA_CONSULTAR | — | RF-05 |
| GET | /alumnos/{id}/matricula?anioId | MATRICULA_CONSULTAR_PROPIA (+alcance) / MATRICULA_CONSULTAR | UC-03 (FA-2) | RF-05 |

**Pagos**
| GET / PUT | /anios-escolares/{id}/politica-cobranza (+cronograma) | COBRANZA_GESTIONAR | — (pre UC-01/02) | RF-06 |
| GET | /alumnos/{id}/obligaciones?anioId | OBLIGACION_CONSULTAR_PROPIA (+alcance) | UC-03 (FA-1) | RF-07, RF-09, RF-11 |
| POST | /comprobantes/carga → URL prefirmada | COMPROBANTE_PRESENTAR | UC-04 | DA-07 |
| POST | /comprobantes `{obligacionId, numeroOperacion, medioPago, fechaPago, montoPresentado, claveArchivo}` | COMPROBANTE_PRESENTAR | UC-04 (FA-1, FA-2) | RF-08 |
| GET | /comprobantes?estado=PENDIENTE&page | COMPROBANTE_VALIDAR | UC-05 paso 1 | RF-08 |
| GET | /comprobantes/{id} (obligación, base, penalidad, total, checks, URL del voucher) | COMPROBANTE_VALIDAR | UC-05 pasos 2–4 | RF-08, RF-10 |
| POST | /comprobantes/{id}/validacion → registra el pago y la boleta y marca PAGADA (1 transacción) | COMPROBANTE_VALIDAR | UC-05 pasos 5–9 | RF-08, RF-09, RNF-10, RNF-11 |
| POST | /comprobantes/{id}/observacion · /rechazo | COMPROBANTE_VALIDAR | UC-05 FA-1, FA-2 | RF-08 |
| GET | /alumnos/{codigo}/historial-financiero?anioId | PAGO_HISTORIAL_CONSULTAR | UC-06 (FA-1, FA-2) | RF-11 |

**Asistencia**
| GET | /asistencias/docentes/mia/hoy → hora del servidor, primera clase, tolerancia, estado del día | ASISTENCIA_DOCENTE_MARCAR | UC-07 | RF-17 |
| POST | /asistencias/docentes (marcar) → 201, 200 (FA-1 existente) o 422 `SIN_CLASES` / `DIA_NO_LECTIVO` / `JORNADA_CERRADA` | ASISTENCIA_DOCENTE_MARCAR | UC-07 (FA-1…FA-3) | RF-12, RF-17, RF-18, RF-19 |
| GET | /asistencias/docentes/mia?desde&hasta | ASISTENCIA_DOCENTE_MARCAR | UC-07 (vista) | RF-19 |
| GET | /asistencias/docentes?fecha | ASISTENCIA_REPORTE_GENERAL | UC-12 (consulta) | RF-18, RF-20 |
| GET | /secciones/{id}/asistencias?fecha (lista alfabética + registros existentes) | ASISTENCIA_ALUMNO_REGISTRAR (+tutor) | UC-08 pasos 2–4 | RF-13 |
| POST | /secciones/{id}/asistencias `{registros:[{alumnoId, estado: PUNTUAL\|FALTA}]}` (lote atómico) | ASISTENCIA_ALUMNO_REGISTRAR | UC-08 (FA-1…FA-5) | RF-13 |
| PATCH | /asistencias/alumnos/{id} `{estado:"TARDANZA"}` | ASISTENCIA_ALUMNO_REGISTRAR (+tutor) | UC-09 (FA-1…FA-3) | RF-12, RF-14 |
| GET | /asistencias/alumnos/mia?mes | ASISTENCIA_CONSULTAR_PROPIA | (inicio alumno) | RF-15 |
| GET | /reportes/asistencia?ambito=ALUMNOS\|DOCENTES&anio&mes&seccionId&formato=json\|xlsx\|pdf | ASISTENCIA_REPORTE_SECCION (solo su sección) / ASISTENCIA_REPORTE_GENERAL | UC-10, UC-11 (FA-1…FA-4) | RF-15, RF-16, RF-20 |

**Evaluación**
| GET | /asignaciones/mias (cursos que dicta) | LOGRO_REGISTRAR | UC-15 paso 1 | RF-30 |
| GET | /asignaciones/{id} (curso, sección, competencias, regla, bimestre vigente) | LOGRO_REGISTRAR | UC-16 paso 2 | RF-30, RF-33 |
| GET | /asignaciones/{id}/logros?bimestreId | LOGRO_REGISTRAR | UC-15 paso 2 | RF-21 |
| PUT | /asignaciones/{id}/logros `{bimestreId, registros:[{alumnoId, competenciaId, nivel}]}` → aviso si está incompleto (FA-2) | LOGRO_REGISTRAR | UC-15 (FA-1, FA-2) | RF-21, RF-48 |
| PUT | /asignaciones/{id}/regla `{regla}` | REGLA_DEFINIR | UC-16 (FA-1, FA-2) | RF-25 |
| GET | /asignaciones/{id}/notas-consolidadas | LOGRO_REGISTRAR | UC-17 paso 2 | RF-28 |
| POST | /asignaciones/{id}/notas-finales | NOTA_FINAL_CALCULAR | UC-17 | RF-25, RF-27, RF-37 |
| GET | /consolidaciones/avance?anioId&bimestreId | NOTA_CONSOLIDAR_BIMESTRE | UC-18 paso 2 (FA-2) | RF-26 |
| POST | /consolidaciones `{bimestreId}` → 422 `REGISTRO_INCOMPLETO` + lista de docentes pendientes | NOTA_CONSOLIDAR_BIMESTRE | UC-18 (FA-1, FA-2) | RF-26, RF-27, RF-34, RF-36 |
| GET | /alumnos/{id}/cursos | NOTA_CONSULTAR_PROPIA (+alcance) | UC-19 paso 2 | RF-31, RF-32 |
| GET | /alumnos/{id}/notas?cursoId | NOTA_CONSULTAR_PROPIA | UC-19 pasos 4–5 | RF-29, RF-33 |
| GET | /alumnos/{id}/libretas/{anioId}.pdf?bimestre | NOTA_CONSULTAR_PROPIA | UC-19 (FA-2) | RF-34, RF-35, RF-36 |

**Archivos (solo en el perfil local)**: `PUT/GET /api/v1/archivos/{token}` emula la URL prefirmada (token HMAC de 5 min). En `aws` se usan URL de S3.
**Operación**: `/actuator/health` (ALB), `/v3/api-docs`, `/swagger-ui.html` (solo en local).

---

## 5. Mapeo de pantallas (wireframes → rutas)

| WF (pág.) | Ruta frontend | Componentes principales | Endpoints | Roles | UC |
|---|---|---|---|---|---|
| p1 | `/login` | FormLogin, Alerta de bloqueo | /auth/login | público | UC-24 |
| p2–3 | `/intranet/inicio` | tarjetas por rol (clase actual y siguiente, notas del bimestre, asistencia del mes, matrícula) | /auth/me, /horarios/vigente, /alumnos/{id}/notas, /asistencias/alumnos/mia, /alumnos/{id}/matricula | todos | — |
| p4–5 | `/intranet/matricula/solicitud` | Stepper (Estudiante, Documentos, Grado, Confirmar), CargaArchivo | /solicitudes-matricula/documentos/carga, POST /solicitudes-matricula | Apoderado | UC-01 |
| p6 | `/intranet/matricula/solicitudes` y `/intranet/matricula/solicitudes/:codigo` | Bandeja + panel de evaluación (validaciones, secciones con vacantes, resumen de costos, casilla de aceptación, Rechazar/Confirmar) | §4.3 Matrícula | Admin | UC-02 |
| p7–8 | `/intranet/pagos` | resumen de deuda, tabla de obligaciones, selector de hijo | /alumnos/mios, /alumnos/{id}/matricula, /alumnos/{id}/obligaciones | Apoderado (Alumno: Q-07) | UC-03 |
| p9–10 | `/intranet/pagos/comprobante?obligacion=:id` | formulario + CargaArchivo | /comprobantes/carga, POST /comprobantes | Apoderado | UC-04 |
| p11 | `/intranet/pagos/validar` | lista de pendientes + detalle (checks, monto vs. total) + Rechazar/Observar/Validar | /comprobantes… | Admin | UC-05 |
| p12 | `/intranet/pagos/historial/:codigoAlumno` | pestañas Obligaciones / Boletas y pagos / Deudas y penalidades | /alumnos/{codigo}/historial-financiero | Admin | UC-06 |
| p13–14 | `/intranet/mi-asistencia` | reloj del servidor, primera clase, botón Registrar, últimos 5 días (**sin** el indicador "Conectado a la red", C-01) | /asistencias/docentes/mia/hoy, POST /asistencias/docentes | Docente | UC-07 |
| p15 | `/intranet/asistencia/:seccion/registrar` | lista alfabética con Puntual/Falta, "Marcar todos como Puntual", resaltado de pendientes | GET/POST /secciones/{id}/asistencias | Docente tutor | UC-08 |
| p16 | `/intranet/asistencia/:seccion/registro-del-dia` | tabla del día + modal "Cambiar a Tardanza" (límite StudentLateCutoffTime) | PATCH /asistencias/alumnos/{id} | Docente tutor | UC-09 (C-06) |
| p17 | `/intranet/asistencia/reportes` | filtros ámbito, mes y sección; vista previa; descarga Excel/PDF | /reportes/asistencia | Admin; tutor (solo su sección) | UC-10, UC-11 (C-05) |
| p18 | `/intranet/asistencia/resumen-mensual` (en vez de `/consolidacion`, C-05) | resumen por sección **calculado al consultar**, sin estados "Consolidado" | /reportes/asistencia?formato=json | Admin | UC-10, UC-11 |
| p19 | `/intranet/asistencia/docentes/:fecha` | tabla de docentes con clases (solo consulta) | /asistencias/docentes?fecha | Admin | UC-12 |
| p20 | `/intranet/academico/anio-escolar/nuevo` | Paso 1 fechas, Paso 2 bimestres, línea de tiempo con superposición | POST /anios-escolares | Directivo | UC-13 |
| p21 | `/intranet/academico/:anio/cursos` | lista de cursos + editor de curso y competencias + "Copiar configuración" | §4.3 Académico | Directivo | UC-14 |
| p22 | `/intranet/notas/:curso/:seccion/bimestre-:n` | grilla alumnos × competencias con validación AD/A/B/C y aviso de incompletos | /asignaciones/{id}/logros | Docente | UC-15 |
| p23 | `/intranet/cursos/:cursoSeccion/configuracion` | tarjetas de las 3 reglas con ejemplo, regla actual y por defecto | PUT /asignaciones/{id}/regla | Docente | UC-16 |
| p24 | `/intranet/notas/:curso/:seccion/finales` | B-I…B-IV + Final por competencia, botón "Calcular notas finales" | /asignaciones/{id}/notas-consolidadas, POST notas-finales | Docente | UC-17 |
| p25 | `/intranet/academico/consolidar-notas` | avance por curso y sección, docentes pendientes, botón Consolidar | /consolidaciones… | Directivo | UC-18 |
| p26–27 | `/intranet/notas` | cursos con la nota del bimestre, detalle por competencia y nivel esperado, descargar informe y libreta | /alumnos/{id}/cursos, /notas, /libretas | Alumno, Apoderado | UC-19 |
| p28 | `/intranet/disponibilidad/:anio` | matriz clicable de días × bloques, carga vs. disponibles, observaciones, Confirmar | /disponibilidades/mia | Docente | UC-20 |
| p29 | `/intranet/horarios/:anio/elaborar` | SelectorPerspectiva, **MatrizHorario**, panel "Nueva asignación" con opciones filtradas, conflicto en tiempo real y panel de carga horaria | §4.3 Horarios | Admin | UC-21 |
| p30 | `/intranet/horarios/:anio/publicar` | tabla de completitud, botón Publicar (deshabilitado si hay observaciones) | /completitud, POST /publicaciones | Admin | UC-22 |
| p31–33 | `/intranet/horario` | MatrizHorario de solo lectura, detalle del bloque, estado "aún no ha sido publicado" | /horarios/vigente | Alumno, Apoderado, Docente (sus asignaciones), Admin (cualquier perspectiva) | UC-23 |
| p34 | `/intranet/usuarios` | tabla con filtros, modal Nuevo usuario (duplicado de DNI, vínculo con matrícula, vista de permisos del rol), Desbloquear/Desactivar | §4.3 Seguridad | Admin | UC-24 |
| p35 | `/intranet/auditoria` | filtros (usuario, módulo, acción, fechas), tabla paginada, Exportar | §4.3 Auditoría | Admin | UC-25 |
| (sin WF) S-14 | `/intranet/configuracion/{secciones, aulas, bloques, disponibilidad, vinculaciones, carga-horaria, cobranza}` | formularios CRUD con el estilo de los WF | endpoints de configuración | Admin / Directivo | pre de UC-01/02/20/21/22 |

Responsive (RNF-02): las páginas de alumno, apoderado y docente tienen versión móvil (WF p3, 5, 8, 10, 14, 27, 32, 33) con `NavInferiorMovil` (Inicio · Notas · Horario · Pagos). El menú lateral se arma con los **permisos** de `/auth/me` (RF-49); el backend decide siempre.

---

## 6. Fases de implementación

Cada fase incluye: tareas, criterios de aceptación (de los flujos de la ERS), RF/RNF cubiertos y pruebas. **Definición de terminado (todas las fases)**: código con revisión por PR, pruebas en verde en CI, OpenAPI actualizado, operaciones críticas con `@Auditable`, la regla ArchUnit pasa y no hay valores fijos de configuración.

### Fase 0 — Base (repositorio, Docker, seguridad, auditoría, errores, CI)
**Tareas**
1. Monorepo: `.gitignore`, `.editorconfig`, README, `.env.example`, `docker-compose.yml` (postgres:16 con healthcheck y volumen, mailpit 1025/8025), `infra/local/postgres/init/01-roles.sql`.
2. Backend: Spring Boot 3.3.x, Java 21, Maven wrapper. Dependencias: web, validation, security, data-jpa, aop, actuator, flyway-core + flyway-database-postgresql, postgresql, springdoc-openapi-starter-webmvc-ui 2.x, caffeine, shedlock-spring + shedlock-provider-jdbc-template, uuid-creator, nimbus-jose-jwt (vía spring-security-oauth2-jose), poi-ooxml, openpdf, bucket4j-core (límite de login), AWS SDK v2 s3/ses (perfil aws), spring-boot-starter-mail. Test: testcontainers-postgresql, archunit-junit5, spring-security-test.
3. `compartido` y `transversal`: excepciones → `ManejadorErrores` (ProblemDetail RFC 7807 + `codigo`, traducción de `DataIntegrityViolationException` por nombre de constraint y de `OptimisticLockingFailureException` a 409), `PaginaRespuesta`, `RelojInstitucional`, `@Auditable`, `AuditoriaContexto`, `UsuarioAutenticado`, OpenAPI con esquema bearer, Jackson (fechas con offset, BigDecimal como string), Caffeine y ShedLock.
4. Migraciones V1 a V5. `gesedu_app` sin UPDATE/DELETE en auditoría.
5. **Seguridad** (DA-04, DA-05): `AutenticacionService.login` → busca por correo o DNI; si `bloqueado_hasta > now` → 401 `CUENTA_BLOQUEADA` (sin revelar si la contraseña era correcta); si falla → `intentos_fallidos+1` y al llegar a 5 asigna `bloqueado_hasta = now + SECURITY_BLOQUEO_MINUTOS` y audita "Bloqueó la cuenta…" (UC-24 FA-3). Esto **se confirma (commit)** antes de responder 401 (transacción propia, `noRollbackFor`). Si el login es correcto, reinicia el contador, actualiza `ultimo_acceso` y emite el JWT de 15 min (claims: sub, rolId, perfil, permisos) y el refresh rotatorio (hash SHA-256 en BD, cookie HttpOnly). `JwtFiltro` (OncePerRequestFilter) valida firma y expiración y consulta `EstadoCuentaCache` (Caffeine, TTL 30 s, se invalida al desactivar o bloquear) y `PermisosPorRolCache` (se invalida en PUT /roles/{id}/permisos). Al desactivar una cuenta se revocan sus refresh tokens. `@EnableMethodSecurity` + `@PreAuthorize("hasAuthority('X')")` en los servicios de aplicación. BCrypt con costo `BCRYPT_COST` (12). Límite de peticiones en /auth/login.
6. **Auditoría** (DA-06): `AuditableAspect` (@Around, `@Order` dentro del advisor transaccional: `@EnableTransactionManagement(order = Ordered.HIGHEST_PRECEDENCE)` y aspecto de menor precedencia) escribe `RegistroAuditoria` + `CambioAuditoria` del `AuditoriaContexto` mediante `AuditoriaService.registrar` (`Propagation.MANDATORY`). Si la operación falla no hay registro y si el registro falla se revierte la operación. Para los jobs se usa el actor "Sistema" (usuario_id NULL).
7. Frontend: Vite + React 18 + TS estricto, ESLint y Prettier, React Router 6, TanStack Query 5, react-hook-form + zod, Vitest + Testing Library. `http.ts` (Bearer, un solo refresh concurrente ante 401, `ProblemError`), `AuthProvider` (access token **en memoria**, refresh silencioso al cargar), `RutaProtegida`, `RequierePermiso`, `IntranetLayout`, tokens CSS, generación de tipos `npm run api:types`.
8. CI: workflows de backend (`./mvnw -B verify`) y frontend (`npm ci && npm run lint && npm run typecheck && npm test && npm run build`), con caché y path filters. El trabajo de backend exporta `openapi.json` como artefacto.
9. Prueba de arquitectura ArchUnit (§2) y `ConfiguracionAsistenciaProperties` validada (`@Validated`, `@PositiveOrZero`, `@NotNull`, más una validación cruzada `studentLateCutoffTime < schoolDayClosingTime` y `ZoneId` válido). Un valor inválido **impide el arranque** con un mensaje de diagnóstico (DA-10).

**Criterios de aceptación**: `docker compose up -d` + `./mvnw spring-boot:run` arrancan y aplican V1 a V5; `/actuator/health` = UP; con credenciales malas 5 veces la cuenta queda bloqueada y el 6.º intento, aun correcto, recibe 401 `CUENTA_BLOQUEADA` (RNF-06); un endpoint protegido sin permiso devuelve 403 problem+json; `UPDATE auditoria.registro_auditoria` como `gesedu_app` falla; si `GESEDU_ASISTENCIA_SCHOOL_DAY_CLOSING_TIME` es inválido la app no arranca.
**RF/RNF**: RF-47, RF-48, RF-49, RNF-01, RNF-06, RNF-07, RNF-08, DA-02 a DA-06, DA-08, DA-10.
**Pruebas**: `BloqueoCuentaIT`, `RefreshTokenIT` (rotación, revocación), `JwtFiltroTest`, `AuditoriaInmutableIT`, `AuditoriaTransaccionalIT` (rollback conjunto), `ManejadorErroresTest`, `ConfiguracionAsistenciaPropertiesTest`, `ReglasModulosTest` (ArchUnit), y en el frontend `http.test.ts` (refresh único).

### Fase 1 — Demo en localhost (UC-24 login y roles, UC-21, UC-02, UC-25 + semillas)
**Tareas**
1. **Semillas** (`DemoDataSeeder`, perfil `demo`, idempotente): usuarios Administradora (Carmen Salas Paredes), Directivo (Ricardo Paredes Ortiz, cargo "Coordinación académica"), Docente tutora (Rosa Quispe Mendoza, 3° A), Alumna (Lucía García Rojas) y Apoderada (María Rojas Salazar), más ~10 docentes de los WF. Año 2026 CERRADO mínimo (para recurrentes y para una deuda vencida, FA-3). **Año 2027 PLANIFICADO** con 4 bimestres; grados 1° a 5° con secciones A y B (capacidad 30; 3° A con 4 vacantes, 3° B con 0 y 2° A con **1 vacante** para la demo de concurrencia); 6 cursos y sus competencias (WF p21: Mat 5 h, Com 5 h, CyT 4 h, CCSS 3 h, Ing 3 h, EF 2 h = 22 h); aulas (Aula 101 a 205, Lab. 1, Losa deportiva); bloques L–V 7:45–14:00 (8 por día, recreo 10:00–10:15); vinculaciones, asociaciones docente-curso, disponibilidad confirmada y carga horaria 2027; política de cobranza 2027 (S/ 350.00, 5 %, cronograma de 11 conceptos de WF p6); 8 solicitudes pendientes (MAT-2027-00479…00486) con documentos de ejemplo.
2. Backend `academico` (lectura de estructura y vacantes), `horarios` (borrador, perspectivas, opciones, validación, CRUD de asignaciones con If-Match, completitud de solo lectura), `ValidadorConflictos` (dominio puro), `matricula` (bandeja, detalle con validaciones, evaluación, rechazo, confirmación) y `pagos.aplicacion.GeneradorObligaciones` (crea las obligaciones del cronograma dentro de la transacción de la matrícula). Notificación por correo a Mailpit (UC-02 paso 13, `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`). `auditoria` (consulta paginada, detalle y exportación xlsx). `usuarios` (lista y desbloquear, para mostrar el bloqueo).
3. Operaciones auditadas en la demo: login bloqueado, desbloqueo, crear/editar/eliminar asignación, confirmar y rechazar matrícula, evaluación de solicitud.
4. Frontend: `/login`, `/intranet/inicio` (por rol), menú por permisos, `/intranet/horarios/2027/elaborar` (MatrizHorario + panel de asignación con validación en tiempo real con debounce de 300 ms), `/intranet/matricula/solicitudes[/:codigo]`, `/intranet/auditoria` y `/intranet/usuarios` (lista + desbloquear).

**Criterios de aceptación (ERS)**
- *UC-24*: login correcto con los 5 usuarios semilla y menú distinto por rol (paso 6, RF-49). FA-3: 5 fallos → bloqueado (mensaje de WF p1) y el admin desbloquea. Acceder por URL a una ruta de otro rol → página 403 y la API devuelve 403.
- *UC-21*: FB 1–10 (perspectiva, borrador único, asignación con opciones restringidas a docentes asociados al curso y disponibles, RF-46). FE-1 docente no disponible → bloqueo y causa. FE-2 cruce de docente, aula o sección → mensaje con el recurso y la asignación existente (WF p29: "Prof. R. Quispe ya tiene clase en 4° B (Aula 305) el martes de 9:15 a 10:00"). FA-1 modificar (se excluye a sí misma en la búsqueda de cruces), FA-2 eliminar, FA-3 cambiar de perspectiva sin otro borrador. Dos pestañas editando → la segunda recibe 409 y recarga (Arq §4.7).
- *UC-02*: FB 1–13. FA-1 no admitido, FA-2 duplicidad, FA-3 deudas (alumno recurrente de 2026 con deuda VENCIDA), FA-4 sin vacante (3° B), FA-5 desistimiento. Al confirmar: vacantes 4→3, matrícula CONFIRMADA, 11 obligaciones PENDIENTE y correo en Mailpit. Dos confirmaciones simultáneas en 2° A con 1 vacante → exactamente 1 éxito y 1 `SIN_VACANTE` (RNF-09). Si falla la generación de obligaciones → nada queda persistido.
- *UC-25*: las operaciones anteriores aparecen con usuario, rol, módulo, acción, detalle y fecha (RF-50, RNF-08). Filtros por usuario, módulo, acción y rango; exportación a Excel con los resultados filtrados; la UI indica "Los registros no se pueden editar ni eliminar".

**RF/RNF**: RF-03, RF-04, RF-05, RF-07 (generación), RF-38, RF-39, RF-40, RF-41, RF-46, RF-47 a RF-50, RNF-01, RNF-03, RNF-06, RNF-07, RNF-08, RNF-09, RNF-11.
**Pruebas**: unitarias `ValidadorConflictosTest` (los 6 tipos, exclusión en edición, solapamiento parcial de horas), `GeneradorObligacionesTest` (11 conceptos, montos exactos), `EvaluacionSolicitudTest`. Integración `VacantesConcurrentesIT` (20 hilos, 1 vacante → 1 éxito, vacantes=0, el CHECK impide negativos), `MatriculaTransaccionalIT` (falla en obligaciones → rollback de matrícula y vacante), `MatriculaUnicaIT` (índice parcial), `AsignacionHorarioIndicesIT` (los 3 UNIQUE ante una carrera), `BorradorVersionIT` (409), `AuditoriaConsultaIT` + exportación. API: `HorarioControllerIT`, `MatriculaControllerIT` y `AuditoriaControllerIT` (401/403/422/409 con forma problem+json). Frontend: `MatrizHorario.test.tsx`, `PanelAsignacion.test.tsx` (muestra conflictos), `RutaProtegida.test.tsx`. E2E opcional con Playwright siguiendo el guion de §9.

### Fase 2 — Seguridad completa y configuración académica (UC-24, UC-13, UC-14 + configuración S-14)
**Tareas**: CRUD de usuarios (alta con rol, validación de duplicado por DNI y correo, vínculo con alumno o apoderado, desactivar/activar, edición), gestión de permisos por rol con invalidación de caché, UC-13 (validador de año de 10 a 12 meses y 4 bimestres sin cruce), UC-14 (curso, competencias y copia desde otro año), pantallas de configuración: secciones (capacidad, tutor), aulas, bloques, período de disponibilidad, vinculaciones y asociaciones, carga horaria y política de cobranza.
**Criterios**: UC-24 FB 1–6, FA-1 cuenta duplicada (WF p34 "Ya existe una cuenta registrada para el DNI…"), FA-2 sin vínculo académico → la cuenta no se habilita; la desactivación corta el acceso **de inmediato** (siguiente petición → 401, refresh revocado). UC-13 FB 1–8, FA-1 rango de año inválido, FA-2 bimestre inválido o cruzado (WF p20). UC-14 FB 1–14 y FA-1 copia.
**RF/RNF**: RF-01, RF-02, RF-03, RF-04, RF-06, RF-22, RF-23, RF-24, RF-42 (carga), RF-45 (bloques y período), RF-46 (asociaciones), RF-47, RF-49, RNF-07.
**Pruebas**: `AnioEscolarTest` (10–12 meses, bordes), `BimestresSolapadosIT` (EXCLUDE), `CopiaCursosIT`, `UsuarioDuplicadoIT`, `DesactivacionInmediataIT`, `PermisosPorRolIT` (matriz completa), `CachePermisosIT` (invalidación < 1 s, RNF-07).

### Fase 3 — Matrícula y pagos (UC-01, UC-03, UC-04, UC-05, UC-06 + jobs)
**Tareas**: Gestor de Archivos (disco en local + S3 prefirmado en aws, validación de tipo y tamaño: PDF/JPG/PNG ≤ 5 MB [S-15]), UC-01 con código de seguimiento y correo, UC-03, UC-04, UC-05 (`SELECT … FOR UPDATE` de la obligación, verificación de monto = base + penalidad, número de operación único, boleta con secuencia, todo en una transacción), UC-06, `JobVencimientos` (diario 00:05 en Lima, ShedLock: PENDIENTE con vencimiento < hoy → VENCIDA y `monto_penalidad = base × % HALF_UP` una sola vez con `penalidad_aplicada_en`; idempotente).
**Criterios**: UC-01 FB 1–7, FA-1 incompleto, FA-2 DNI inválido o con trámite vigente (WF p4). UC-03 FB 1–6, FA-1 sin obligaciones, FA-2 matrícula no vigente; muestra el importe actualizado con penalidad (WF p7: S/ 350.00 + S/ 17.50 = S/ 367.50). UC-04 FB 1–6, FA-1 incompleto, FA-2 obligación ya pagada. UC-05 FB 1–9, FA-1 no corresponde → rechazo, FA-2 importe insuficiente → observado con el total pendiente; un comprobante observado o rechazado no cambia la obligación. UC-06 FB 1–6, FA-1 y FA-2, sin modificaciones.
**RF/RNF**: RF-02, RF-06 a RF-11, RNF-10, RNF-11, RNF-12 (carga directa a almacenamiento), DA-07, DA-08.
**Pruebas**: unitarias `CalculadoraPenalidadTest` (5 % de 350.00 = 17.50; casos .005 con HALF_UP), `ValidacionPagoTest`. Integración `PagoTransaccionalIT` (se fuerza una excepción después de crear la boleta → obligación sigue PENDIENTE/VENCIDA, sin registro_pago ni auditoría), `PagoConcurrenteIT` (dos validaciones de la misma obligación → 1 éxito), `NumeroOperacionUnicoIT`, `JobVencimientosIT` (idempotencia y ShedLock con 2 ejecuciones), `SolicitudDuplicadaIT`, `GestorArchivosDiscoIT` (token expira).

### Fase 4 — Horarios completos (UC-20, UC-22, UC-23)
**Tareas**: UC-20 (matriz, aviso de insuficiencia frente a la carga de los cursos asociados, cierre por fecha del período), consolidado de disponibilidad y docentes sin registro, UC-22 (validador de completitud y publicación con una nueva versión inmutable y la anterior que deja de estar vigente; se crean o actualizan las `asignacion_docente` C-21), UC-23 (alcance por rol, perspectivas permitidas, detalle, estado "no publicado"), caché Caffeine del horario vigente (se invalida al publicar, RNF-03).
**Criterios**: UC-20 FB 1–8, FA-1 actualización (queda solo la última versión), FA-2 aviso de insuficiencia (WF p28 "Faltan al menos 4 bloques"), FE-1 período cerrado. UC-22 FB 1–6, FA-1 incompleto → no se puede publicar (WF p30), FE-1 cancelación; el borrador nunca es visible para quien no tiene HORARIO_GESTIONAR (RF-43). UC-23 FB 1–6, FA-1 no publicado (WF p33), FA-2 cambio de perspectiva. Alumno = su sección; apoderado = sección del hijo; docente = sus asignaciones; admin = todo.
**RF/RNF**: RF-38, RF-42, RF-43, RF-44, RF-45, RF-46, RNF-03.
**Pruebas**: `ValidadorCompletitudTest`, `PublicacionHorarioIT` (versión vigente única, conservación de anteriores), `AlcanceHorarioIT` (por rol), `DisponibilidadPeriodoIT`.

### Fase 5 — Asistencia (UC-07 a UC-12)
**Tareas**: `CalendarioAcademico` (día lectivo, S-16), `ClasificadorAsistenciaDocente` (dominio puro), UC-07 usando `horarios.aplicacion.ConsultaHorarioService.primerBloqueDelDia(docente, fecha)`, UC-08 (lote atómico), UC-09, UC-10/UC-11 (cálculo bajo demanda + Excel/PDF), `JobCierreDiario` (cron a `SchoolDayClosingTime` en `TimeZone`, ShedLock, idempotente, actor Sistema), pantalla de asistencia docente del día y resumen mensual calculado.
**Criterios**: UC-07 FB 1–8 (≤ inicio + tolerancia → PUNTUAL; después y antes del cierre → TARDANZA; hora del servidor), FA-1 ya existe (no sobrescribe, unicidad ante peticiones simultáneas), FA-2 sin clases u horario, FA-3 día no lectivo o jornada cerrada (≥ cierre). UC-08 FB 1–8 (solo PUNTUAL/FALTA, orden alfabético, guardado atómico de los pendientes), FA-1 incompleto, FA-2 existentes, FA-3 sin tutoría o alumnos, FA-4 no lectivo o cerrado, FA-5 concurrencia. UC-09 FB 1–5, FA-1 plazo (> StudentLateCutoffTime) o jornada cerrada, FA-2 no elegible, FA-3 fuera de la sección. UC-10 FB 1–7, FA-1 parcial (el día actual solo cuenta tras el cierre), FA-2 sin registros → no se genera archivo, FA-3 alcance no autorizado o futuro, FA-4 falta calendario u horario. UC-11: % = 100 × (P+T)/(P+T+F), redondeado a 2 decimales; los no registrados se muestran aparte y quedan fuera del denominador; sin días → "No disponible". UC-12 FB 1–6, FA-1 no lectivo, FA-2 existentes intactos, FA-3 reejecución sin duplicar, FA-4 sin horario → registra el fallo y no inventa faltas.
**RF/RNF**: RF-12 a RF-20, RNF-03, DA-08, DA-10.
**Pruebas**: `ClasificadorAsistenciaDocenteTest` (límite inclusivo, igual al cierre, antes del inicio), `PorcentajeAsistenciaTest` (división entre cero, no registrados), `CalendarioAcademicoTest`, `JobCierreDiarioIT` (idempotencia, ShedLock, día no lectivo), `AsistenciaUnicaConcurrenteIT`, `ActualizacionTardanzaIT` (reloj fijo con `Clock` inyectado), `ReporteAsistenciaIT` (no persiste nada).

### Fase 6 — Evaluación (UC-15 a UC-19)
**Tareas**: `EstrategiaConsolidacion` + 3 implementaciones + `ConsolidacionService` con mapa por `TipoRegla` (DA-09). `NivelLogro.desdePromedio` (HALF_UP). UC-15 (grilla, validación de escala, aviso de incompleto, cambio de nota auditado con valor anterior y nuevo), UC-16 (con PROMEDIO_PONDERADO por defecto), UC-17, UC-18 (avance, bloqueo si está incompleto, cálculo por lotes idempotente, en el último bimestre se calcula el FINAL, libretas), UC-19 (alcance, nivel esperado, PDF de informe y libreta con OpenPDF).
**Criterios**: UC-15 FB 1–7, FA-1 nivel inválido, FA-2 incompleto (WF p22 "Hay 2 alumnos con competencias sin calificar"). UC-16 FB 1–6, FA-1 cambio, FA-2 por defecto promedio ponderado. UC-17 FB 1–5. UC-18 FB 1–4, FA-1 consolidado final, FA-2 incompleto con lista de docentes (WF p25). UC-19 FB 1–7, FA-1 apoderado con varios hijos, FA-2 libreta final.
**RF/RNF**: RF-21, RF-25 a RF-37, RF-48 (cambios de nota), RNF-03.
**Pruebas**: `PromedioPonderadoStrategyTest` (AD,A → 3.5 → AD; B,A → 2.5 → A; pesos distintos; entrada vacía), `ModaStrategyTest` (empate → el más reciente), `UltimoRegistroStrategyTest`, `NivelLogroTest`, `ConsolidacionIdempotenteIT`, `ConsolidacionIncompletaIT`, `LibretaPdfIT`, `AlcanceNotasIT`.

### Fase 7 — Endurecimiento y cierre
k6 con 200 apoderados concurrentes en UC-01/UC-03 (RNF-12), medición de p95 < 2 s (RNF-03) y nuevos índices (V12+), matriz de navegadores (las 2 últimas de Chrome, Firefox, Safari y Edge; RNF-04), revisión de accesibilidad y responsive (RNF-02), perfil `aws` verificado contra LocalStack (S3/SES) [S-17], manual de usuario por perfil y guía de configuración inicial (Visión §13), ayudas contextuales en marcación y publicación (Visión §13.2), cierre de las preguntas abiertas pendientes.

**Paralelismo**: las fases 3, 4, 5 y 6 corren **en paralelo** después de la fase 2. Dependencias que se rompen con semillas o interfaces: la fase 5 usa un horario publicado semilla hasta que la fase 4 termine `ConsultaHorarioService`; la fase 6 usa `asignacion_docente` semilla hasta que la fase 4 la derive al publicar.

---

## 7. Reparto para 10 personas (P1–P10)

| Equipo | Personas | Fase 0–1 | Fases 2–7 | Fronteras (contratos) |
|---|---|---|---|---|
| **Plataforma backend** | P1 (líder técnico) | estructura, seguridad JWT/RBAC, errores, ArchUnit, CI | soporte transversal, revisión de PR, Fase 7 rendimiento | `compartido`, `transversal`, `seguridad.aplicacion` |
| **Plataforma frontend** | P2 | Vite, layout, auth, http, componentes base, MatrizHorario base | componentes compartidos, responsive, accesibilidad, E2E | `components/`, `api/http.ts` |
| **Seguridad y auditoría** | P3 | aspecto `@Auditable`, auditoría (consulta y export), usuarios (lista y desbloqueo), semillas de usuarios | Fase 2 usuarios y roles, QA transversal de auditoría | `auditoria.aplicacion` |
| **Académico** | P4 | semillas académicas (año 2027, grados, secciones, cursos) | Fase 2 UC-13, UC-14 y configuración; luego apoyo a evaluación | `academico.aplicacion.ConsultaAcademicaService` |
| **Horarios** | P5, P6 | P5: backend de UC-21 (ValidadorConflictos, índices, versión). P6: pantalla Elaborar + semillas de bloques, aulas y disponibilidad | Fase 4 UC-20, UC-22, UC-23 | `horarios.aplicacion.ConsultaHorarioService` (lo usan asistencia y evaluación) |
| **Matrícula y pagos** | P7, P8 | P7: backend de UC-02 + vacantes + concurrencia. P8: bandeja y evaluación (frontend) + generación de obligaciones | Fase 3 (P7 UC-05 y job; P8 UC-01, UC-03, UC-04, UC-06) | `pagos.aplicacion.GeneradorObligaciones`, `ConsultaDeudaService` |
| **Asistencia** | P9 | demo: apoyo de QA y guion de demo | Fase 5 completa | consume `ConsultaHorarioService` |
| **Evaluación** | P10 | demo: inicio por rol (frontend) | Fase 6 completa | consume académico y horarios |

Reglas: una rama por historia (`feat/<modulo>-<uc>`), PR con al menos 1 revisión (de otro equipo cuando toque `compartido`), las migraciones de cada módulo solo las edita su equipo y los números de versión se reservan en un canal (o se usa `V<fase><nn>`). Los contratos entre módulos (`*.aplicacion` + OpenAPI) se acuerdan **antes** de implementar.

---

## 8. Estrategia de pruebas

| Nivel | Herramienta | Qué cubre | Ubicación |
|---|---|---|---|
| Unitarias de dominio | JUnit 5 + AssertJ, sin Spring | estrategias de consolidación y `NivelLogro` (HALF_UP, empate en la moda, pesos), `ValidadorConflictos` (6 tipos, exclusión propia), `ClasificadorAsistenciaDocente` y porcentaje, `CalculadoraPenalidad` y validación de monto, validación de año y bimestres, generador de cronograma, `CalendarioAcademico` | `<modulo>/dominio/*Test` |
| Integración | `@SpringBootTest` + **Testcontainers PostgreSQL 16** (contenedor compartido, Flyway real, usuario `gesedu_app` real) | **vacantes concurrentes** (CountDownLatch, N hilos), **reversión de pagos** (fallo inyectado mediante un bean de prueba), **inmutabilidad de la auditoría** (UPDATE, DELETE y TRUNCATE fallan; rollback conjunto), índices anticruce, `@Version`, unicidad de asistencia, idempotencia de jobs y ShedLock, índices parciales | `*IT` (failsafe) |
| API / endpoints | MockMvc sobre el contexto completo + JWT de prueba | status, problem+json y `codigo`, permisos por rol (403), alcance (apoderado ajeno → 403), paginación y validación de entradas | `<modulo>/api/*ControllerIT` |
| Arquitectura | ArchUnit | capas, solo `aplicacion` entre módulos, sin ciclos, `@PreAuthorize` en los métodos públicos de los servicios de aplicación | `arquitectura/` |
| Frontend | Vitest + Testing Library + MSW | MatrizHorario, panel de conflictos, guardas por permiso, formularios (zod), http refresh | `*.test.tsx` |
| E2E (Fase 1 opcional, Fase 7) | Playwright | guion de la demo | `frontend/e2e/` |
| Carga | k6 | RNF-12 (200 concurrentes), RNF-03 | `perf/` (Fase 7) |

Reloj controlable: todo el código de dominio y de aplicación recibe un `Clock` (`RelojInstitucional`). Sin `LocalDateTime.now()` directo (lo verifica una regla ArchUnit).

---

## 9. Guion de la demo en localhost

**Requisitos previos**: Docker Desktop, JDK 21, Node 20 LTS.
```bash
cp .env.example .env                      # editar DEMO_PASSWORD y JWT_SECRET (openssl rand -base64 48)
docker compose up -d                      # postgres:16 + mailpit
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local,demo   # aplica Flyway y carga las semillas
cd frontend && npm ci && npm run dev      # http://localhost:5173 (proxy /api → :8080)
# alternativa en un solo comando: docker compose --profile full up --build
```
Swagger: http://localhost:8080/swagger-ui.html · Mailpit: http://localhost:8025

**Pasos en clase (~12 min)**
1. **Arquitectura** (1 min): mostrar el árbol de paquetes, `ReglasModulosTest` en verde y Swagger con `/api/v1`.
2. **Login por rol** (2 min): entrar como alumna → menú del alumno; como docente → menú del docente; como apoderada → selector de hijos; como admin → menú completo. Escribir por URL `/intranet/auditoria` como alumna → 403. En DevTools: JWT de 15 min y cookie HttpOnly.
3. **Bloqueo** (1 min): 5 contraseñas erróneas con el usuario del docente → mensaje de bloqueo (WF p1); como admin en `/intranet/usuarios` se ve "Bloqueado · 5 intentos fallidos" → Desbloquear.
4. **UC-21** (3 min): `/intranet/horarios/2027/elaborar`, perspectiva 3° A, asignar Matemática el martes 9:15 → se ven solo los docentes de Matemática con su disponibilidad; elegir a R. Quispe (ya dicta en 4° B a esa hora) → conflicto en tiempo real con la causa; cambiar a F. Medina → guardar. Cambiar a la perspectiva por docente y por aula: es el mismo borrador. Abrir una segunda pestaña, editar en ambas → la segunda recibe 409.
5. **UC-02** (3 min): bandeja → MAT-2027-00482 → validaciones en verde, 3° A 4 vacantes, resumen de costos S/ 3,850.00 → aceptar → confirmar → 3 vacantes y 11 compromisos; el correo llega a Mailpit. Mostrar FA-3 con la solicitud de un recurrente con deuda 2026 → bloqueada. Concurrencia: ejecutar `VacantesConcurrentesIT` (o el script `scripts/demo-concurrencia.sh`, que lanza 2 peticiones paralelas sobre 2° A con 1 vacante) → 1 éxito y 1 `409 SIN_VACANTE`.
6. **UC-25** (2 min): `/intranet/auditoria` → filtrar por módulo Horarios y Matrícula → ver las acciones recién hechas y el bloqueo → Exportar Excel. En `psql` como `gesedu_app`: `DELETE FROM auditoria.registro_auditoria` → error del trigger o de permisos.

---

## 10. Preparación para AWS (sin escribir infraestructura todavía)
- **Configuración 100 % por entorno**: `application-aws.yml` solo referencia variables (`DB_URL`, `DB_APP_*`, `DB_OWNER_*`, `JWT_SECRET`, `ARCHIVOS_BUCKET`, `AWS_REGION`, `CORREO_REMITENTE`, `GESEDU_ASISTENCIA_*`, `TZ`…). En EB llegan como propiedades de entorno resueltas desde Secrets Manager y Parameter Store (Arq §10.9).
- **Adaptadores listos**: `S3GestorArchivos` (prefirmado de 5 min, SSE-S3, credenciales del rol de instancia), `SesNotificador`, seleccionados con `ARCHIVOS_PROVEEDOR` y `CORREO_PROVEEDOR`.
- **Backend sin estado**: JWT + refresh en BD, ShedLock para varias instancias, Flyway al arrancar con el usuario owner (o un paso de despliegue), `/actuator/health` para el ALB, `server.forward-headers-strategy=framework` detrás del ALB, cookie `Secure` activada y JAR ejecutable `gesedu-api.jar` (Arq §10.6).
- **Frontend**: `npm run build` → `dist/` estático con rutas relativas `/api/v1` (misma origen con CloudFront `/api/*` → ALB), SPA con fallback a `index.html` (configurado luego en CloudFront).
- **Pendiente para una fase posterior** (fuera de esta entrega): IaC (CloudFormation o Terraform), pipeline de despliegue a S3, EB e invalidación de CloudFront, y script DBA de roles en RDS.

---

## 11. Contradicciones, supuestos y preguntas

### 11.1 Contradicciones encontradas (cómo se resuelven según la prioridad)
| ID | Documentos | Contradicción | Resolución |
|---|---|---|---|
| C-01 | Visión §1, §2, §6.3, §8.7, §9 y WF p13–14 vs ERS §2.3, §3.2.2, §3.5.1 y Arq §4.8 y §10.1 | La visión y el WF validan la red o IP del colegio ("Conectado a la red del colegio"); la ERS y la Arq dicen explícitamente que no | **No se valida IP ni red**; se quita el indicador del WF |
| C-02 | Arq §10.10 vs instrucción del líder | Arq: dos repos (gesedu-frontend, gesedu-backend); instrucción: `backend/` y `frontend/` en un repo | **Monorepo** (instrucción explícita), CI con path filters. Si la Arq debe prevalecer → ver Q-10 |
| C-03 | ERS §2.1.4, §3.1.7–3.1.12, §3.4 vs Arq tabla 7 y MC p.3 | ERS: `StudentAttendance`/`TeacherAttendance`, `PUNCTUAL/LATE/ABSENT`, `checkInAt`, `TeacherRole`; Arq y MC: `AsistenciaAlumno`/`AsistenciaDocente`, `PUNTUAL/TARDANZA/FALTA`, `horaRegistro` | **Nombres en español (Arq + MC)**. Las claves de configuración se mantienen tal como las escriben la Arq y la ERS |
| C-04 | Catálogo RF-13 vs ERS UC-08 | RF-13 permite registrar al inicio Puntual, Tardanza o Falta; UC-08 solo PUNTUAL o FALTA (la tardanza se registra por UC-09) | **ERS** |
| C-05 | WF p17–18 y Procesos §3 vs Catálogo RF-15 y ERS UC-10/11 | El WF muestra "Consolidación mensual" automática con estados "Consolidado" y un % sobre los días lectivos del mes; la ERS pide calcular bajo demanda sin persistir y usar como denominador los días registrados | **ERS**: la pantalla p18 se convierte en "Resumen mensual" calculado y la fórmula es la de UC-11 |
| C-06 | WF p16 vs ERS UC-09 | El WF guarda el estado anterior, la hora del cambio y el responsable en el módulo; la ERS dice que no hay historial en el módulo | **ERS**: solo `updated_at`. El cambio queda en **auditoría** (WF p35 ya lo muestra); la columna "Última modificación" se lee de `auditoria.aplicacion` |
| C-07 | Visión RF-23 vs Catálogo y ERS UC-14 | La visión dice que el docente edita las competencias de su curso; el catálogo y la ERS, que lo hace el directivo | **Catálogo y ERS** (directivo) |
| C-08 | Matriz de trazabilidad vs ERS v3 | La matriz usa una numeración anterior (UC-26, asistencia UC-07 a UC-13…) y en la hoja RFvsRNF los textos de RF-47 y RF-48 están intercambiados | **Numeración de la ERS v3**; actualizar la matriz (tarea de documentación) |
| C-09 | ERS historial v2.0 vs ERS §3.1.11 | El historial dice "eliminación de UC-11", pero UC-11 sigue especificado | Se mantiene **UC-11** como cálculo incluido en UC-10 (25 UC) |
| C-10 | WF p1 y p34 vs MC | El login usa "DNI o correo" y la lista de usuarios muestra el DNI de admins y docentes, pero el MC solo tiene `documentoIdentidad` en Alumno y Apoderado | **S-01**: `usuario.numero_documento` |
| C-11 | MC (nombres solo en Usuario, Alumno 0..1 Usuario) vs ERS UC-01 (se registran datos del alumno y del apoderado sin cuenta) | Faltan nombres en Alumno y Apoderado | **S-02**: se agregan nombres, apellidos, fecha de nacimiento, parentesco y correo |
| C-12 | Catálogo RF-26 y RF-27 ("automáticamente al cierre") vs ERS UC-17 y UC-18 (un actor pulsa el botón) | Disparo automático o manual | **ERS**: manual (Directivo y Docente); el cálculo es automático una vez disparado |
| C-13 | MC p.4 `Curso.horasLectivas` (UC-14) vs MC p.5 `CargaHorariaCurso` por grado (RF-42) | Dos fuentes de la carga semanal | `carga_horaria_curso` es la fuente de RF-42 y se **inicializa** desde `horas_lectivas` (editable por grado) |
| C-14 | WF p20–25 ("Menú del directivo") vs ERS §2.1.1 y Arq §5 ("no constituye un rol adicional") | El menú diferenciado sugiere un rol aparte | **S-03**: perfil Administrador con `cargo`; `RolAcceso` "DIRECTIVO" como conjunto de permisos (no es un nuevo tipo de usuario). Ver Q-03 |
| C-15 | Catálogo RF-01 ("Eliminar") vs Procesos §11, ERS UC-24 y WF p34 ("desactivar") | Borrado físico o lógico | **Desactivación lógica** (preserva la auditoría y las FK) |
| C-16 | Arq tabla 8 (LocalStack en desarrollo) vs instrucción (disco o Mailpit permitidos) | Simuladores locales | Disco + Mailpit por defecto; LocalStack opcional para validar el perfil aws (S-17) |
| C-17 | WF p28–31 (fila "Recreo") vs MC `BloqueHorario` (sin tipo) | Recreo modelado o no | El recreo **no** es un bloque; la UI lo dibuja como hueco entre bloques |
| C-18 | Procesos §1 (deuda del **estudiante**) vs Catálogo RF-05 y ERS UC-02 FA-3 (deuda VENCIDA del **apoderado** en periodos anteriores) | Sujeto de la verificación de deuda | **Catálogo y ERS**: obligaciones VENCIDAS de años anteriores asociadas al apoderado |
| C-19 | Visión §8.8–8.9 (moda como regla única, evidencias pedagógicas) vs Catálogo RF-25 y ERS | La visión habla de una sola regla y de registrar evidencias | **3 reglas**; el registro de evidencias queda **fuera** (no hay UC) |
| C-20 | MC p.4 `AsignacionDocente` vs MC p.5 `AsociacionDocenteCurso`/`AsignacionHorario` | Ningún documento dice cómo se crea `AsignacionDocente` (docente dicta curso en sección) | **S-18**: se deriva del horario publicado (UPSERT, conserva la regla) |
| C-21 | WF p2 (menú del alumno con "Matrícula y pagos") vs ERS UC-03 (actor: Apoderado) | El alumno ve pagos o no | Alumno: solo la tarjeta de matrícula (solo lectura). Ver Q-07 |

### 11.2 Supuestos (propuestos, a confirmar)
- **S-01** `usuario.numero_documento` único y nullable; el login acepta DNI o correo.
- **S-02** Alumno y Apoderado guardan sus propios nombres y contacto (pueden existir sin cuenta).
- **S-03** Perfiles fijos (ADMINISTRADOR, DOCENTE, ALUMNO, APODERADO) y `RolAcceso` configurables (5 semilla, incluido DIRECTIVO). Un rol por usuario (MC: "tiene asignado 1").
- **S-04** Un solo año ACTIVO. Se permite matricular para un año PLANIFICADO o ACTIVO (campaña 2027 durante 2026).
- **S-05** `seccion.capacidad` para RF-04 ("capacidad y disponibilidad"); `vacantes_disponibles` se inicializa igual a la capacidad.
- **S-06** Los cursos aplican a todos los grados (sin tabla curso-grado) y la carga por grado está en horarios.
- **S-07** `competencia.nivel_esperado_descripcion` para RF-33 (WF p26 "Nivel esperado (A): …").
- **S-08** Código de seguimiento `MAT-<año>-<5 dígitos>` (WF p6).
- **S-09** `motivo_rechazo` detalla los estados "Rechazada por…" de la ERS; el MC solo tiene RECHAZADA.
- **S-10** `politica_cobranza` + `cronograma_cobro` (RF-06 no tiene entidad en el MC). Penalidad = % del importe base, **una sola vez** al vencer (WF p6 y p12: 5 % → S/ 17.50, "aplicada el 01/09"). Cuota de ingreso: no se modela (Q-08).
- **S-11** `medio_pago` (WF p9 "Banco o medio de pago").
- **S-12** Número de operación único entre los comprobantes PENDIENTE o APROBADO (uno rechazado puede corregirse y volver a presentarse).
- **S-13** Boleta = `RegistroPago` con número correlativo `B001-NNNNN` (WF p7). Sin pagos parciales: el monto debe ser igual a base + penalidad.
- **S-14** Pantallas de configuración sin wireframe (secciones y tutor, aulas, bloques, período de disponibilidad, vinculaciones, carga horaria, cobranza), con el mismo estilo.
- **S-15** Archivos: PDF, JPG o PNG de hasta 5 MB.
- **S-16** Día lectivo = fecha dentro de algún bimestre del año ACTIVO y con bloques configurados ese día de la semana. **Sin calendario de feriados** (Q-04).
- **S-17** LocalStack solo para la verificación del perfil aws en la Fase 7.
- **S-18** `asignacion_docente` se crea o actualiza al publicar el horario.
- **S-19** Duración del bloqueo: **15 min** (`SECURITY_BLOQUEO_MINUTOS`), con desbloqueo manual por el admin (WF p34). Criterio pendiente según la Arq §11.3.
- **S-20** Motor de evaluación (Arq §9.2): redondeo **HALF_UP** (2.5 → A), empate de la moda → **registro más reciente**, **pesos iguales** por defecto guardados por asignación y bimestre.
- **S-21** Valores de asistencia de ejemplo para local: `TeacherLateThresholdMinutes=5` (WF p13), `StudentLateCutoffTime=08:30` (WF p16), `SchoolDayClosingTime=15:30` (WF p19), `TimeZone=America/Lima`. **No son valores institucionales aprobados** (ERS §3.4).
- **S-22** Correo solo en UC-01 y UC-02 (Arq §6.2); en los demás "informa al apoderado" se refleja en el estado visible en la intranet.
- **S-23** Se audita (además de lo que pide la Arq §4.4): bloqueo y desbloqueo de cuentas, cambios de asistencia FALTA→TARDANZA, faltas generadas por el job (WF p35 los muestra), configuración académica y de cobranza.
- **S-24** Dos usuarios de BD (owner para Flyway y app para runtime) para garantizar la táctica INSERT/SELECT de la auditoría.
- **S-25** Build con **Maven**; UI sin librería de componentes (CSS propio con tokens) para seguir los wireframes con fidelidad.

### 11.3 Preguntas abiertas
- **Q-01 (importante para la Fase 6)** Granularidad de `RegistroLogro`: ¿el docente registra **una** nota por competencia y bimestre (como el WF p22) o **varias evaluaciones** por bimestre (como sugieren RF-26 y la Arq §9.2, donde la regla se aplica a los registros del bimestre)? Propuesta: varias por bimestre, con la grilla mostrando la última y un botón "Agregar evaluación"; la regla se aplica dentro del bimestre (RF-26) y entre bimestres para el final (RF-27).
- **Q-02** ¿Cómo obtiene su cuenta un apoderado **nuevo** para hacer UC-01, si UC-24 paso 4 exige una matrícula activa o una relación de apoderado? Propuesta: la secretaría registra al apoderado y la relación con el postulante y habilita la cuenta; el apoderado completa UC-01.
- **Q-03** ¿Es aceptable el `RolAcceso` DIRECTIVO (S-03) o todos los administradores deben ver las opciones académicas?
- **Q-04** ¿Hace falta un calendario de feriados o días no lectivos para la asistencia (UC-07, UC-08 y UC-12)?
- **Q-05** Valores institucionales de la asistencia, la duración del bloqueo y la política de cobranza (montos y %) reales.
- **Q-06** ¿Cómo se registra el resultado de la admisión de un nuevo ingreso (no hay un módulo de admisión)? Propuesta: el administrador lo marca en la evaluación de la solicitud.
- **Q-07** ¿El alumno ve sus obligaciones de pago (WF p2) o solo el estado de su matrícula?
- **Q-08** ¿Existe la "cuota de ingreso" de UC-02 paso 7? ¿Con qué monto y vencimiento?
- **Q-09** ¿La boleta debe descargarse en PDF o basta con el registro y su número?
- **Q-10** ¿Confirmas el monorepo (C-02) pese a que la Arq §10.10 indica dos repositorios?

---

## 12. Al aprobar el plan
1. Guardar este documento como `docs/PLAN.md`.
2. Crear `CLAUDE.md` en la raíz con: stack y versiones; estructura (§2); **reglas de dependencia entre módulos** (§2) y de capas; convenciones (UUIDv7 String, nombres en español, `NUMERIC(12,2)`/`BigDecimal` HALF_UP, `Clock` inyectado, `@Auditable` en las operaciones críticas, `@PreAuthorize` en los servicios de aplicación, RFC 7807 con `codigo`, paginación, migraciones inmutables, sin configuración fija en el código); cómo correr (§9), probar (`./mvnw verify`, `npm test`) y generar tipos (`npm run api:types`); prioridad de documentos y referencia a `docs/PLAN.md` para contradicciones y supuestos.
3. Empezar la **Fase 0**.

## Verificación de este plan
- Trazabilidad: cada UC-01…UC-25 aparece en §4 (endpoint), §5 (pantalla o job) y §6 (fase y criterios); cada RF-01…RF-50 y RNF-01…RNF-12 aparece en al menos una fase.
- La demo (§9) se valida de punta a punta al cerrar la Fase 1 con el E2E de Playwright o la ejecución manual del guion, más `./mvnw verify` y `npm test` en verde en CI.
