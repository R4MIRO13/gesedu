# GESEDU — Plan de implementación

> Versión del plan: 1.3 (22/10/2026). Convenciones operativas en `CLAUDE.md`.
> Revisión 1.1: tras las correcciones del equipo quedaron resueltas C-01, C-08, C-15, C-17, C-19 y C-23.
> Revisión 1.3: la demo es el **23/10/2026** y solo pide la arquitectura funcionando en localhost; se agrega la sección **7.0 «Demo 23/10»** con un alcance reducido. Las semanas S1–S8 empiezan después de la demo. Se corrigió la referencia de UC-18 (C-26).
> Revisión 1.2: se releyeron la ERS y la matriz y quedan resueltas C-29, C-30 y C-31 (sin contradicciones abiertas entre la ERS, el catálogo, la arquitectura y la matriz; solo permanece el wireframe p. 15, que no se actualizará). Cambios: corte mínimo de demo en la fase 1, regla de migraciones editables hasta el primer despliegue compartido (S-26) y apoyo de I2 a Asistencia desde la fase 2.
> Fuentes leídas por completo: `arquitectura.pdf` (v1.0), `ers.pdf` (v3.0), `catalogo_requisitos.pdf` (v4.0), `modelo_conceptual.pdf` (v1; 4 diagramas extraídos como imagen), `wireframes.pdf` (35 pantallas), `vision.pdf` (v3.0), `procesos.pdf` (v2), `matriz_trazabilidad.xlsx` (RF×UC y RF×RNF).
> Toda cita RF/RNF usa la numeración del Catálogo v4. Las contradicciones se listan en § 13 (C-xx) y los supuestos en § 14 (S-xx); en el texto se referencian por su ID.

## Contexto

El equipo (10 estudiantes, PUCP 2026-2) debe construir GESEDU, el sistema web de gestión integral de un colegio de secundaria. La documentación de análisis y arquitectura ya está cerrada, pero todavía no hay código. Este plan convierte esos documentos en una ruta de implementación ejecutable. El primer hito es una demo en localhost durante la clase que muestre la arquitectura funcionando de punta a punta. Después vienen los 25 casos de uso, organizados por fases, para que 10 personas avancen en paralelo con pocas dependencias entre sí.

Decisiones tomadas con el usuario durante la planificación:
- **Monorepo** `gesedu` con `backend/` y `frontend/` y un CI con dos jobs. La § 10.10 de la arquitectura ya describe el monorepo (C-01, resuelta).
- **El inicio de sesión es solo con correo** (RF-52). La etiqueta del wireframe «Usuario (DNI o correo)» pasa a «Correo» (C-02).

---

## 1. Resumen de entendimiento

### 1.1 Qué es GESEDU
Una SPA (React 19) y una API REST (Spring Boot 4.1 sobre Java 25), organizadas como monolito modular sobre PostgreSQL 18 con un esquema por módulo. Atiende a un solo colegio que inicia operaciones. Se desplegará después en AWS: CloudFront + S3 para la SPA, Elastic Beanstalk para la API, RDS para la base de datos, S3 para documentos y SES para correo (DA-01 a DA-11).

### 1.2 Actores
| Actor | En el sistema | Qué hace |
|---|---|---|
| Administrador | Rol ADMINISTRADOR | Usuarios, roles y permisos; matrícula; validación de pagos; horarios; reportes; auditoría |
| Administrador (Directivo) | El **mismo rol** ADMINISTRADOR, con `cargo` de dirección o coordinación (ERS 2.1.1, arquitectura § 5) | Año escolar y bimestres (UC-13), cursos y competencias (UC-14), consolidación bimestral (UC-18) |
| Docente (y Docente Tutor) | Rol DOCENTE; la tutoría es una asignación a una sección, no un rol | Marca su asistencia; si es tutor, registra la asistencia de su sección; registra logros; define la regla de consolidación; registra su disponibilidad |
| Alumno | Rol ALUMNO | Consulta su horario, sus notas, su libreta y su asistencia |
| Apoderado | Rol APODERADO | Solicita la matrícula, consulta obligaciones, presenta comprobantes, consulta notas, libreta y horario de sus hijos |
| Tiempo | Tarea programada (ShedLock) | Cierre diario de asistencia (UC-12); vencimientos y penalidades (RF-09, RF-10) |

### 1.3 Módulos (paquete Java y esquema de BD) y casos de uso
| Módulo (paquete / esquema) | Casos de uso | RF | RNF |
|---|---|---|---|
| `seguridad` | UC-24 + inicio de sesión y recuperación de contraseña (RF-52) | RF-01, RF-48, RF-50, RF-52 | RNF-01, RNF-06, RNF-07 |
| `matricula` | UC-01, UC-02 | RF-02, RF-04, RF-05, RF-12 | RNF-09, RNF-12 |
| `pagos` | UC-03, UC-04, UC-05, UC-06 | RF-06 a RF-11 | RNF-10, RNF-11 |
| `asistencia` | UC-07 a UC-12 | RF-13 a RF-21 | RNF-03 |
| `academico` | UC-13, UC-14 | RF-03, RF-23 a RF-25 | RNF-03 |
| `evaluacion` | UC-15 a UC-19 | RF-22, RF-26 a RF-38 | RNF-03 |
| `horarios` | UC-20 a UC-23 | RF-39 a RF-47 | RNF-03 |
| `auditoria` | UC-25 | RF-49, RF-51 | RNF-08 |
| `compartido` (sin esquema) | — | Núcleo compartido: errores, dinero, identificadores, reloj, anotación `@Auditable` | — |
| `transversal` (esquema `transversal`, solo para ShedLock) | — | Configuración, archivos, notificaciones, reportes, planificador | RNF-05 |

### 1.4 Decisiones de arquitectura en mis palabras
- **DA-01** SPA en React 19 + TypeScript estricto + Vite; TanStack Router (rutas tipadas por archivo) y TanStack Query (caché de la API). La SPA no tiene reglas de negocio, solo valida formatos.
- **DA-02** API REST en Java 25 y Spring Boot 4.1, como un único JAR. Los módulos se comunican solo a través de sus servicios de la capa `aplicacion`; las operaciones que cruzan módulos (matrícula → obligaciones) corren en una sola transacción ACID local.
- **DA-03** PostgreSQL 18 con un esquema por módulo; usamos `CHECK`, índices únicos parciales, `NUMERIC(12,2)` y `SELECT … FOR UPDATE`.
- **DA-04** JWT de acceso de 15 min más refresh token rotativo en cookie HttpOnly/Secure/SameSite=Strict, guardado como hash; BCrypt con costo 12.
- **DA-05** RBAC con permisos `MODULO_ACCION`, verificados con `@PreAuthorize` y una caché Caffeine que se invalida al cambiar roles (RNF-07).
- **DA-06** Aspecto `@Auditable` síncrono que escribe en la misma transacción; tablas de auditoría de solo inserción (trigger + GRANT).
- **DA-07** Documentos en S3 privado con URL prefirmadas de 5 min; los archivos no pasan por la API.
- **DA-08** Spring Scheduling + ShedLock 7, con jobs idempotentes.
- **DA-09** Motor de evaluación con Strategy (`PromedioPonderado`, `Moda`, `UltimoRegistro`).
- **DA-10** Parámetros de asistencia en un `@ConfigurationProperties` validado: si son inválidos, la aplicación no arranca.
- **DA-11** AWS en alta disponibilidad (2 a 4 instancias en 2 zonas, RDS Multi-AZ). En esta etapa solo se deja todo preparado (§ 12).

---

## 2. Estructura del repositorio

```
gesedu/
├─ .editorconfig  .gitattributes  .gitignore  .env.example  README.md  CLAUDE.md
├─ docker-compose.yml                 # postgres:18, mailpit; localstack opcional (perfil compose "aws-sim")
├─ .run/                              # configuraciones compartidas de IntelliJ (§ 2.4)
├─ .github/workflows/ci.yml           # jobs backend y frontend (§ 10)
├─ infra/
│  └─ postgres/init/01-roles-y-bd.sql # crea la BD gesedu y los roles gesedu_migrador y gesedu_app (solo local y Testcontainers)
├─ scripts/
│  ├─ reiniciar-bd.sh / reiniciar-bd.ps1     # docker compose down -v postgres && up
│  └─ generar-tipos-api.sh / .ps1            # openapi-typescript contra http://localhost:8080/v3/api-docs
├─ docs/
│  ├─ arquitectura.pdf  ers.pdf  catalogo_requisitos.pdf  modelo_conceptual.pdf  wireframes.pdf
│  ├─ vision.pdf  procesos.pdf  matriz_trazabilidad.xlsx
│  ├─ PLAN.md
│  ├─ api/errores.md                  # catálogo de códigos de negocio (§ 5.2)
│  ├─ demo/guion.md                   # § 11
│  └─ despliegue/{aws.md, s3-cors.json, variables-entorno.md}
├─ backend/
│  ├─ mvnw  mvnw.cmd  .mvn/wrapper/  pom.xml
│  └─ src/
│     ├─ main/java/pe/edu/gesedu/
│     │  ├─ GeseduApplication.java
│     │  ├─ compartido/               # núcleo compartido, sin dependencias hacia los módulos
│     │  │  ├─ dominio/ (EntidadBase con id UUIDv7, Dinero, NivelLogro, RangoFechas)
│     │  │  ├─ error/ (ExcepcionNegocio, CodigoError, ManejadorProblemas)
│     │  │  ├─ auditoria/ (@Auditable, ContextoAuditoria)   # anotación compartida; el aspecto vive en el módulo auditoria
│     │  │  ├─ seguridad/ (UsuarioActual: interfaz)
│     │  │  └─ web/ (PaginaDto, ETags, ParametrosPaginacion)
│     │  ├─ transversal/
│     │  │  ├─ config/ (OpenApiConfig, RelojConfig, JacksonConfig, CacheConfig)
│     │  │  ├─ planificador/ (SchedulingConfig + ShedLock)
│     │  │  ├─ archivos/ (GestorArchivos + GestorArchivosS3 + GestorArchivosDisco)
│     │  │  ├─ notificaciones/ (Notificador + NotificadorSmtp + NotificadorSes + plantillas)
│     │  │  └─ reportes/ (GeneradorExcel con Apache POI, GeneradorPdf con OpenPDF)
│     │  ├─ seguridad/ {api, aplicacion, dominio, infraestructura}
│     │  ├─ matricula/ {api, aplicacion, dominio, infraestructura}
│     │  ├─ pagos/      …   ├─ asistencia/ …   ├─ academico/ …
│     │  ├─ evaluacion/ …   ├─ horarios/   …   └─ auditoria/ …
│     ├─ main/resources/
│     │  ├─ application.yml  application-local.yml  application-demo.yml  application-aws.yml
│     │  ├─ db/migration/V1__…sql … (§ 4.4)
│     │  ├─ db/demo/V1000__datos_demo.sql        # solo con el perfil demo (spring.flyway.locations)
│     │  └─ plantillas/correo/*.html
│     └─ test/java/pe/edu/gesedu/ {arquitectura/, <modulo>/…, soporte/ContenedorPostgres.java}
└─ frontend/
   ├─ package.json  package-lock.json  vite.config.ts  tsconfig*.json  eslint.config.js  .prettierrc  index.html
   └─ src/
      ├─ main.tsx  routeTree.gen.ts (generado por el plugin)
      ├─ routes/                      # rutas por archivo (§ 6)
      │  ├─ __root.tsx  login.tsx  recuperar-contrasena.tsx  restablecer-contrasena.tsx
      │  └─ intranet/ route.tsx (layout + guardia de sesión), inicio.tsx, matricula/…, pagos/…, asistencia/…,
      │     academico/…, notas/…, cursos/…, disponibilidad/…, horarios/…, horario.tsx, usuarios/…, auditoria.tsx
      ├─ modulos/<modulo>/ {api.ts (hooks de TanStack Query), esquemas.ts (zod), componentes/}
      ├─ compartido/
      │  ├─ api/ cliente.ts (fetch + Bearer + refresh + problem+json), tipos.gen.ts (openapi-typescript), errores.ts
      │  ├─ auth/ (SesionProvider, usePermiso, guardias)
      │  ├─ componentes/ (Layout, MenuLateral, BarraInferiorMovil, MatrizHorario, TablaPaginada, Campo,
      │  │                 SelectorArchivo, Alerta, Insignia, EscalaLogro, Monto, Dialogo)
      │  └─ estilos/ (tokens.css, responsive.css)  # CSS Grid/Flexbox con puntos de quiebre (RNF-02)
      └─ test/ (setup.ts, msw/handlers.ts)
```

### 2.1 Capas y reglas de dependencia (verificadas con ArchUnit)
- Cada módulo tiene cuatro capas: `api` (controladores y DTO HTTP), `aplicacion` (servicios `@Transactional`, DTO públicos tipo `record` y puertos SPI), `dominio` (entidades JPA, enums y reglas puras) e `infraestructura` (repositorios Spring Data y adaptadores).
- Dentro de un módulo: `api → aplicacion → {dominio, infraestructura}` e `infraestructura → dominio`. `dominio` no depende de `api`, `aplicacion` ni `infraestructura`. `api` no usa `infraestructura`.
- **Entre módulos solo se usa la capa `aplicacion` del otro módulo.** Las entidades nunca cruzan módulos: se intercambian `record`. En JPA las relaciones entre esquemas son ids (`String`), no asociaciones; en la BD sí hay FK entre esquemas, para conservar la integridad referencial.
- Grafo acíclico de módulos (ArchUnit `slices().should().beFreeOfCycles()`):
  `seguridad` ← `academico` ← `pagos` ← `matricula`; `evaluacion` → {`academico`, `matricula`, `seguridad`}; `horarios` → {`academico`, `seguridad`, `evaluacion`}; `asistencia` → {`horarios`, `academico`, `matricula`, `seguridad`}; `auditoria` → `seguridad`. Todos pueden usar `compartido` y `transversal`.
- Dependencias inversas mediante puertos SPI. `seguridad.aplicacion.VerificadorVinculoAcademico` (UC-24, paso 4) lo implementa `matricula.infraestructura`. La anotación `@Auditable` está en `compartido`, para que los módulos no dependan de `auditoria`.

### 2.2 Configuración por perfil
| Archivo | Contenido |
|---|---|
| `application.yml` | Valores comunes. **Ningún secreto.** `spring.threads.virtual.enabled=true`, `server.forward-headers-strategy=framework`, `spring.jpa.open-in-view=false`, `spring.jpa.hibernate.ddl-auto=validate`, Flyway con `spring.flyway.user=${GESEDU_DB_MIGRATOR_USER}`, datasource con `${GESEDU_DB_APP_USER}`, `gesedu.asistencia.*` y `gesedu.seguridad.*` desde variables de entorno |
| `application-local.yml` | Mailpit (`spring.mail.host=localhost`, puerto 1025), `gesedu.archivos.tipo=disco`, `gesedu.notificaciones.tipo=smtp`, cookie `Secure` activa (Chrome y Firefox la aceptan en `http://localhost`), log SQL opcional |
| `application-demo.yml` | Se activa junto con `local` (`local,demo`). Agrega `classpath:db/demo` a `spring.flyway.locations` y `gesedu.reloj.fijo` opcional para simular la hora en la demo |
| `application-aws.yml` | `gesedu.archivos.tipo=s3`, `gesedu.notificaciones.tipo=ses`, región y bucket por variables, SSL JDBC, logs JSON, `management.endpoints` restringidos |
| `.env.example` | `GESEDU_DB_URL`, `GESEDU_DB_APP_USER/PASSWORD`, `GESEDU_DB_MIGRATOR_USER/PASSWORD`, `GESEDU_JWT_SECRET` (≥ 256 bits), `GESEDU_ASISTENCIA_TEACHER_LATE_THRESHOLD_MINUTES=5`, `GESEDU_ASISTENCIA_STUDENT_LATE_CUTOFF_TIME=08:30`, `GESEDU_ASISTENCIA_SCHOOL_DAY_CLOSING_TIME=15:30`, `GESEDU_TIME_ZONE=America/Lima`, `GESEDU_WEB_ORIGIN=http://localhost:5173`, `GESEDU_S3_BUCKET`, `AWS_REGION`, `GESEDU_MAIL_FROM` |

**Configuración de asistencia (DA-10).** La clase `AsistenciaProperties` es un `record` con `@ConfigurationProperties("gesedu.asistencia")` y `@Validated`, con los campos `teacherLateThresholdMinutes` (`@NotNull @PositiveOrZero`), `studentLateCutoffTime` (`LocalTime`, `@NotNull`), `schoolDayClosingTime` (`@NotNull`) y `timeZone` (`@NotNull`, `ZoneId` válido). Una restricción de clase exige `studentLateCutoffTime < schoolDayClosingTime`. Si la configuración es inválida, el contexto no levanta y `FailureAnalyzer` muestra un diagnóstico legible. La validación se prueba con `ApplicationContextRunner`.

### 2.3 Docker Compose (local)
- `postgres`: imagen `postgres:18`, volumen `gesedu-pg:/var/lib/postgresql` (**ruta nueva desde la v18**), `./infra/postgres/init:/docker-entrypoint-initdb.d:ro`, healthcheck con `pg_isready`, puerto 5432.
- `mailpit`: `axllent/mailpit` (versión fijada); SMTP en 1025 e interfaz web en http://localhost:8025.
- `localstack` (opcional, perfil de compose `aws-sim`): **desde la release 2026.03 la imagen exige un auth token**, aunque existe un plan Hobby gratuito para uso no comercial. Por eso no es obligatoria: sirve para probar `GestorArchivosS3` y `NotificadorSes` (§ 13, C-15, ya reflejado en la Tabla 9 de la arquitectura).

### 2.4 Configuración del entorno de desarrollo (fase 0)
- `.gitattributes`: `* text=auto`; `mvnw text eol=lf`; `*.sh text eol=lf`; `Dockerfile text eol=lf`; `*.cmd text eol=crlf`; `*.ps1 text eol=crlf`; binarios (`*.pdf`, `*.xlsx`, `*.png`, `*.jar`) marcados como `binary`.
- `.gitignore`: `.idea/` (las configuraciones compartidas viven en `.run/`, fuera de `.idea`), `*.iml`, `target/`, `node_modules/`, `dist/`, `.env`, `*.local`, `coverage/`, `.vscode/*` con `!.vscode/extensions.json`.
- `.editorconfig`: UTF-8, LF, indentación de 4 espacios en Java y XML y de 2 en TS, TSX, JSON, YAML y CSS, `insert_final_newline`.
- `.run/`: `Backend (local).run.xml` y `Backend (demo).run.xml` (Spring Boot, perfiles `local` y `local,demo`, cargan `.env`), `Frontend dev.run.xml` (npm `dev`), `Docker Compose.run.xml` (compose up de postgres y mailpit), `Pruebas backend.run.xml` (Maven `verify`), `Pruebas frontend.run.xml` (npm `test`) y `Todas las pruebas.run.xml` (compound de las dos anteriores).
- Quien use VS Code tiene `.vscode/extensions.json` (Java, ESLint, Prettier) y usa los comandos de § 11.

---

## 3. Dependencias y versiones

**Regla:** se usan las versiones de la arquitectura v1.0, Tabla 3. Cuando dice «administrada por Spring Boot», **no se fija versión en el `pom.xml`**. Las versiones exactas de los parches se fijan el primer día de la fase 0 contra Maven Central y npm, y se registran en `CLAUDE.md`.

### 3.1 Backend
| Dependencia (coordenadas) | Versión | Compatibilidad / notas |
|---|---|---|
| JDK Amazon Corretto (o Temurin en desarrollo) | 25 LTS | Mismo JDK en desarrollo, CI y Elastic Beanstalk |
| `spring-boot-starter-parent` | **4.1.x** (último parche; 4.1.1 publicado el 20/08/2026) | Spring Framework 7, Spring Security 7, Hibernate 7, Jackson 3 |
| `spring-boot-starter-webmvc` ⚠ | administrada | **Spring Boot 4 renombra** `spring-boot-starter-web` |
| `spring-boot-starter-security` | administrada | Spring Security 7 |
| `spring-boot-starter-security-oauth2-resource-server` ⚠ | administrada | JWT HS256 con Nimbus (`NimbusJwtEncoder`/`Decoder`), sin librerías JWT externas. **Spring Boot 4 agrega `security-` al nombre del starter** (verificado en Maven Central) |
| `spring-boot-starter-data-jpa` | administrada | Hibernate 7 / Jakarta Persistence 3.2 |
| `spring-boot-starter-validation` | administrada | Bean Validation |
| `spring-boot-starter-aspectj` ⚠ | administrada | **Spring Boot 4 renombra** `spring-boot-starter-aop` |
| `spring-boot-starter-flyway` ⚠ + `org.flywaydb:flyway-database-postgresql` | administradas | **La autoconfiguración de Flyway pasa a un módulo propio** (`spring-boot-flyway`); sin el starter, Flyway no se ejecuta. El módulo de PostgreSQL es obligatorio |
| `spring-boot-starter-mail` | administrada | SMTP hacia Mailpit (local) |
| `spring-boot-starter-cache` + `com.github.ben-manes.caffeine:caffeine` | administradas | Caché de permisos (RNF-07) y de catálogos y horario vigente (RNF-03) |
| `spring-boot-starter-actuator` | administrada | `/actuator/health` para el ALB |
| Jackson 3 `tools.jackson.core:jackson-databind` ⚠ | administrada | **El paquete cambia de `com.fasterxml.jackson` a `tools.jackson`**; las anotaciones siguen en `com.fasterxml.jackson.annotation`. `JsonMapper` reemplaza a `ObjectMapper` en la configuración |
| `org.postgresql:postgresql` | administrada | PostgreSQL 18 |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | **3.1.x** (3.1.1, construida sobre Spring Boot 4.1; la 3.0.x apunta a Spring Boot 4.0) | La línea 3.x es la de Spring Boot 4; **la 2.x no sirve** |
| `net.javacrumbs.shedlock:shedlock-spring` + `shedlock-provider-jdbc-template` | **7.x** | Línea para Spring Framework 7. ⚠ Verificar en las notas de versión que declare compatibilidad con Spring 7; si no la declara, avisar antes de seguir |
| `software.amazon.awssdk:bom` → `s3`, `ses` (o `sesv2`) | 2.x (BOM, último) | S3Presigner y SES. Solo se cargan con el perfil `aws` o con LocalStack |
| `com.github.librepdf:openpdf` | 2.x/3.x estable | Libretas y reportes PDF (arquitectura § 8.1). ⚠ La 3.x cambia el paquete a `org.openpdf`; usar la que esté vigente y documentarla |
| `org.apache.poi:poi-ooxml` | 5.x | Excel de asistencia y auditoría |
| Generación de UUIDv7 | Hibernate 7 `@UuidGenerator(style = VERSION_7)` sobre `String` | Sin dependencia extra (S-21) |
| **Pruebas** `spring-boot-starter-test` + starters de prueba por módulo (`spring-boot-starter-webmvc-test`, `-data-jpa-test`, `-security-test`) ⚠ | administradas | **En Spring Boot 4 los starters de prueba también son modulares.** JUnit 5 (Jupiter), AssertJ. `RestTestClient` de Spring Framework 7 para los endpoints |
| `spring-boot-testcontainers` + `org.testcontainers:testcontainers-postgresql` + `testcontainers-junit-jupiter` ⚠ | administradas (línea 2.x) | **Testcontainers 2 cambia las coordenadas** (`testcontainers-<modulo>`). Docker Engine 29 exige API ≥ 1.44: la 1.x falla con «client version too old»; usar la 2.x más reciente y, si persiste el error, subir el parche |
| `com.tngtech.archunit:archunit-junit5` | 1.4.x | Compatible con Java 25 (verificar el soporte de bytecode 69) |

### 3.2 Frontend (Node 22 LTS)
| Paquete | Versión | Notas |
|---|---|---|
| `react`, `react-dom` | 19.x | — |
| `typescript` | 5.9.x (`strict: true`, `noUncheckedIndexedAccess`) | — |
| `vite` + `@vitejs/plugin-react` | Vite 7.x | Requiere Node ≥ 22.12; proxy `/api` → `http://localhost:8080` (mismo origen, igual que con CloudFront) |
| `@tanstack/react-router` + `@tanstack/router-plugin` | 1.x | Rutas por archivo con el plugin de Vite |
| `@tanstack/react-query` (+ devtools) | 5.x | Compatible con React 19 |
| `react-hook-form` + `@hookform/resolvers` + `zod` | 7.x / 5.x / 4.x | — |
| `openapi-typescript` (dev) | 7.x | Tipos generados desde `/v3/api-docs` |
| `vitest`, `@vitest/coverage-v8`, `jsdom` | 3.x o 4.x | — |
| `@testing-library/react`, `@testing-library/user-event`, `@testing-library/jest-dom` | 16.x / 14.x / 6.x | Compatibles con React 19 |
| `msw` (dev) | 2.x | Simula la API en las pruebas |
| `eslint` 9 (flat config), `typescript-eslint`, `eslint-plugin-react-hooks`, `@tanstack/eslint-plugin-query`, `@tanstack/eslint-plugin-router`, `prettier` | vigentes | `npm run lint` |

Sin librería de componentes: CSS propio con tokens, Grid y Flexbox (arquitectura § 4.2), para reproducir fielmente los wireframes (S-22).

**Dependencias sin compatibilidad confirmada:** ninguna bloqueante. Hay que verificar al iniciar ShedLock 7.x con Spring Framework 7, ArchUnit con Java 25 y el nombre exacto del starter de resource server en Spring Boot 4.1. Alternativas si algo falla: para ShedLock, un `@Scheduled` con lock propio en PostgreSQL (`pg_try_advisory_xact_lock`) que conserve la idempotencia; para ArchUnit, Spring Modulith, que la arquitectura § 9.1 también admite.

---

## 4. Modelo de datos

Convenciones (S-21): PK `id VARCHAR(36)` con UUIDv7 generado en Java; todas las tablas tienen `created_at TIMESTAMPTZ` y `updated_at TIMESTAMPTZ`, y las editables concurrentemente, `version BIGINT`. Las fechas sin hora son `DATE` y los instantes `TIMESTAMPTZ`, presentados en `-05:00`. Los montos son `NUMERIC(12,2)` (RNF-11) y los enums se guardan como `VARCHAR` con `CHECK`. Extensiones: `btree_gist` (exclusión de rangos) y `citext` (correo).

### 4.1 Esquemas y tablas (derivados del Modelo Conceptual; las desviaciones llevan su ID)

**`seguridad`**
- `rol_acceso` (codigo UNIQUE ∈ {ADMINISTRADOR, DOCENTE, ALUMNO, APODERADO}, nombre, descripcion) — el Modelo Conceptual asigna un rol por usuario (Usuario 0..* → 1 RolAcceso).
- `permiso` (codigo UNIQUE, modulo, accion, descripcion), `rol_permiso` (PK (rol_id, permiso_id)).
- `usuario` (nombres, apellidos, `documento_identidad` UNIQUE NOT NULL (C-21), `correo CITEXT` con índice único parcial `WHERE correo IS NOT NULL`, hash_contrasena NULL, activo, intentos_fallidos `CHECK ≥ 0`, bloqueado_hasta, ultimo_acceso, rol_acceso_id FK, version). `CHECK (NOT activo OR (correo IS NOT NULL AND hash_contrasena IS NOT NULL))`. **No existe DELETE**: las cuentas solo se desactivan (RF-01).
- `administrador` (usuario_id UNIQUE FK, cargo), `docente` (usuario_id UNIQUE, codigo_docente UNIQUE, especialidad), `apoderado` (usuario_id UNIQUE, numero_telefono), `alumno` (usuario_id UNIQUE NOT NULL, codigo_alumno UNIQUE, fecha_nacimiento (C-22), apoderado_id FK NOT NULL («es responsable de» 1..*), parentesco_apoderado (C-22)).
- `refresh_token` (usuario_id, token_hash UNIQUE, familia, expira_en, revocado_en, reemplazado_por) — técnica (DA-04).
- `token_recuperacion` (usuario_id, token_hash UNIQUE, expira_en = emitido + 30 min, usado_en) — técnica (RF-52); índice parcial `WHERE usado_en IS NULL`.

**`academico`**
- `anio_escolar` (anio INT UNIQUE, fecha_inicio, fecha_fin, estado ∈ {PLANIFICADO, ACTIVO, CERRADO}). `CHECK fecha_fin > fecha_inicio`; la duración de 10 a 12 meses se valida en la aplicación y con `CHECK` (UC-13). Índice único parcial `(estado) WHERE estado='ACTIVO'`.
- `bimestre` (anio_escolar_id, numero `CHECK 1..4`, fecha_inicio, fecha_fin, UNIQUE (anio, numero)). `EXCLUDE USING gist (anio_escolar_id WITH =, daterange(fecha_inicio, fecha_fin, '[]') WITH &&)` impide bimestres superpuestos (UC-13, FA-2). Que el bimestre caiga dentro del año se valida en la aplicación.
- `grado` (anio_escolar_id, numero_nivel `CHECK 1..5`, UNIQUE).
- `seccion` (grado_id, anio_escolar_id, letra, `capacidad` (C-22), vacantes_disponibles, tutor_docente_id NULL FK→seguridad.docente). `CHECK (vacantes_disponibles >= 0 AND vacantes_disponibles <= capacidad)` (RNF-09); UNIQUE (grado_id, letra); UNIQUE (anio_escolar_id, tutor_docente_id).
- `curso` (anio_escolar_id, nombre, slug, horas_lectivas `CHECK > 0`, habilitado, UNIQUE (anio, nombre), UNIQUE (anio, slug)). Aplica a todos los grados (RF-23).
- `competencia` (curso_id, codigo `C1…`, nombre, `nivel_esperado` TEXT (RF-34, C-22), orden, UNIQUE (curso_id, codigo)).

**`matricula`**
- `solicitud_matricula` (codigo_seguimiento UNIQUE `MAT-2027-00482`, anio_escolar_id, alumno_id, apoderado_id, grado_id, fecha_solicitud, tipo_estudiante ∈ {NUEVO_INGRESO, RECURRENTE}, estado_documentacion, estado_admision ∈ {ADMITIDO, NO_ADMITIDO, NO_APLICA}, estado ∈ {PENDIENTE_REVISION, APROBADA, RECHAZADA}, `motivo_rechazo` ∈ {NO_ADMITIDO, DUPLICIDAD, DEUDAS, SIN_VACANTE, VOLUNTAD_APODERADO} con `observacion` (C-22), `costos_aceptados`, evaluado_por, version). `CHECK (estado <> 'RECHAZADA' OR motivo_rechazo IS NOT NULL)`. Índice único parcial `(alumno_id, anio_escolar_id) WHERE estado='PENDIENTE_REVISION'` (UC-01, FA-2).
- `documento_solicitud` (solicitud_id, tipo ∈ {CERTIFICADO_ESTUDIOS, FICHA_MATRICULA_ANTERIOR, OTRO}, clave_objeto, nombre_archivo, content_type, tamano_bytes) (C-22; metadatos del Gestor de Archivos).
- `matricula` (solicitud_id UNIQUE, alumno_id, anio_escolar_id, seccion_id, fecha_matricula, estado ∈ {CONFIRMADA, ANULADA}, confirmada_por). **Índice único parcial `(alumno_id, anio_escolar_id) WHERE estado='CONFIRMADA'`** (RNF-09, RF-05). La sección actual del alumno se deriva de su matrícula confirmada; no se duplica en `alumno`.

**`pagos`**
- `cronograma_cobro` (anio_escolar_id, concepto ∈ {MATRICULA, PENSION}, periodo, monto NUMERIC(12,2) `CHECK ≥ 0`, fecha_vencimiento, UNIQUE (anio, concepto, periodo)) y `politica_cobro` (anio_escolar_id UNIQUE, porcentaje_penalidad NUMERIC(5,2) `CHECK 0..100`) (RF-06, C-22).
- `obligacion_pago` (matricula_id, alumno_id, apoderado_id, concepto, periodo, monto_base, fecha_vencimiento, monto_penalidad `DEFAULT 0 CHECK ≥ 0`, penalidad_aplicada_en, estado ∈ {PENDIENTE, PAGADA, VENCIDA}, version, UNIQUE (matricula_id, concepto, periodo)). Índices (alumno_id), (estado, fecha_vencimiento).
- `comprobante_pago` (obligacion_id, apoderado_id, numero_operacion, fecha_pago, monto_presentado, `medio_pago` (C-22), referencia_comprobante (clave del objeto), estado_revision ∈ {PENDIENTE, APROBADO, OBSERVADO, RECHAZADO}, observacion, revisado_por, revisado_en). **Índice único parcial `(numero_operacion) WHERE estado_revision IN ('PENDIENTE','APROBADO')`** (RF-08, S-11); único `(obligacion_id) WHERE estado_revision='APROBADO'`; único `(obligacion_id) WHERE estado_revision='PENDIENTE'`.
- `registro_pago` (boleta) (comprobante_id UNIQUE, obligacion_id, numero_boleta UNIQUE `B001-00001` (secuencia `pagos.seq_boleta`), fecha_registro, monto_total).

**`asistencia`** (nombres en español según la arquitectura, Tabla 8; C-09)
- `asistencia_alumno` (alumno_id, fecha, estado ∈ {PUNTUAL, TARDANZA, FALTA}, created_at, updated_at, **UNIQUE (alumno_id, fecha)**).
- `asistencia_docente` (docente_id, fecha, hora_registro TIMESTAMPTZ NULL, estado, **UNIQUE (docente_id, fecha)**, `CHECK ((estado='FALTA') = (hora_registro IS NULL))`).

**`evaluacion`**
- `asignacion_docente` (docente_id, curso_id, seccion_id, regla_consolidacion ∈ {PROMEDIO_PONDERADO, MODA, ULTIMO_REGISTRO} `DEFAULT 'PROMEDIO_PONDERADO'` (UC-16, FA-2), UNIQUE (curso_id, seccion_id)). Se sincroniza al publicar el horario (S-13).
- `peso_bimestre` (asignacion_id, bimestre_id, peso NUMERIC(5,2) `CHECK > 0` `DEFAULT 1`, UNIQUE) (RF-38).
- `registro_logro` (asignacion_id, alumno_id, competencia_id, bimestre_id, nivel_logro `CHECK IN ('AD','A','B','C')`, fecha_registro, version, UNIQUE (alumno_id, competencia_id, bimestre_id)) (C-12, S-12).
- `nota_consolidada` (alumno_id, competencia_id, asignacion_id, anio_escolar_id, bimestre_id NULL, tipo ∈ {BIMESTRAL, ANUAL}, nivel_logro, calculada_en, libreta_id). Únicos parciales `(alumno, competencia, bimestre) WHERE tipo='BIMESTRAL'` y `(alumno, competencia, anio) WHERE tipo='ANUAL'`: el cálculo idempotente hace un upsert.
- `consolidacion_bimestral` (bimestre_id UNIQUE, fecha_consolidacion, estado, ejecutada_por) y `libreta` (alumno_id, anio_escolar_id, estado ∈ {PARCIAL, FINAL}, fecha_actualizacion, UNIQUE (alumno, anio)).

**`horarios`** (sin `Aula`; C-03)
- `bloque_horario` (anio_escolar_id, dia_semana ∈ {LUNES…DOMINGO}, hora_inicio, hora_fin, `CHECK hora_fin > hora_inicio`, UNIQUE (anio, dia, hora_inicio)). Que no haya solapamientos dentro del mismo día se valida en la aplicación. El recreo es el hueco entre bloques.
- `periodo_registro_disponibilidad` (anio_escolar_id UNIQUE, fecha_hora_inicio, fecha_hora_fin, estado ∈ {HABILITADO, CERRADO}).
- `vinculacion_docente` (docente_id, anio_escolar_id, habilitado, carga_semanal_referencia, UNIQUE) y `asociacion_docente_curso` (vinculacion_id, curso_id, UNIQUE).
- `carga_horaria_curso` (grado_id, curso_id, bloques_semanales `CHECK > 0`, UNIQUE) (C-10).
- `disponibilidad_docente` (vinculacion_id UNIQUE, fecha_hora_confirmacion, observaciones) y `bloque_disponible` (disponibilidad_id, bloque_id, UNIQUE). Solo se guarda la última versión confirmada (RF-46).
- `horario_borrador` (anio_escolar_id **UNIQUE**: un borrador por año, fecha_creacion, fecha_actualizacion, **version** para `@Version`).
- `asignacion_horario` (horario_borrador_id, bloque_id, seccion_id, curso_id, docente_id, asociacion_docente_curso_id). **UNIQUE (horario_borrador_id, bloque_id, docente_id)** y **UNIQUE (horario_borrador_id, bloque_id, seccion_id)** (RF-41, arquitectura § 9.3).
- `horario_publicado` (anio_escolar_id, numero_version, fecha_hora_publicacion, vigente, publicado_por, UNIQUE (anio, numero_version), **índice único parcial `(anio_escolar_id) WHERE vigente`**) y `asignacion_publicada` (mismos campos e índices únicos), inmutables (RF-44, RF-45).

**`auditoria`**
- `registro_auditoria` (fecha_hora `DEFAULT now()`, usuario_id NULL (NULL = «Sistema»), accion, modulo, tipo_entidad, id_entidad_afectada, descripcion). Índices (fecha_hora DESC), (usuario_id, fecha_hora), (modulo, fecha_hora) y (accion, fecha_hora) (RF-51, RNF-03).
- `cambio_auditoria` (registro_id FK, campo, valor_anterior, valor_nuevo).
- **Inmutabilidad (RNF-08):** trigger `BEFORE UPDATE OR DELETE` y trigger `BEFORE TRUNCATE` (a nivel de sentencia) que ejecutan `auditoria.rechazar_modificacion()` → `RAISE EXCEPTION 'auditoría de solo inserción'`. `REVOKE ALL` y `GRANT SELECT, INSERT … TO gesedu_app`.

**`transversal`**: `shedlock` (name PK, lock_until, locked_at, locked_by).

### 4.2 Roles de base de datos
- `gesedu_migrador`: dueño de los esquemas y tablas; lo usa Flyway (`spring.flyway.user`). Crea los triggers y otorga los permisos.
- `gesedu_app`: lo usa el datasource de la aplicación. `USAGE` en los esquemas; `SELECT, INSERT, UPDATE, DELETE` en las tablas de negocio; **solo `SELECT, INSERT` en `auditoria.*`**; `USAGE, SELECT` en las secuencias. Los permisos se dejan con `ALTER DEFAULT PRIVILEGES FOR ROLE gesedu_migrador IN SCHEMA … GRANT … TO gesedu_app`, y para `auditoria` se revocan UPDATE y DELETE de forma explícita.
- Los roles y la BD se crean con `infra/postgres/init/01-roles-y-bd.sql` en local y en Testcontainers; en RDS, una vez, con el usuario maestro (`docs/despliegue/aws.md`). Flyway no crea roles: hacerlo requiere superusuario.

### 4.3 Reglas transaccionales críticas en la BD
- Vacantes (RNF-09): `UPDATE academico.seccion SET vacantes_disponibles = vacantes_disponibles - 1 WHERE id = ? AND vacantes_disponibles > 0`; si afecta 0 filas → `SIN_VACANTE`. A eso se suman el `CHECK` y el índice único parcial de matrícula.
- Pagos (RNF-10): `SELECT … FROM pagos.obligacion_pago WHERE id = ? FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`) dentro de un único `@Transactional`: comprobante → APROBADO, `registro_pago` (boleta), obligación → PAGADA y auditoría. Cualquier excepción revierte todo.
- Horarios: el validador de la aplicación, los índices únicos y `@Version` (`OPTIMISTIC_FORCE_INCREMENT` sobre `horario_borrador` en cada alta, cambio o baja).

### 4.4 Migraciones de Flyway (en orden)
**Regla de edición (S-26).** Hasta el **primer despliegue compartido** (el primer entorno con una base de datos que no sea la local de cada integrante: QA o AWS) las migraciones **V1 a V11 se pueden editar**; cada integrante reinicia su base con `scripts/reiniciar-bd.ps1` o `scripts/reiniciar-bd.sh` (`docker compose down -v` + `up`). El CI siempre parte de una base vacía. Quien edite una migración lo avisa en el PR y en el canal del equipo, porque Flyway rechaza el arranque con un checksum distinto en una base ya migrada. `db/demo/V1000` se puede editar siempre. **Desde el primer despliegue compartido, solo hacia adelante:** una migración integrada no se modifica y los cambios van en `V12+`. El líder técnico anuncia el momento con la etiqueta de git `esquema-congelado` y actualiza `CLAUDE.md`.

| Versión | Contenido |
|---|---|
| V1__extensiones_y_esquemas.sql | `btree_gist`, `citext`; esquemas `seguridad, academico, matricula, pagos, asistencia, evaluacion, horarios, auditoria, transversal`; default privileges hacia `gesedu_app` |
| V2__seguridad.sql | rol_acceso, permiso, rol_permiso, usuario, administrador, docente, apoderado, alumno, refresh_token, token_recuperacion |
| V3__academico.sql | anio_escolar, bimestre (EXCLUDE), grado, seccion (CHECK vacantes), curso, competencia |
| V4__pagos.sql | cronograma_cobro, politica_cobro, obligacion_pago, comprobante_pago (índices parciales), registro_pago, seq_boleta |
| V5__matricula.sql | solicitud_matricula, documento_solicitud, matricula (índice único parcial) |
| V6__horarios.sql | bloque_horario, periodo, vinculacion, asociacion, carga_horaria, disponibilidad, bloque_disponible, borrador, asignacion_horario (índices anticruce), publicado (índice vigente), asignacion_publicada |
| V7__evaluacion.sql | asignacion_docente, peso_bimestre, registro_logro, nota_consolidada, consolidacion_bimestral, libreta |
| V8__asistencia.sql | asistencia_alumno, asistencia_docente |
| V9__auditoria.sql | tablas, índices, función y triggers, GRANT SELECT/INSERT |
| V10__shedlock.sql | transversal.shedlock |
| V11__catalogo_roles_permisos.sql | Los 4 roles y los permisos de § 5.3 (datos de referencia que existen en todos los ambientes) |
| db/demo/V1000__datos_demo.sql | Solo con el perfil demo (§ 7.2) |

---

## 5. Contrato de la API

### 5.1 Convenciones (arquitectura § 7.3)
- Prefijo `/api/v1`, sustantivos en plural, JSON. Fechas ISO 8601 con `-05:00`; montos como string decimal de 2 cifras (`"350.00"`) para no perder precisión en JS (S-23).
- **Paginación:** `?page=0&size=20&sort=campo,asc` (máximo 100; lista blanca de campos ordenables). Respuesta `{contenido, pagina, tamano, totalElementos, totalPaginas}` (Spring Data `PagedModel` vía DTO).
- **Concurrencia optimista:** los recursos con `@Version` devuelven `ETag: "<version>"`. Las modificaciones exigen `If-Match`: sin la cabecera → 400 `VERSION_REQUERIDA`; con una versión desactualizada → **409** `CONFLICTO_CONCURRENCIA`, como dice la arquitectura (§ 4.7), en lugar de 412. Se aplica al borrador de horario (versión del borrador), a los usuarios y a la configuración académica.
- **Errores (RFC 9457):** `application/problem+json` con `ProblemDetail` de Spring: `type` (`https://gesedu.pe/problemas/<codigo>`), `title`, `status`, `detail`, `instance` y, como propiedades extra, `codigo`, `errores[]` (campo y mensaje, en 400) y `conflictos[]` (horarios). `ResponseEntityExceptionHandler` + `@RestControllerAdvice`; `spring.mvc.problemdetails.enabled=true`. El 401 y el 403 de Spring Security salen con el mismo formato (`AuthenticationEntryPoint` y `AccessDeniedHandler` propios).
- Documentación: `/v3/api-docs` y `/swagger-ui.html`, con esquema de seguridad bearer y grupos por módulo.

### 5.2 Códigos de negocio (`docs/api/errores.md`)
| HTTP | Código | Origen |
|---|---|---|
| 400 | VALIDACION_INVALIDA, VERSION_REQUERIDA, TOKEN_RECUPERACION_INVALIDO | Bean Validation, If-Match, RF-52 |
| 401 | NO_AUTENTICADO, CREDENCIALES_INVALIDAS, CUENTA_BLOQUEADA (incluye `bloqueadoHasta`), CUENTA_INACTIVA, SESION_EXPIRADA | RF-52, RNF-06 |
| 403 | ACCESO_DENEGADO, FUERA_DE_ALCANCE | RF-50, RNF-07 |
| 404 | RECURSO_NO_ENCONTRADO | — |
| 409 | CONFLICTO_CONCURRENCIA, SIN_VACANTE, MATRICULA_DUPLICADA, SOLICITUD_EN_TRAMITE, DOCUMENTO_DUPLICADO, CORREO_DUPLICADO, CONFLICTO_HORARIO (con `conflictos`), NUMERO_OPERACION_DUPLICADO, ASISTENCIA_YA_REGISTRADA | RNF-09, RF-05, RF-02, RF-41, RF-08, UC-07 FA-1, UC-08 FA-5 |
| 422 | DEUDA_VENCIDA, NO_ADMITIDO, DOCUMENTACION_INCOMPLETA, OBLIGACION_YA_PAGADA, MONTO_INSUFICIENTE, DOCENTE_NO_ASOCIADO, DOCENTE_NO_DISPONIBLE, HORARIO_INCOMPLETO, PERIODO_DISPONIBILIDAD_CERRADO, DIA_NO_LECTIVO, JORNADA_CERRADA, SIN_CLASES_PROGRAMADAS, PLAZO_TARDANZA_EXCEDIDO, RANGO_ANIO_INVALIDO, BIMESTRES_SUPERPUESTOS, BIMESTRE_INCOMPLETO, NIVEL_LOGRO_INVALIDO, ARCHIVO_INVALIDO, VINCULO_ACADEMICO_INEXISTENTE | Reglas de negocio de la ERS |

### 5.3 Permisos (RF-48)
`SESION` (implícito en todos los roles) · `USUARIO_CONSULTAR`, `USUARIO_GESTIONAR`, `ROL_GESTIONAR` · `MATRICULA_SOLICITAR`, `MATRICULA_CONSULTAR_PROPIA`, `MATRICULA_EVALUAR`, `MATRICULA_REGISTRAR` · `COBRO_CONFIGURAR`, `OBLIGACION_CONSULTAR_PROPIA`, `COMPROBANTE_PRESENTAR`, `COMPROBANTE_VALIDAR`, `PAGO_HISTORIAL_CONSULTAR` · `ASISTENCIA_DOCENTE_MARCAR`, `ASISTENCIA_ALUMNO_REGISTRAR`, `ASISTENCIA_REPORTE_SECCION`, `ASISTENCIA_REPORTE_GENERAL`, `ASISTENCIA_DOCENTE_CONSULTAR`, `ASISTENCIA_CONSULTAR_PROPIA` · `ACADEMICO_CONFIGURAR`, `ACADEMICO_CONSULTAR` · `NOTA_REGISTRAR`, `REGLA_DEFINIR`, `NOTA_CALCULAR_FINAL`, `NOTA_CONSOLIDAR`, `NOTA_CONSULTAR_PROPIA` · `HORARIO_CONFIGURAR`, `HORARIO_GESTIONAR`, `DISPONIBILIDAD_REGISTRAR`, `HORARIO_CONSULTAR` · `AUDITORIA_CONSULTAR`, `AUDITORIA_EXPORTAR`.
Asignación inicial: ADMINISTRADOR, todos los de gestión; DOCENTE, marcar asistencia, registrar asistencia de alumnos, reporte de sección, notas, regla, cálculo final, disponibilidad y consulta de horario; ALUMNO, los `*_PROPIA` y la consulta de horario; APODERADO, `MATRICULA_SOLICITAR`, `MATRICULA_CONSULTAR_PROPIA`, `OBLIGACION_CONSULTAR_PROPIA`, `COMPROBANTE_PRESENTAR`, `NOTA_CONSULTAR_PROPIA` y `HORARIO_CONSULTAR`.
**El alcance (mi sección, mis hijos, mis cursos) se verifica en el servicio** con `AlcanceService`, además de `@PreAuthorize`; si no se cumple → 403 `FUERA_DE_ALCANCE`.

### 5.4 Endpoints por módulo
(Perm. = permiso de `@PreAuthorize`; ★ = endpoint de la fase 1)

**Seguridad — UC-24, RF-01, RF-48, RF-50, RF-52**
| Método | Ruta | Perm. | UC / RF |
|---|---|---|---|
| POST ★ | /auth/inicio-sesion `{correo, contrasena}` → `{tokenAcceso, expiraEn, usuario{nombres, rol, cargo, permisos[]}}` + cookie de refresh | público (con límite de peticiones) | RF-52, RNF-06 |
| POST ★ | /auth/refresco (cookie) → nuevo token de acceso y rotación del refresh | público + cookie | DA-04 |
| POST ★ | /auth/cierre-sesion | SESION | RF-52 |
| POST ★ | /auth/recuperacion `{correo}` → **202 siempre con el mismo cuerpo** | público (con límite de peticiones) | RF-52 |
| POST ★ | /auth/recuperacion/confirmacion `{token, nuevaContrasena}` | público | RF-52 |
| GET ★ | /auth/yo | SESION | RF-50 (menú) |
| GET ★ / POST / PUT / PATCH | /usuarios (filtros: q, rol, estado ACTIVO/INACTIVO/BLOQUEADO/PENDIENTE; paginado), /usuarios/{id} (ETag), POST /usuarios (alta con invitación de 72 h, S-77), PUT /usuarios/{id} (`If-Match`), PATCH /usuarios/{id}/activacion `{correo?}` (vínculo académico, S-82), /usuarios/{id}/desactivacion, POST /usuarios/{id}/invitacion (reenviar), /usuarios/{id}/desbloqueo | USUARIO_CONSULTAR / USUARIO_GESTIONAR | UC-24, RF-01 |
| GET / PUT | /roles, /roles/{id}/permisos | ROL_GESTIONAR | RF-48 |

**Académico — UC-13, UC-14, RF-03, RF-04, RF-23 a RF-25**
| GET / POST | /anios-escolares, /anios-escolares `{fechaInicio, fechaFin, bimestres[4]}` | ACADEMICO_CONSULTAR / ACADEMICO_CONFIGURAR | UC-13, RF-03, RF-25 |
| GET / PATCH | /anios-escolares/activo (cualquier usuario autenticado), /anios-escolares/{anio}/activacion | ACADEMICO_CONFIGURAR | UC-13 (extensión), C-47 |
| GET / POST / PUT / DELETE | /anios-escolares/{anio}/cursos, /cursos/{id} (con competencias), /anios-escolares/{anio}/cursos/copia-anterior | ACADEMICO_CONFIGURAR | UC-14, RF-23, RF-24 |
| GET / POST / PUT | /anios-escolares/{anio}/grados, /grados/{id}/secciones, /secciones/{id} (capacidad, tutor) | ACADEMICO_CONFIGURAR | RF-03, RF-04 |

**Matrícula — UC-01, UC-02, RF-02, RF-04, RF-05, RF-12**
| POST | /solicitudes-matricula/archivos (pide una URL prefirmada de subida) | MATRICULA_SOLICITAR | UC-01, RF-08 (Gestor de Archivos) |
| POST | /solicitudes-matricula | MATRICULA_SOLICITAR | UC-01 |
| GET | /solicitudes-matricula/mias | MATRICULA_CONSULTAR_PROPIA | UC-01 (seguimiento) |
| GET ★ | /solicitudes-matricula?estado=&q= (bandeja paginada) | MATRICULA_EVALUAR | UC-02, paso 1 |
| GET ★ | /solicitudes-matricula/{codigo} (detalle + validaciones + vacantes por sección + resumen de costos) | MATRICULA_EVALUAR | UC-02, pasos 2–7 |
| GET ★ | /solicitudes-matricula/{codigo}/documentos/{id}/url (URL prefirmada de descarga) | MATRICULA_EVALUAR | UC-02, paso 2 |
| POST ★ | /solicitudes-matricula/{codigo}/confirmacion `{seccionId, costosAceptados:true}` (If-Match) → 201 matrícula + obligaciones | MATRICULA_REGISTRAR | UC-02, pasos 9–13, RNF-09, RF-07, RF-12 |
| POST ★ | /solicitudes-matricula/{codigo}/rechazo `{motivo, observacion}` | MATRICULA_REGISTRAR | UC-02, FA-1 a FA-5 |
| GET | /matriculas/mias?alumnoId= | MATRICULA_CONSULTAR_PROPIA | UC-03 |

**Pagos — UC-03 a UC-06, RF-06 a RF-11**
| GET / PUT | /anios-escolares/{anio}/cobros (cronograma + política) | COBRO_CONFIGURAR | RF-06 |
| GET | /alumnos/{alumnoId}/obligaciones (alcance de apoderado) | OBLIGACION_CONSULTAR_PROPIA | UC-03, RF-09, RF-11 |
| POST | /obligaciones/{id}/comprobantes/archivo (URL prefirmada) y /obligaciones/{id}/comprobantes | COMPROBANTE_PRESENTAR | UC-04, RF-08 |
| GET | /comprobantes?estado=PENDIENTE (bandeja) y /comprobantes/{id} (+ URL del voucher) | COMPROBANTE_VALIDAR | UC-05, pasos 1–3 |
| POST | /comprobantes/{id}/validacion, /comprobantes/{id}/observacion, /comprobantes/{id}/rechazo | COMPROBANTE_VALIDAR | UC-05, RNF-10, RNF-11 |
| GET | /alumnos/{codigoAlumno}/historial-financiero | PAGO_HISTORIAL_CONSULTAR | UC-06, RF-11 |

**Horarios — UC-20 a UC-23, RF-39 a RF-47**
| GET / PUT | /anios-escolares/{anio}/bloques, /periodo-disponibilidad, /vinculaciones-docentes (+ asociaciones con cursos), /cargas-horarias | HORARIO_CONFIGURAR | RF-46, RF-47, RF-43 |
| GET / PUT | /anios-escolares/{anio}/disponibilidad/mia | DISPONIBILIDAD_REGISTRAR | UC-20, RF-46 |
| GET ★ | /horarios/{anio}/borrador?perspectiva=SECCION&seccionId= o perspectiva=DOCENTE&docenteId= (crea el borrador si no existe; ETag) | HORARIO_GESTIONAR | UC-21, RF-39, RF-44 |
| GET ★ | /horarios/{anio}/borrador/opciones?bloqueId&seccionId[&cursoId][&docenteId] → cursos con horas asignadas/requeridas y docentes asociados con `disponible`/`ocupadoEn` | HORARIO_GESTIONAR | RF-47, RF-43 |
| POST ★ | /horarios/{anio}/borrador/validaciones `{bloqueId, seccionId, cursoId, docenteId, asignacionId?}` → `{valido, conflictos[]}` | HORARIO_GESTIONAR | RF-41, RF-42 (tiempo real) |
| POST ★ / PUT ★ / DELETE ★ | /horarios/{anio}/borrador/asignaciones[/{id}] (If-Match) → 201/200/204 o 409 CONFLICTO_HORARIO / CONFLICTO_CONCURRENCIA | HORARIO_GESTIONAR | RF-40 a RF-42, RF-47 |
| GET | /horarios/{anio}/borrador/completitud | HORARIO_GESTIONAR | UC-22, RF-43 |
| POST | /horarios/{anio}/publicaciones | HORARIO_GESTIONAR | UC-22, RF-44 |
| GET | /horarios/{anio}/vigente?perspectiva=&seccionId=&docenteId= (alcance por rol; 404 HORARIO_NO_PUBLICADO) | HORARIO_CONSULTAR | UC-23, RF-45 |

**Asistencia — UC-07 a UC-12, RF-13 a RF-21**
| GET / POST | /asistencia-docente/hoy (hora del servidor, primera clase, tolerancia) y /asistencia-docente/marcaciones | ASISTENCIA_DOCENTE_MARCAR | UC-07, RF-18 a RF-20 |
| GET | /asistencia-docente/mias?desde&hasta | ASISTENCIA_DOCENTE_MARCAR | UC-07 (historial) |
| GET / POST | /secciones/{codigo}/asistencia?fecha=hoy y /secciones/{codigo}/asistencia (lote atómico; cada estado ∈ PUNTUAL, TARDANZA o FALTA) | ASISTENCIA_ALUMNO_REGISTRAR (tutor) | UC-08, RF-14 |
| PATCH | /asistencia-alumnos/{id} `{estado:"TARDANZA"}` | ASISTENCIA_ALUMNO_REGISTRAR (tutor) | UC-09, RF-15 |
| GET | /reportes/asistencia?ambito=ALUMNOS\|DOCENTES&mes&anio&seccion=&formato=json\|xlsx\|pdf | ASISTENCIA_REPORTE_SECCION / _GENERAL | UC-10, UC-11, RF-16, RF-17, RF-21 |
| GET | /asistencia-docente?fecha= | ASISTENCIA_DOCENTE_CONSULTAR | UC-12 (consulta) |
| GET | /alumnos/{id}/asistencia/resumen?mes= | ASISTENCIA_CONSULTAR_PROPIA | Inicio del alumno (RF-16 bajo demanda) |

**Evaluación — UC-15 a UC-19, RF-22, RF-26 a RF-38**
| GET | /asignaciones-docentes/mias | NOTA_REGISTRAR | RF-31 |
| GET / PUT | /asignaciones-docentes/{id}/registros?bimestre= (rejilla alumnos × competencias) | NOTA_REGISTRAR | UC-15, RF-22 |
| GET / PUT | /asignaciones-docentes/{id}/configuracion `{regla, pesos[bimestre]}` | REGLA_DEFINIR | UC-16, RF-26, RF-38 |
| GET / POST | /asignaciones-docentes/{id}/notas-finales y /asignaciones-docentes/{id}/notas-finales/calculo | NOTA_CALCULAR_FINAL | UC-17, RF-28, RF-29 |
| GET / POST | /anios-escolares/{anio}/bimestres/{n}/avance y /anios-escolares/{anio}/bimestres/{n}/consolidacion | NOTA_CONSOLIDAR | UC-18, RF-27, RF-35, RF-37 |
| GET | /alumnos/{id}/cursos, /alumnos/{id}/cursos/{cursoId}/notas, /alumnos/{id}/libreta?periodo=&formato=pdf | NOTA_CONSULTAR_PROPIA | UC-19, RF-30, RF-32 a RF-34, RF-36 |
| GET | /apoderados/yo/alumnos | NOTA_CONSULTAR_PROPIA | UC-19, FA-1, RF-33 |

**Auditoría — UC-25, RF-49, RF-51**
| GET ★ | /auditoria/registros?usuarioId&modulo&accion&desde&hasta (paginado) | AUDITORIA_CONSULTAR | UC-25, pasos 4–5 |
| GET ★ | /auditoria/registros/{id} (incluye los cambios) | AUDITORIA_CONSULTAR | RF-49 |
| GET ★ | /auditoria/registros/exportacion?…&formato=xlsx\|csv | AUDITORIA_EXPORTAR | UC-25, paso 6 |
| GET ★ | /auditoria/catalogos (módulos y acciones para los filtros) | AUDITORIA_CONSULTAR | RF-51 |

**Archivos (desarrollo local sin S3):** `PUT/GET /api/v1/archivos-locales/{token}` existe solo cuando `gesedu.archivos.tipo=disco`. Imita la URL prefirmada con un token HMAC de 5 min, en el mismo origen (S-20).

---

## 6. Mapeo de pantallas (wireframes → rutas)

| Wireframe | Ruta SPA | Componentes principales | Endpoints | Roles | Fase |
|---|---|---|---|---|---|
| Login (p. 1) | `/login` | FormLogin (rhf + zod), AlertaBloqueo, enlace «¿Olvidaste tu contraseña?» | /auth/inicio-sesion | público | 1 |
| *(sin wireframe, S-01)* | `/recuperar-contrasena`, `/restablecer-contrasena?token=` | FormCorreo, FormNuevaContrasena | /auth/recuperacion[/confirmacion] | público | 1 |
| Inicio del alumno (p. 2–3) | `/intranet/inicio` | TarjetaClaseActual, ClasesDeHoy, ResumenAsistencia, NotasBimestre, EstadoMatricula. En la fase 1, inicio mínimo por rol | /auth/yo, /horarios/{anio}/vigente, /alumnos/{id}/… | todos (contenido por rol) | 1 (mínimo) → 5 |
| Solicitar matrícula (p. 4–5) | `/intranet/matricula/solicitud` | Asistente de 4 pasos, SelectorArchivo (subida prefirmada), resumen de errores | /solicitudes-matricula[/archivos] | Apoderado | 2 |
| Evaluar solicitud (p. 6) | `/intranet/matricula/solicitudes` y `/intranet/matricula/solicitudes/$codigo` | BandejaSolicitudes, DetalleSolicitud, ListaValidaciones, SelectorSeccion (vacantes), TablaCostos, CheckAceptacion, DialogoRechazo | ★ bandeja, detalle, confirmación, rechazo | Administrador | **1** |
| Matrícula y pagos del apoderado (p. 7–8) | `/intranet/pagos` | SelectorHijo, TarjetaMatricula, TablaObligaciones (desglose de penalidad), tarjetas Deuda vencida y Próximo vencimiento | /matriculas/mias, /alumnos/{id}/obligaciones | Apoderado (el alumno ve su matrícula en el inicio) | 2 |
| Presentar comprobante (p. 9–10) | `/intranet/pagos/comprobante?obligacion=` | FormComprobante, SelectorArchivo | /obligaciones/{id}/comprobantes… | Apoderado | 2 |
| Validar comprobantes (p. 11) | `/intranet/pagos/validar` | BandejaComprobantes, PanelVerificacion (monto vs. total), VisorVoucher, Rechazar/Observar/Validar | /comprobantes… | Administrador | 2 |
| Historial financiero (p. 12) | `/intranet/pagos/historial` (buscador) y `/intranet/pagos/historial/$codigoAlumno` | Pestañas Obligaciones / Boletas / Deudas, TotalesFinancieros | /alumnos/{codigo}/historial-financiero | Administrador | 2 |
| Mi asistencia docente (p. 13–14) | `/intranet/mi-asistencia` | RelojServidor, BotonRegistrar, Ultimos5Dias. **Sin la insignia de «red del colegio» (C-04)** | /asistencia-docente/hoy, marcaciones, mias | Docente | 4 |
| Registrar asistencia de la sección (p. 15) | `/intranet/asistencia/$seccion/registrar` | TablaPaseLista (Puntual / Tardanza / Falta, según UC-08 paso 5 y RF-14; el wireframe p. 15 solo muestra Puntual y Falta y no se actualizará: prevalece la ERS), MarcarTodos, AvisoPendientes | /secciones/{codigo}/asistencia | Docente tutor | 4 |
| Registro del día (p. 16) | `/intranet/asistencia/$seccion/registro-del-dia` | TablaDia, DialogoCambiarATardanza (hora límite). **Sin historial ni responsable (C-07)** | GET asistencia, PATCH | Docente tutor | 4 |
| Reportes de asistencia (p. 17) | `/intranet/asistencia/reportes` | Filtros (ámbito, mes, sección), VistaPrevia, Descargar Excel/PDF, «generado el …». **El % sigue la ERS (C-06)** | /reportes/asistencia | Administrador, Docente tutor (solo su sección) | 4 |
| Consolidación mensual (p. 18) | — **no se implementa** (C-05) | — | — | — | — |
| Asistencia docente del día (p. 19) | `/intranet/asistencia/docentes/$fecha` | TablaDocentesDia, «generado al cierre» | /asistencia-docente?fecha= | Administrador | 4 |
| Nuevo año escolar (p. 20) | `/intranet/academico/anio-escolar/nuevo` | Paso de fechas, Paso de bimestres, LineaTiempo con superposiciones | /anios-escolares | Administrador (Directivo) | 3 |
| Cursos y competencias (p. 21) | `/intranet/academico/$anio/cursos` | ListaCursos, FormCurso, ListaCompetencias, CopiarAñoAnterior | /anios-escolares/{anio}/cursos… | Administrador (Directivo) | 3 |
| Registro de notas (p. 22) | `/intranet/notas/$curso/$seccion/bimestre-$n` | RejillaLogros (AD/A/B/C), AvisoIncompletos, contador de celdas | /asignaciones-docentes/{id}/registros | Docente | 5 |
| Configuración del curso (p. 23) | `/intranet/cursos/$cursoSeccion/configuracion` (p. ej. `matematica-3A`) | TarjetasRegla con ejemplo, EditorPesos por bimestre (S-14) | /asignaciones-docentes/{id}/configuracion | Docente | 5 |
| Notas finales (p. 24) | `/intranet/notas/$curso/$seccion/finales` | TablaBimestres×Competencias, BotonCalcular | /…/notas-finales[/calculo] | Docente | 5 |
| Consolidar notas (p. 25) | `/intranet/academico/consolidar-notas` | TablaAvance, DocentesPendientes, botón habilitado al 100 % | /bimestres/{n}/avance, consolidacion | Administrador (Directivo) | 5 |
| Mis notas (p. 26–27) | `/intranet/notas` | ListaCursos, DetalleCompetencias (nivel esperado), HistoricoBimestres, Descargar informe, Ver libreta | /alumnos/{id}/… | Alumno, Apoderado (con SelectorHijo) | 5 |
| Mi disponibilidad (p. 28) | `/intranet/disponibilidad/$anio` | MatrizDisponibilidad (clic para alternar), AlertaInsuficiente, Observaciones | /disponibilidad/mia | Docente | 3 |
| Elaborar horario (p. 29) | `/intranet/horarios/$anio/elaborar` | **MatrizHorario** (perspectivas Sección/Docente, **sin «Por aula» (C-03)**), PanelAsignacion (curso con h asignadas/requeridas, docentes con Disponible/«tiene clase en…», alerta de conflicto en vivo), CargaHorariaSeccion | ★ borrador, opciones, validaciones, asignaciones | Administrador | **1** |
| Publicar horario (p. 30) | `/intranet/horarios/$anio/publicar` | TablaCompletitud, DetalleObservaciones, Publicar deshabilitado mientras haya observaciones | /completitud, /publicaciones | Administrador | 3 |
| Mi horario (p. 31–33) | `/intranet/horario` | MatrizHorario (solo lectura), DetalleBloque, vista diaria en móvil, «aún no publicado» + «Ver horario 2026» | /horarios/{anio}/vigente | Alumno, Apoderado, Docente (perspectiva docente), Administrador (cualquiera) | 3 |
| Usuarios (p. 34) | `/intranet/usuarios` | TablaUsuarios (filtros, paginación), DialogoNuevoUsuario (rol, vínculo con matrícula, permisos del rol), Desactivar/Activar/Desbloquear | /usuarios… | Administrador | 1 (lista + desbloquear) → 6 |
| Auditoría (p. 35) | `/intranet/auditoria` | FiltrosAuditoria, TablaPaginada, Exportar ▾ (Excel/CSV), «no se pueden editar ni eliminar» | ★ /auditoria/… | Administrador | **1** |
| *(sin wireframe, S-02)* | `/intranet/academico/$anio/secciones`, `/intranet/pagos/configuracion`, `/intranet/horarios/$anio/configuracion`, `/intranet/roles` | Formularios simples | RF-03, RF-04, RF-06, RF-46, RF-47, RF-48 | Administrador | 2, 3, 6 |

El menú lateral (escritorio) y la barra inferior (móvil, p. 3, 8, 14, 27) se arman con los **permisos** de `/auth/yo`. Rutas: TanStack Router con `beforeLoad` exige sesión y permiso y redirige a `/login` o a `/intranet/inicio`. Esto solo oculta opciones: la decisión de acceso la toma siempre el backend (DA-05).

---

## 7. Fases de implementación

**Calendario.** La demo es el **23/10/2026** (P-01, resuelta) y solo exige la arquitectura funcionando en localhost, por lo que el alcance de esa fecha es la sección 7.0. Las semanas S1–S8 de las fases siguientes **empiezan el día siguiente a la demo**; su conversión a fechas calendario queda pendiente (P-08). Cada fase termina con CI en verde y una demo interna.

### 7.0 Demo 23/10 (esqueleto vertical, mínimo indispensable)
**Objetivo.** Mostrar la arquitectura funcionando en localhost: pantalla → REST → filtro JWT → controlador → servicio → repositorio → PostgreSQL 18. Es un subconjunto de las fases 0 y 1; lo que se construye aquí **se conserva** (no se tira) cuando se continúe con el plan.

**Dentro de la demo**
| # | Entrega | Detalle |
|---|---|---|
| 1 | Monorepo, Docker y `.run/` | `gesedu/` con `backend/` y `frontend/`, `docker-compose.yml` con `postgres:18` (volumen en `/var/lib/postgresql`) y Mailpit opcional, `.gitattributes`, `.gitignore`, `.editorconfig`, `.env.example`, `.run/` para IntelliJ (Docker Compose, Backend demo, Frontend dev, Pruebas backend) y `scripts/reiniciar-bd.ps1/.sh` |
| 2 | Backend Spring Boot 4.1 + Java 25 | Estructura `pe.edu.gesedu.<modulo>.{api, aplicacion, dominio, infraestructura}` para los 10 paquetes del plan, aunque **solo se implementen `seguridad`, `auditoria` y `horarios`** (más `academico` solo para `anio_escolar`, que necesita `bloque_horario`). Prueba **ArchUnit** básica: dependencias entre capas, módulos solo vía `aplicacion` y sin ciclos entre módulos |
| 3 | Flyway mínimo | Se conserva la numeración del plan, con huecos: `V1` extensiones y esquemas, `V2` seguridad (`rol_acceso`, `permiso`, `rol_permiso`, `usuario`, `administrador`), `V3` `academico.anio_escolar`, `V6` `horarios.bloque_horario`, `V9` auditoría (tablas + trigger de solo inserción + `GRANT SELECT, INSERT` a `gesedu_app`), `V11` catálogo de roles y permisos. Roles de BD `gesedu_migrador` y `gesedu_app`. Semilla `db/demo/V1000`: **un usuario por rol y el Directivo** (+ la cuenta `bloqueo@`), el año 2027 y sus **bloques horarios** (§ 7.2). Las migraciones restantes (`V4`, `V5`, `V7`, `V8`, `V10`) se agregan después; mientras no haya despliegue compartido todos reinician la base (S-26) |
| 4 | Seguridad | Inicio de sesión con **JWT (15 min) y BCrypt 12**, `@PreAuthorize` con permisos, **bloqueo tras 5 intentos** (RNF-06, S-03: 15 min) y errores **RFC 9457** con `codigo`. **Sin refresh token ni recuperación de contraseña** (S-27) |
| 5 | Endpoints de ejemplo | `GET /api/v1/auth/yo`; `GET /api/v1/anios-escolares/{anio}/bloques` (cualquier rol, permiso `HORARIO_CONSULTAR`); `POST /api/v1/anios-escolares/{anio}/bloques` (solo Administrador, `HORARIO_CONFIGURAR`, `@Auditable`) |
| 6 | Auditoría | Aspecto `@Auditable` síncrono en la misma transacción; `GET /api/v1/auditoria/registros` con filtros (usuario, módulo, acción, fechas) y paginación, solo Administrador (`AUDITORIA_CONSULTAR`) |
| 7 | Swagger UI | springdoc 3.x en `/swagger-ui.html` con bearer |
| 8 | Frontend | React 19 + TypeScript + Vite + TanStack Router + TanStack Query: `/login`, `/intranet/inicio` con menú según el rol, `/intranet/horarios/bloques` y `/intranet/auditoria`. Diseño simple y responsive |
| 9 | Guion | `docs/demo/guion.md` con los comandos en PowerShell y los pasos de la demo (5 a 10 minutos) |

**Fuera de la demo (queda para después, según el plan):** CI, refresh token y cierre de sesión revocable, recuperación de contraseña, exportación de auditoría, UC-21 completo, UC-02 y matrícula, el resto del esquema (V4, V5, V7, V8, V10) y de los módulos.

**Desviaciones respecto al plan v1.2 (temporales)**
- En la demo, `GET .../bloques` exige `HORARIO_CONSULTAR` (todos los roles) y no `HORARIO_CONFIGURAR`; en el plan completo la configuración de bloques es solo de gestión.
- El cierre de sesión de la demo solo borra el token de la SPA: sin refresh token no hay revocación en el servidor (se completa en la fase 0 completa).
- El token de acceso dura 15 minutos y no se renueva: la demo debe durar menos o se inicia sesión de nuevo.

**Orden de construcción y puntos de control.** (1) Docker, base del repositorio, backend base y migraciones; (2) seguridad y auditoría; (3) endpoints y Swagger; (4) frontend; (5) guion. Al terminar cada parte se verifica con los comandos indicados y se espera la confirmación del equipo antes de continuar.



### Fase 0 — Base (S1)
**Tareas**
1. Repositorio: `.gitattributes`, `.gitignore`, `.editorconfig`, `.env.example`, `README.md`, `.run/*` (§ 2.4), `docker-compose.yml` + `infra/postgres/init` (§ 2.3), y los scripts `.sh`/`.ps1`.
2. Backend: proyecto Maven con wrapper (Spring Boot 4.1.x, Java 25); paquetes vacíos de los 10 módulos con sus 4 capas; perfiles; `AsistenciaProperties` validado (DA-10); `Clock` con la zona institucional.
3. Flyway V1 a V11 (todo el esquema, para que los equipos no se bloqueen entre sí; editables hasta el primer despliegue compartido, § 4.4 y S-26) y los roles `gesedu_migrador` y `gesedu_app`.
4. `compartido`: `EntidadBase` (UUIDv7, auditoría técnica de timestamps), `Dinero` (BigDecimal escala 2, HALF_UP), `ExcepcionNegocio` + `CodigoError` + `ManejadorProblemas` (RFC 9457), paginación, ETag e If-Match.
5. Seguridad (inicio y cierre de sesión, refresh, bloqueo y permisos; la recuperación de contraseña pasa a la segunda prioridad de la fase 1): BCrypt 12, emisión y validación de JWT HS256 (15 min), refresh rotativo con detección de reutilización, filtro que verifica cuenta activa y no bloqueada (caché Caffeine), permisos con `@PreAuthorize` y caché invalidable (RNF-07), bloqueo tras 5 intentos (RNF-06, S-03), límite de peticiones en `/auth/*` (Caffeine por IP).
6. Auditoría: aspecto `@Auditable(accion, modulo, tipoEntidad, idEntidad = SpEL)` + `ContextoAuditoria.registrarCambio(campo, anterior, nuevo)`, en la misma transacción (`@Around` dentro de la transacción, `Propagation.MANDATORY` en el repositorio de auditoría), con «Sistema» para los jobs.
7. Transversal: interfaces `GestorArchivos` (disco y S3) y `Notificador` (SMTP y SES, `@Async` tras el commit), springdoc 3 con bearer, ShedLock configurado, `GeneradorExcel`.
8. ArchUnit: reglas de § 2.1 + «solo `@Transactional` en `aplicacion`» + «los controladores no devuelven entidades».
9. Frontend: Vite + React 19 + TS estricto, TanStack Router (plugin) y Query, ESLint 9, Prettier, Vitest + Testing Library + MSW, tokens CSS, `Layout`, cliente de API con refresh transparente y parseo de problem+json, `SesionProvider` (token de acceso en memoria y refresh al cargar), generación de tipos OpenAPI.
10. CI (§ 10).

**Criterios de aceptación**
- `docker compose up -d` + `.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local` (o `./mvnw …`) arranca; Flyway aplica V1 a V11 como `gesedu_migrador`; la aplicación se conecta como `gesedu_app`; Swagger UI responde en `/swagger-ui.html`.
- Con `GESEDU_ASISTENCIA_STUDENT_LATE_CUTOFF_TIME` ≥ `SCHOOL_DAY_CLOSING_TIME`, la aplicación **no arranca** y explica por qué (DA-10).
- `UPDATE auditoria.registro_auditoria …` como `gesedu_app` → `permission denied`; como dueño → excepción del trigger (RNF-08).
- `./mvnw -B verify` y `npm run lint && npm run typecheck && npm test && npm run build` pasan en CI.

**RF/RNF:** RF-01 (sin DELETE de usuarios), RF-48, RF-49, RF-50, RF-52, RNF-01, RNF-06, RNF-07, RNF-08 y la base de RNF-03.
**Pruebas:** ArchUnit; `JwtServiceTest`; `BloqueoCuentaIT` (5 fallos → bloqueado; un éxito reinicia el contador); `AuditoriaInmutableIT`; `AsistenciaPropertiesTest`; `ProblemDetailsIT` (formato y `codigo`).

### Fase 1 — Demo ampliada en localhost (después del 23/10; el alcance del 23/10 está en § 7.0)
**Corte mínimo de demo (prioridad 1, P1).** Es lo que tiene que funcionar el día de la demo aunque lo demás no llegue:
- **(a)** Inicio de sesión con los 4 roles y el Directivo, menú según el rol y bloqueo tras 5 intentos (UC-24 FA-3 y FA-4, RF-52, RNF-06, RNF-07).
- **(b)** UC-21 completo: matriz, validación de cruces en tiempo real, `@Version` y auditoría de las asignaciones (RF-39 a RF-42, RF-47).
- **(c)** Auditoría con consulta y exportación (UC-25, RF-49, RF-51, RNF-08).
- **(d)** Swagger UI con springdoc 3.x.

**Segunda prioridad (P2).** UC-02 con su correo (RF-12) y la recuperación de contraseña (UC-24 FA-5). El trabajo en P2 empieza cuando el corte mínimo está en `main`, con CI en verde y ensayado una vez completo; si P2 no llega, se omiten sus pasos del guion (§ 11) y la demo sigue siendo válida. La infraestructura que P2 reutiliza (`Notificador`, `GestorArchivos`, Mailpit) se deja lista en la fase 0 en cualquier caso.
**Tareas**
1. **[P1] Inicio de sesión real** (RF-52, UC-24 FA-3 y FA-4, RNF-06, RNF-07): pantalla `/login` fiel al wireframe (etiqueta «Correo», C-02) con el mensaje de bloqueo; layout con menú lateral y barra inferior según los permisos; `/intranet/inicio` mínimo por rol (saludo, rol y accesos del menú); cierre de sesión que revoca el refresh token.
2. **[P1]** **UC-21, Elaborar horario** (RF-39 a RF-42, RF-47): borrador por año (lo crea si no existe), perspectivas sección y docente sobre el mismo borrador, `opciones` (cursos con h asignadas/requeridas y docentes asociados con disponibilidad y ocupación), **validación en tiempo real** al elegir curso, docente y bloque (`/validaciones`), confirmación con revalidación transaccional + índices únicos + `@Version` (409 con «recarga la matriz»), alta, modificación y baja con `@Auditable`, y el panel de carga horaria de la sección (informativo, RF-43).
3. **[P1] UC-25, Auditoría**: consulta con filtros por usuario, módulo, acción y fechas, paginada; detalle con los cambios; exportación a Excel (Apache POI) y CSV. Eventos auditados en el corte mínimo: inicio de sesión fallido con bloqueo y alta, cambio o baja de asignaciones del borrador.
4. **[P1] Datos semilla** (`V1000__datos_demo.sql`, § 7.2, parte P1) y **Swagger UI** con todos los endpoints de la fase y ejemplos.
5. **[P2]** **UC-02, Registrar matrícula** (RF-04, RF-05, RF-07, RF-12, RNF-09, RNF-10): bandeja, detalle con las validaciones automáticas (matrícula activa, deudas vencidas de años anteriores, admisión), vacantes por sección, resumen de costos desde `cronograma_cobro` + `politica_cobro`, aceptación de costos, **confirmación atómica** (vacante condicional → matrícula CONFIRMADA → 11 obligaciones → auditoría) y correo al apoderado **después del commit** con el cronograma (RF-12). Rechazos FA-1 a FA-5 con su motivo y correo.
6. **[P2] Recuperación de contraseña** (UC-24 FA-5, RF-52): `/auth/recuperacion` y `/auth/recuperacion/confirmacion` (token aleatorio de 32 bytes, SHA-256, 30 min, un solo uso, misma respuesta exista o no la cuenta, revoca los refresh y se audita) y las pantallas `/recuperar-contrasena` y `/restablecer-contrasena`; el correo llega a Mailpit.
7. **[P2] Usuarios (parcial, UC-24):** lista con filtros, estado «Bloqueado · 5 intentos» y acción Desbloquear (auditada). El resto del CRUD queda en la fase 6.

**Criterios de aceptación (de la ERS)**
- **[P1]** *RF-52 y UC-24 (paso 6, FA-3, FA-4):* cada uno de los 4 usuarios (5 con el Directivo) entra con su correo y ve solo su menú. Si un alumno llama a `/api/v1/auditoria/registros` → 403 `ACCESO_DENEGADO` en < 1 s (RNF-07). Al 5.º intento fallido consecutivo → `CUENTA_BLOQUEADA` y la pantalla muestra el mensaje del wireframe; el evento «Bloqueó la cuenta…» aparece en Auditoría (y la cuenta figura «Bloqueado» en Usuarios cuando esté la pantalla P2). **FA-4 (inicio y cierre de sesión):** las credenciales incorrectas muestran un mensaje genérico y suman un intento fallido (el 5.º dispara FA-3); una cuenta inactiva o bloqueada no entra; al cerrar sesión se revoca el refresh token (el siguiente `/auth/refresco` da 401).
- **[P1]** *UC-21:* flujo básico, pasos 1–10. FA-1 (modificar) y FA-2 (eliminar) actualizan la matriz. FA-3: al cambiar de perspectiva se ve el mismo borrador. **FE-1:** un docente no disponible no se puede seleccionar o el guardado se rechaza con `DOCENTE_NO_DISPONIBLE`. **FE-2:** el cruce de docente o sección se muestra en vivo, «Prof. R. Quispe ya tiene clase en 4° B el martes de 9:15 a 10:00», y el guardado se rechaza con 409 `CONFLICTO_HORARIO`. Solo se ofrecen los docentes asociados al curso (RF-47). Dos navegadores que editan la misma versión → el segundo recibe 409 y recarga. El borrador no es visible para alumnos (RF-44).
- **[P1]** *UC-25:* flujo básico, pasos 4–6; cada operación anterior aparece con usuario, acción, módulo, entidad y fecha y hora, registrada en < 2 s (en la misma transacción) (RF-49, RNF-08); los registros no se pueden editar (RF-51).
- **[P1]** Swagger UI en `/swagger-ui.html` con los endpoints de la fase, agrupados por módulo, con bearer y ejemplos; `/v3/api-docs` responde OpenAPI 3.
- **[P2]** *UC-02:* flujo básico, pasos 1–13: tras confirmar, «3° A pasa a 3 vacantes y se generan 11 compromisos de pago», la solicitud queda APROBADA, la matrícula CONFIRMADA y el correo con el cronograma llega a Mailpit. **FA-1** no admitido → «Rechazada por No Admitido» + correo. **FA-2** matrícula activa → bloqueo + «Rechazada por Duplicidad». **FA-3** deuda vencida de un año anterior → bloqueo + «Rechazada por Deudas» con el importe. **FA-4** sin vacantes → «Rechazada sin Vacante». **FA-5** desistimiento → «Rechazada por Voluntad del Apoderado», sin vacante ni deudas. Postcondición: si se rechaza, no queda matrícula. **Dos confirmaciones simultáneas sobre la última vacante → una sola tiene éxito** (RNF-09).
- **[P2]** *UC-24 FA-5 (recuperación):* «¿Olvidaste tu contraseña?» con un correo existente o inexistente → mismo mensaje; en Mailpit llega el enlace (solo si la cuenta existe); el enlace funciona una sola vez y caduca a los 30 min. al definir la nueva contraseña el enlace queda invalidado, se revocan las sesiones abiertas de esa cuenta y el evento aparece en Auditoría (RF-49); un enlace usado o vencido se rechaza y permite solicitar otro.

**RF/RNF:** RF-04, RF-05, RF-07, RF-12, RF-39 a RF-42, RF-47, RF-49 a RF-52 (RF-43 parcial); RNF-01 (BCrypt; HTTPS queda para AWS), RNF-02 (pantallas responsive), RNF-03, RNF-06 a RNF-10.
**Pruebas:** `ValidadorConflictosTest` (cruce de docente, cruce de sección, exclusión de la asignación editada, docente no asociado o no disponible); `HorarioBorradorConcurrenciaIT` (dos inserciones en paralelo → una viola el índice; @Version → 409); `VacantesConcurrentesIT` (20 hilos sobre 1 vacante → 1 éxito, 19 `SIN_VACANTE`, vacantes = 0, nunca < 0); `ConfirmacionMatriculaIT` (rollback total si falla la generación de obligaciones, y el correo no se envía); `GeneradorObligacionesTest` (11 obligaciones, montos BigDecimal); endpoints con `RestTestClient` (401, 403, 409 problem+json); frontend: `Login.test.tsx` (mensaje de bloqueo), `PanelAsignacion.test.tsx` (muestra el conflicto que devuelve MSW), `ConfirmarMatricula.test.tsx`.
Prioridad de las pruebas: **P1** = `ValidadorConflictosTest`, `HorarioBorradorConcurrenciaIT`, `AuditoriaConsultaExportacionIT`, `Login.test.tsx` y `PanelAsignacion.test.tsx`; **P2** = `VacantesConcurrentesIT`, `ConfirmacionMatriculaIT`, `GeneradorObligacionesTest`, `ConfirmarMatricula.test.tsx` y `RecuperacionContrasenaIT` (respuesta idéntica para un correo inexistente, token caducado o reusado → 400, el correo llega por un `Notificador` falso).

### 7.2 Datos semilla de la demo (perfil `demo`)
**P1:** usuarios, año 2027 con grados, secciones, cursos, bloques, docentes (vinculaciones, asociaciones y disponibilidad), el borrador parcial y a Lucía con su matrícula 2027 ya confirmada (insertada directamente, sin pasar por UC-02). **P2:** cobros 2027, año 2026 cerrado con su deuda y las solicitudes de matrícula.
- Contraseña común `Gesedu2027!` (hash BCrypt precalculado en el SQL; solo demo y local). Correos `@demo.gesedu.pe`, que caen en Mailpit:
  `admin@` Carmen Salas Paredes (Administrador, cargo «Secretaría académica»); `directivo@` Ricardo Paredes Ortiz (Administrador, cargo «Coordinación académica»); `docente@` Rosa Quispe Mendoza (Docente de Matemática, tutora de 3° A); `alumno@` Lucía García Rojas (3° A, matrícula 2027 confirmada); `apoderado@` María Rojas Salazar (madre de Lucía). Además, una cuenta `bloqueo@` para mostrar el bloqueo sin afectar a las demás.
- Año escolar **2027** (ACTIVO; 15/02/2027–20/12/2027) con 4 bimestres; grados 1° a 5°, secciones A y B (capacidad 30). 3° A con **4 vacantes** y 2° B con **1 vacante** (para la demo de concurrencia).
- Cursos 2027 (iguales para todos los grados, RF-23): Matemática 5 h, Comunicación 5 h, Ciencia y Tecnología 4 h, Ciencias Sociales 3 h, Inglés 3 h, Educación Física 2 h, con sus competencias (C1–C4 en Matemática, etc.) y las cargas horarias por grado.
- Bloques L–V: 7:45–8:30, 8:30–9:15, 9:15–10:00, (recreo), 10:15–11:00, 11:00–11:45, 11:45–12:30, 12:30–13:15, 13:15–14:00, 14:00–14:45.
- Docentes (sin aulas): Rosa Quispe, Fernando Medina, Carlos Ríos (Matemática); Jorge Mendoza (Comunicación); Ana Huamán (CyT); Luis Castro (CCSS); Carmen Villanueva (Inglés); Óscar Cárdenas (EF). Con vinculación 2027, asociaciones con cursos y disponibilidad confirmada; Carlos Ríos no está disponible el martes de 9:15 a 10:00.
- Borrador 2027 parcialmente armado: Rosa Quispe ya tiene 4° B el martes de 9:15 a 10:00 (para provocar el cruce); 3° A con 19/22 h.
- Cobros 2027: matrícula S/ 350.00 (vence el 15/02/2027), pensiones de marzo a diciembre S/ 350.00 (vencen a fin de mes; diciembre el 15/12), penalidad del 5 %.
- Solicitudes pendientes `MAT-2027-00479…00486`, una por flujo alternativo: recurrente OK (Valeria Torres → 3° A), nuevo ingreso no admitido, recurrente con matrícula activa, recurrente con deuda VENCIDA de 2026 (se siembran el año 2026 CERRADO y su obligación vencida), dos solicitudes que compiten por la última vacante de 2° B, y una nueva que acepta costos.

### Fase 2 — Matrícula y pagos (S3–S5) · equipo Matrícula y Pagos
**Tareas:** UC-01 (asistente con subida de documentos prefirmada, validación de DNI duplicado y solicitud en trámite, código de seguimiento), configuración de cobros (RF-06), UC-03, UC-04, UC-05 (transacción única con `FOR UPDATE`), UC-06, el job **Vencimientos y penalidades** (diario a las 00:05 America/Lima, ShedLock: PENDIENTE y vencida → VENCIDA y, si aún no tiene penalidad, `monto_penalidad = round(monto_base × %, 2, HALF_UP)`; idempotente gracias a `penalidad_aplicada_en IS NULL`) (RF-09, RF-10, S-10), y la vista «mis solicitudes» del apoderado.
**Criterios de aceptación:**
- UC-01: flujo básico, pasos 1–7. FA-1, campos o documentos faltantes resaltados y envío bloqueado. FA-2, DNI inválido o «Ya existe una solicitud en trámite con este DNI para 2027».
- UC-03: flujo básico; paso 5, penalidad mostrada como «Importe base + penalidad = total». FA-1, sin obligaciones. FA-2, matrícula no vigente.
- UC-04: flujo básico; postcondición, la obligación no cambia. FA-1, datos incompletos. FA-2, la obligación ya está pagada.
- UC-05: flujo básico, pasos 1–9: boleta `B001-…`, obligación PAGADA, aviso al apoderado. FA-1, rechazo. FA-2, observación por monto insuficiente (base + penalidad). El número de operación duplicado se rechaza.
- UC-06: flujo básico; FA-1 y FA-2; la consulta no modifica nada.
**RF/RNF:** RF-02, RF-06 a RF-11, RNF-10, RNF-11, RNF-12.
**Pruebas:** `CalculoPenalidadTest` (HALF_UP, escala 2), `JobVencimientosIT` (doble ejecución = mismo resultado; ShedLock con dos instancias simuladas), `ValidacionPagoIT` (**excepción provocada tras insertar la boleta → todo revertido**; dos validaciones concurrentes de la misma obligación → una espera el `FOR UPDATE` y luego recibe `OBLIGACION_YA_PAGADA`), `NumeroOperacionUnicoIT`, endpoints con alcance (un apoderado no ve a hijos ajenos → 403).

### Fase 3 — Configuración académica y horarios (S3–S5) · equipos Académico y Horarios
**Tareas:** UC-13 (año y 4 bimestres con la restricción EXCLUDE), UC-14 (cursos y competencias, copiar del año anterior), grados, secciones, capacidad, vacantes y tutor (RF-03, RF-04), configuración de bloques, periodo de disponibilidad, vinculaciones, asociaciones y cargas (RF-46, RF-47), UC-20, UC-22 (completitud + publicación inmutable + sincronización de `asignacion_docente` (S-13)), UC-23 (alcance por rol y caché del horario vigente).
**Criterios de aceptación:**
- UC-13: flujo básico, pasos 1–8; FA-1, la duración no está entre 10 y 12 meses; FA-2, bimestres fuera del año o cruzados.
- UC-14: flujo básico, pasos 1–14; FA-1, copia de 2026.
- UC-20: flujo básico; FA-1, actualización que conserva solo la última versión; FA-2, aviso de insuficiencia («faltan al menos 4 bloques»); FE-1, periodo cerrado.
- UC-22: flujo básico; FA-1, horario incompleto o excedido que no se puede publicar; FE-1, cancelación; la versión anterior deja de ser vigente.
- UC-23: flujo básico; FA-1, «aún no publicado»; FA-2, cambio de perspectiva; el alumno y el apoderado solo ven su sección y el docente solo sus asignaciones.
**RF/RNF:** RF-03, RF-04, RF-23 a RF-25, RF-39, RF-43 a RF-47, RNF-03.
**Pruebas:** `ValidadorAnioEscolarTest`, `BimestresSuperpuestosIT` (EXCLUDE), `ValidadorCompletitudTest`, `PublicacionHorarioIT` (índice único vigente, inmutabilidad, numeración de versiones), `DisponibilidadInsuficienteTest`, frontend `MatrizHorario.test.tsx` (en móvil, vista por día).

### Fase 4 — Asistencia (S5–S6) · equipo Asistencia (I10 + I2 desde la fase 2; UC-07 y UC-12 necesitan un horario publicado de la fase 3)
**Tareas:** UC-07, UC-08, UC-09, UC-10 + UC-11 (cálculo bajo demanda, Excel y PDF), consulta de docentes por día, **job de cierre diario** UC-12 (se ejecuta cada minuto y actúa si `now ≥ SchoolDayClosingTime` y la fecha no fue procesada; ShedLock; crea FALTA solo para quien falta; tolerante a reintentos; con FA-4, si falta el horario, registra el fallo y no inventa faltas) y el resumen de asistencia del inicio del alumno.
**Criterios de aceptación:**
- UC-07: pasos 1–8 (PUNTUAL si la marcación es ≤ inicio del primer bloque + TeacherLateThresholdMinutes; si no, TARDANZA); FA-1, ya registrada (también ante solicitudes simultáneas); FA-2, sin clases o sin horario publicado; FA-3, día no lectivo o jornada cerrada.
- UC-08: pasos 1–8, lote atómico; el tutor registra **PUNTUAL** (presente al pasar lista), **TARDANZA** (llegó después de pasar lista pero antes de confirmar el registro) o **FALTA**, sin umbral automático (ERS § 2.1.4, RF-14; ver C-29); FA-1 a FA-5. Un alumno ya registrado como FALTA que llega después se corrige con UC-09.
- UC-09: pasos 1–5; FA-1, plazo excedido; FA-2, registro no elegible; FA-3, alumno fuera de la sección del tutor.
- UC-10: FA-1, periodo en curso con fecha de corte; FA-2, sin registros; FA-3, alcance o periodo inválido; FA-4, calendario no disponible.
- UC-11: % = 100 × (P + T) / registrados, redondeado a 2 decimales; «No disponible» si no hay registros; los días sin registro se muestran aparte.
- UC-12: FA-1 a FA-4.
**RF/RNF:** RF-13 a RF-21, RNF-03.
**Pruebas:** `ClasificadorAsistenciaDocenteTest` (límites inclusivos, `Clock` fijo, zona America/Lima), `CalculoResumenMensualTest`, `CierreDiarioIT` (idempotencia y concurrencia, unicidad docente-fecha), `PaseListaConcurrenteIT` (incluye un lote con los tres estados y el rechazo de un estado inválido), `PlazoTardanzaTest`.

### Fase 5 — Evaluación (S5–S7) · equipo Evaluación
**Tareas:** UC-15 (rejilla, upsert de RegistroLogro auditado con valor anterior y nuevo, RF-49), UC-16 (regla + pesos por bimestre), UC-17, UC-18 (avance, bloqueo si está incompleto, consolidación idempotente, libretas; el último bimestre dispara el cálculo anual (C-26)), UC-19 (cursos, competencias con nivel esperado, notas por bimestre, PDF del informe y libreta con OpenPDF), FA-1 para apoderados con varios hijos, y el inicio del alumno (notas del bimestre).
**Motor (DA-09):** interfaz `EstrategiaConsolidacion { TipoRegla tipo(); NivelLogro consolidar(List<LogroPonderado>); }`; `ConsolidacionService` arma un `Map<TipoRegla, EstrategiaConsolidacion>` con todas las implementaciones que inyecta Spring. **PromedioPonderado**: Σ(valor × peso) / Σ peso en BigDecimal y redondeo HALF_UP al entero → `NivelLogro.desdePromedio` (2,5 → A). **Moda**: el más frecuente; si hay empate, el del registro más reciente (S-04). **UltimoRegistro**: el de `fecha_registro` máxima.
**Criterios de aceptación:**
- UC-15: pasos 1–7; FA-1, nivel inválido; FA-2, registro incompleto con aviso «2 alumnos con competencias sin calificar».
- UC-16: flujo básico; FA-1, cambiar la regla; FA-2, por defecto PROMEDIO_PONDERADO.
- UC-17: flujo básico.
- UC-18: flujo básico; FA-1, consolidado final; FA-2, incompleto con la lista de docentes pendientes.
- UC-19: flujo básico; FA-1, varios hijos; FA-2, libreta final.
- RF-38: 2,5 → A; pesos iguales por defecto y ajustables por bimestre.
**RF/RNF:** RF-22, RF-26 a RF-38, RNF-03.
**Pruebas:** `PromedioPonderadoStrategyTest` (tabla de casos: 3,5 → AD, 2,5 → A, 1,49 → C, pesos distintos), `ModaStrategyTest` (empate → el más reciente), `UltimoRegistroStrategyTest`, `NivelLogroTest`, `ConsolidacionIdempotenteIT` (reejecutar no duplica), `LibretaPdfTest` (genera un PDF válido).

### Fase 6 — Seguridad completa, endurecimiento y cierre (S6–S8) · equipo Plataforma + QA
**Tareas:** UC-24 completo (crear, modificar, activar y desactivar; duplicados por DNI o correo, FA-1; vínculo académico obligatorio, FA-2, con el puerto SPI; heredar los permisos del rol; invalidar caché y tokens al desactivar), gestión de roles y permisos (RF-48), revisión responsive y de accesibilidad (RNF-02, RNF-04: Chrome, Firefox, Safari y Edge), prueba de carga k6 con 200 apoderados en UC-01 (RNF-12; `grafana/k6` en Docker), `docs/despliegue/*`, revisión de seguridad y manual breve por perfil.
**Tarea adicional (reportes de meses pasados, UC-10/UC-11, RF-16/RF-17; origen: limitación de las tareas 4.4–4.5):** hoy un reporte de un mes pasado se calcula con el universo de *hoy*: las clases de los docentes salen de la versión **vigente** del horario y los alumnos de cada sección son los matriculados **hoy**. Hay que (a) calcular los días esperados de cada docente con la **versión del horario que estaba vigente en cada fecha** (`horarios.horario_publicado`: `numero_version` y `fecha_hora_publicacion`; las versiones publicadas son inmutables, así que basta resolver «versión vigente el día D») y la misma regla en la consulta por día de UC-12; y (b) listar a los alumnos con **la sección que tenían en esa fecha**. **Limitación conocida:** `matricula.matricula` guarda solo la `seccion_id` actual y no hay historial de cambios de sección (RF-05, tarea 2.9); el único rastro es el registro de auditoría `CAMBIAR_SECCION_MATRICULA` (sección y vacantes antes/después). Opciones: crear una tabla de historial (`V12+`, migración hacia adelante) que se llene desde ahora, o reconstruirla desde la auditoría; mientras no exista, los reportes de meses con traslados atribuyen al alumno a su sección actual y el reporte debe decirlo. Pruebas: `ReporteAsistenciaIT` con dos versiones del horario publicadas en fechas distintas (cada mes usa la suya) y, si hay historial, con un alumno trasladado a mitad de mes.
**Criterios de aceptación:**
- UC-24: pasos 1–6; FA-1 a FA-3 (FA-4 y FA-5 ya se entregaron en la fase 1; aquí solo se prueban de nuevo con cuentas creadas desde la pantalla de Usuarios). Desactivar una cuenta revoca sus sesiones de inmediato (el siguiente request da 401).
- La prueba k6 cumple p95 < 2 s (RNF-03) con 200 usuarios virtuales en local o QA.
**Pruebas:** `GestionUsuariosIT`, `CachePermisosIT` (cambiar un permiso de un rol se refleja en el siguiente request), Testing Library de `DialogoNuevoUsuario`.

### Cobertura de los 25 UC por fase
F1: UC-02, UC-21, UC-25 y UC-24 (inicio de sesión, bloqueo, desbloqueo) · F2: UC-01, UC-03 a UC-06 · F3: UC-13, UC-14, UC-20, UC-22, UC-23 · F4: UC-07 a UC-12 · F5: UC-15 a UC-19 · F6: UC-24 completo.

---

## 8. Reparto para 10 personas

| Equipo | Integrantes | Fase 0–1 | Fases 2+ |
|---|---|---|---|
| Plataforma backend | 2 (I1, I2) | Seguridad (inicio de sesión, JWT, RBAC, bloqueo), auditoría (aspecto + UC-25), errores, ArchUnit, CI; la recuperación de contraseña es P2 de la fase 1 | I1: UC-24 completo, roles y permisos, k6, despliegue. **I2 apoya a Asistencia junto con I10 desde la fase 2** |
| Plataforma frontend | 1 (I3) | Proyecto Vite, router, cliente de API, sesión, layout y menú, login, recuperación, pantallas de Auditoría y Usuarios | Componentes compartidos, responsive, accesibilidad, apoyo a los equipos |
| Matrícula y Pagos | 2 (I4, I5) | UC-02 completo (backend y frontend) + semilla de matrícula y cobros | UC-01, UC-03 a UC-06, job de penalidades |
| Horarios | 2 (I6, I7) | UC-21 completo (validador, matriz) + semilla de horarios | UC-20, UC-22, UC-23 y configuración de horarios |
| Académico y Evaluación | 2 (I8, I9) | V3 y V7, semilla académica; apoyo a la semilla; pruebas de dominio del motor (sin UI) | UC-13, UC-14, UC-15 a UC-19 |
| Asistencia + QA | 1 (I10) → 2 desde la fase 2 (I10 + I2) | `AsistenciaProperties`, Docker, `.run/`, guion de la demo, pruebas de humo | UC-07 a UC-12 (arranque anticipado en la fase 2 con I2: UC-08, UC-09 y los reportes de alumnos; UC-07, UC-12 y los reportes de docentes esperan al horario publicado de la fase 3) |

**Paralelización y dependencias:** el esquema completo (V1 a V11) y `compartido` se crean en la fase 0, lo que evita que los equipos se bloqueen por las migraciones; mientras no haya despliegue compartido se pueden editar (§ 4.4, S-26). Desde la fase 1 cada equipo trabaja en su paquete, coordinándose solo por interfaces de `aplicacion` acordadas en un PR corto. Contratos tempranos que hay que publicar: `academico.aplicacion.SeccionesService` (vacantes, alumnos), `horarios.aplicacion.ConsultaHorarioService.primerBloque(docenteId, fecha)` (para Asistencia), `matricula.aplicacion.NominaService.alumnosDeSeccion(...)` (para Asistencia y Evaluación) y `pagos.aplicacion.ObligacionesService.generarParaMatricula / tieneDeudasVencidas` (para Matrícula). Las semillas son **un archivo por módulo**, que se concatenan en V1000 para evitar conflictos. Convención de ramas: `feat/<modulo>-<uc>`, PR a `main` con CI en verde y una revisión de otro equipo.

---

## 9. Estrategia de pruebas

1. **Unitarias del dominio (sin Spring):** estrategias de consolidación y `NivelLogro` (RF-38, S-04); `ValidadorConflictos` (RF-41, RF-47); `ValidadorCompletitud` (RF-43); `ClasificadorAsistenciaDocente` y plazo de tardanza (RF-13, RF-19, con `Clock` fijo); `CalculoResumenMensual` (RF-16); `CalculoPenalidad` y `Dinero` (RF-10, RNF-11); `ValidadorAnioEscolar` (UC-13).
2. **Integración con Testcontainers + `postgres:18`** (`@ServiceConnection` + script de roles; Flyway como migrador y la aplicación como `gesedu_app`, para probar los permisos reales). Un contenedor por JVM (singleton) para mantener la duración baja. Casos clave: **vacantes concurrentes** (RNF-09), **reversión de pagos y `FOR UPDATE`** (RNF-10), **inmutabilidad de la auditoría** (UPDATE, DELETE y TRUNCATE rechazados; INSERT y SELECT permitidos) (RNF-08), índices anticruce y `@Version` (RF-41), jobs idempotentes con ShedLock (UC-12, RF-09, RF-10), restricción EXCLUDE de bimestres y unicidad de asistencia.
3. **Endpoints:** `@SpringBootTest(webEnvironment=RANDOM_PORT)` + `RestTestClient` para los flujos completos con JWT; slices `@WebMvcTest` + `spring-security-test` para la matriz de permisos (cada endpoint × rol → 2xx/403) y para el formato problem+json.
4. **Arquitectura:** ArchUnit (§ 2.1).
5. **Configuración:** `ApplicationContextRunner` para DA-10.
6. **Frontend:** Vitest + Testing Library + MSW: formularios (zod), guardias de ruta por permiso, `cliente.ts` (refresh tras 401, parseo de problem+json), componentes de matriz y rejilla. Los tipos generados desde OpenAPI se comprueban con `tsc`.
7. **Carga:** k6 (fase 6, RNF-12).
Cobertura orientativa: dominio ≥ 80 %; sin umbral global obligatorio (S-24).

## 10. CI en GitHub Actions (`.github/workflows/ci.yml`)
- Disparadores: `pull_request` y `push` a `main`.
- **Job backend** (`ubuntu-latest`, que ya trae Docker para Testcontainers): `actions/checkout@v4` → `actions/setup-java@v4` (`distribution: corretto`, `java-version: 25`, `cache: maven`) → `cd backend && ./mvnw -B verify`. Publica los reportes de Surefire y Failsafe como artefactos.
- **Job frontend:** `actions/setup-node@v4` (`node-version: 22`, `cache: npm`, `cache-dependency-path: frontend/package-lock.json`) → `npm ci` → `npm run lint` → `npm run typecheck` (`tsc -b --noEmit`) → `npm run test -- --run` → `npm run build`.
- Los dos jobs corren en paralelo y la protección de `main` exige ambos.

## 11. Guion de la demo en localhost

**Preparación (una vez)**
```powershell
# PowerShell (Windows 11)
Copy-Item .env.example .env
docker compose up -d postgres mailpit
cd backend; .\mvnw.cmd -B verify -DskipTests; cd ..
cd frontend; npm ci; cd ..
```
```bash
# bash
cp .env.example .env
docker compose up -d postgres mailpit
(cd backend && ./mvnw -B verify -DskipTests)
(cd frontend && npm ci)
```
**Arranque**
```powershell
cd backend; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local,demo"   # terminal 1
cd frontend; npm run dev                                                          # terminal 2
```
```bash
(cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local,demo)       # terminal 1
(cd frontend && npm run dev)                                                       # terminal 2
```
En IntelliJ: ejecutar `Docker Compose`, luego `Backend (demo)`, luego `Frontend dev` desde la lista de configuraciones (`.run/`). Para reiniciar los datos: `.\scripts\reiniciar-bd.ps1` o `./scripts/reiniciar-bd.sh`.
URL: SPA http://localhost:5173 · Swagger http://localhost:8080/swagger-ui.html · Mailpit http://localhost:8025.

**Pasos en clase (≈ 15 min; P1 ≈ 10 min)**
*Corte mínimo (P1)*
1. Arquitectura: mostrar `docker compose ps` (postgres:18 y mailpit), Swagger UI agrupado por módulo y la prueba ArchUnit en verde.
2. Inicio de sesión con los 4 roles + el Directivo: menús distintos. Desde el alumno, llamar a `/api/v1/auditoria/registros` → 403 (se muestra en DevTools; la ruta tampoco está en su menú).
3. Bloqueo: 5 contraseñas incorrectas con `bloqueo@demo.gesedu.pe` → mensaje del wireframe; el evento se muestra en Auditoría (paso 5).
4. UC-21: Elaborar horario 2027 → 3° A → martes de 9:15 a 10:00 → Matemática → el panel marca a Rosa Quispe «tiene clase en 4° B» y a Carlos Ríos «No disponible» → intentar guardar → 409 con la causa. Elegir a Fernando Medina → se guarda. Cambiar a la perspectiva Docente → el mismo borrador. Abrir dos pestañas → el segundo guardado recibe 409 por versión.
5. UC-25: Auditoría → filtrar por usuario, módulo Horarios y fechas → ver el registro con sus cambios (también el bloqueo del paso 3) → Exportar a Excel. Mostrar en psql que `DELETE FROM auditoria.registro_auditoria` falla.

*Segunda prioridad (P2); se omite si no está lista*
6. Recuperación: «¿Olvidaste tu contraseña?» → Mailpit → enlace → nueva contraseña → iniciar sesión. Reusar el enlace → error. En Usuarios, desbloquear la cuenta `bloqueo@`.
7. UC-02: bandeja → MAT-2027-00482 (Valeria Torres) → validaciones en verde → 3° A (4 vacantes) → resumen de costos (S/ 3,850.00) → aceptar → confirmar → «3 vacantes, 11 compromisos» → Mailpit muestra el correo con el cronograma. Mostrar FA-3 (rechazo por deudas) con otra solicitud.
8. RNF-09 en vivo: ejecutar `VacantesConcurrentesIT` (o dos pestañas confirmando las solicitudes que compiten por 2° B) → una sola tiene éxito.

## 12. Preparación para AWS (sin infraestructura todavía)
- **Backend:** JAR ejecutable `gesedu-api.jar` (`finalName`) para Elastic Beanstalk con plataforma Corretto 25 / Amazon Linux 2023. Puerto vía `SERVER_PORT=5000` (el proxy de EB). Health check en `/actuator/health`. Perfil `aws` con `SPRING_PROFILES_ACTIVE=aws`. Toda la configuración por variables de entorno o Parameter Store y Secrets Manager (credenciales de BD, `GESEDU_JWT_SECRET`, parámetros de asistencia). Sin estado: no hay sesiones HTTP y los locks de ShedLock están en la BD. `server.forward-headers-strategy=framework` detrás del ALB y CloudFront. Logs JSON hacia CloudWatch.
- **RDS for PostgreSQL 18:** script `docs/despliegue/rds-bootstrap.sql` (crear la BD y los roles `gesedu_migrador` y `gesedu_app`) que se ejecuta una vez con el usuario maestro. Flyway se ejecuta al arrancar como migrador. JDBC con `sslmode=verify-full`.
- **S3 + CloudFront (SPA):** `npm run build` → `dist/` al bucket `gesedu-web` (OAC). Comportamientos de CloudFront: `/*` → S3 (con fallback a `index.html` para las rutas de la SPA, vía la respuesta a 403/404) y `/api/*` → ALB sin caché y reenviando `Authorization` y las cookies. Como comparten origen, no hace falta CORS entre la SPA y la API.
- **Bucket `gesedu-documentos`:** privado, SSE-S3, versionado. Regla CORS en `docs/despliegue/s3-cors.json`:
  ```json
  [{"AllowedOrigins":["https://gesedu.<colegio>.edu.pe"],
    "AllowedMethods":["PUT","GET"],
    "AllowedHeaders":["Content-Type","Content-MD5","x-amz-*"],
    "ExposeHeaders":["ETag"],"MaxAgeSeconds":3000}]
  ```
  Las URL prefirmadas duran 5 min, con `Content-Type` y tamaño máximo validados (PDF, JPG y PNG ≤ 5 MB, S-19).
- **SES:** dominio verificado y `GESEDU_MAIL_FROM`; `NotificadorSes` (AWS SDK 2.x) con el perfil `aws`.
- **Futuro CI/CD** (no ahora): job de despliegue en `main` → `aws s3 sync` + invalidación de CloudFront + `eb deploy`.

## 13. Contradicciones e inconsistencias encontradas

Prioridad aplicada: arquitectura v1.0 > ERS > catálogo v4 > wireframes > resto.

| ID | Documentos | Diferencia | Resolución |
|---|---|---|---|
| C-01 | Arquitectura § 10.10 vs. prompt | Dos repositorios vs. monorepo | **Resuelta (22/10/2026).** Monorepo (decisión del usuario); la § 10.10 de la arquitectura ya lo describe, con CI de dos jobs (backend con ArchUnit y frontend) |
| C-02 | Wireframe /login vs. RF-52 | «Usuario (DNI o correo)» vs. correo | **Solo correo** (decisión del usuario); cambia la etiqueta |
| C-03 | Modelo Conceptual (Aula, «aloja»), wireframes (Aula 204, Lab. 1, «Por aula», campo y cruce de aula), visión, procesos vs. ERS § 2.3 y el prompt | Gestión de aulas | **Sin Aula**: no hay entidad, perspectiva ni campo; la UI omite el aula |
| C-04 | Visión (validación por IP o red), wireframe «✓ Conectado a la red del colegio» vs. ERS § 2.3 y § 3.5.1, arquitectura § 4.8 | Validación de red en la marcación docente | No se valida; se quita la insignia |
| C-05 | Visión, procesos, wireframe «Consolidación mensual» y «consolidado el 01/09» vs. ERS UC-10 y UC-11, RF-16 | Consolidación mensual guardada | Cálculo bajo demanda; la pantalla de consolidación no se implementa |
| C-06 | Wireframe de reportes («% sobre el total de días lectivos») vs. ERS UC-11, paso 5 | Denominador del % | Días registrados (ERS); los días sin registro van aparte |
| C-07 | Wireframe registro del día («se guardará el estado anterior, la hora y el docente», «Última modificación … Prof.») vs. ERS UC-09 (sin historial) | Historial de cambios de asistencia | Sin historial en el módulo; el evento sí queda en Auditoría (como muestra el wireframe de auditoría) |
| C-08 | RF-14 (estado inicial Puntual, Tardanza o Falta) vs. ERS UC-08 y wireframe (Puntual/Falta) | Tardanza en el pase de lista | **Resuelta (22/10/2026), con una inconsistencia residual (C-29).** Se mantiene RF-14: el pase de lista admite PUNTUAL, TARDANZA o FALTA (ERS § 2.1.4); UC-09 sigue cambiando FALTA → TARDANZA después |
| C-09 | ERS (StudentAttendance, TeacherAttendance, TeacherRole, PUNCTUAL/LATE/ABSENT, checkInAt) vs. Modelo Conceptual y arquitectura (AsistenciaAlumno, AsistenciaDocente, PUNTUAL/TARDANZA/FALTA, horaRegistro) | Idioma de los nombres | Español (arquitectura Tabla 8); en el código se documenta la correspondencia |
| C-10 | Curso.horasLectivas (modelo académico, UC-14, wireframe) vs. CargaHorariaCurso por grado (modelo de horarios, RF-43) | Origen de la carga horaria | `carga_horaria_curso` por grado, inicializada desde `horas_lectivas` |
| C-11 | ERS § 2.1.3 y procesos («pensiones generadas conforme transcurren las fechas») vs. UC-02, paso 12, arquitectura § 4.6 y wireframe («11 compromisos») | Momento en que se generan las pensiones | Todas al confirmar la matrícula |
| C-12 | Arquitectura § 9.2 y RF-27 (regla aplicada a los registros *del bimestre*) vs. wireframes p. 22–24 (un nivel por competencia y bimestre; regla aplicada *entre bimestres*) | Granularidad de RegistroLogro | Supuesto S-12 + pregunta P-02 |
| C-13 | Wireframes («Menú del directivo» distinto) vs. ERS y arquitectura (Directivo = Administrador) | ¿Rol aparte? | Un solo rol ADMINISTRADOR; el cargo se muestra y el menú sale de los permisos |
| C-14 | Visión v3 vs. catálogo v4 | Numeración antigua (RF-01 a RF-50: RF-12 = clasificación de asistencia, RF-37 = conversión, RF-38 = matriz…), «moda» como única regla, RF-23 (competencias por el docente) | Se ignora la Visión en lo funcional; conviene actualizarla |
| C-15 | Arquitectura, Tabla 9 (LocalStack para S3 y SES) vs. prompt (Mailpit) y la licencia de LocalStack (token obligatorio desde la release 2026.03) | Entorno local | **Resuelta (22/10/2026).** Mailpit + `GestorArchivosDisco` en desarrollo; LocalStack opcional por el token (Tabla 9 de la arquitectura actualizada) |
| C-16 | Arquitectura § 9.1 «ArchUnit o Spring Modulith» | — | ArchUnit |
| C-17 | Arquitectura, Tabla 7 (Notificaciones atiende UC-01) vs. ERS UC-01 (sin correo) y matriz (RF-12 solo en UC-02) | Correo en UC-01 | **Resuelta (22/10/2026).** La Tabla 7 ahora asigna Notificaciones a UC-02 y UC-24 (RF-12, RF-52): correo del resultado de matrícula y correo de recuperación de contraseña; UC-01 no envía correo |
| C-18 | Catálogo (RF-03 en el módulo Matrícula) vs. arquitectura, Tabla 7 (Configuración Académica) | Módulo de RF-03 | Paquete `academico` |
| C-19 | UC-24, paso 2, cita RF-02 (datos de estudiante y apoderado) para cuentas duplicadas; UC-24 no describe el inicio de sesión ni la recuperación aunque la matriz le asigna RF-52 | Trazabilidad | **Resuelta (22/10/2026).** UC-24 incluye FA-4 (inicio y cierre de sesión) y FA-5 (recuperación); el paso 2 cita RF-01. Quedan detalles en C-30 y C-31 |
| C-20 | ERS § 2.1.7 nombra subcasos sin ID («Asignar Rol y Permisos», «Filtrar Registros…») | — | Se incluyen en UC-24 y UC-25 |
| C-21 | Modelo (documentoIdentidad solo en Alumno y Apoderado) vs. wireframe Usuarios (DNI de todos, «Ya existe una cuenta… DNI») | DNI de docentes y administradores | `usuario.documento_identidad` único para todos (no se duplica en alumno ni apoderado) |
| C-22 | Modelo Conceptual vs. RF y wireframes | Faltan: Seccion.capacidad (RF-04), costos y políticas (RF-06), documentos de la solicitud, motivo de rechazo, fechaNacimiento, parentesco, medio de pago, nivel esperado de la competencia (RF-34), pesos por bimestre (RF-38), tokens técnicos y `version` del borrador | Se agregan (§ 4.1); conviene actualizar el modelo a v2 |
| C-23 | Catálogo v4 | El historial dice «Versión inicial del Documento de Visión»; RNF-06 no tiene prioridad | **Resuelta (22/10/2026).** El historial del catálogo está corregido y RNF-06 tiene prioridad Alta |
| C-24 | Wireframe (tolerancia de 5 min) vs. ERS (30 min «solo ejemplo») | Valor del umbral | Configurable; la demo usa 5 / 08:30 / 15:30, tomados de los wireframes |
| C-25 | Wireframes (p. 2, 7, 13, 31: año escolar 2026) vs. prompt (semilla 2027) | Año | Semilla 2027 + 2026 cerrado para el caso de deuda |
| C-26 | UC-17 (el docente calcula las finales) vs. UC-18 FA-1 (el directivo, en el último bimestre, calcula las finales) y FA-2 (exige que el docente las haya calculado) | ¿Quién calcula? | Servicio idempotente; UC-18 en el último bimestre recalcula todo y bloquea si hay faltantes |
| C-27 | ERS § 2.1.1 «Personal Administrativo» de procesos | — | Es un Administrador (la ERS lo asume así) |
| C-28 | Numeración antigua en la arquitectura o la ERS | Se revisaron todas las citas | **No se encontraron citas con la numeración v3** en la arquitectura, la ERS ni la matriz; solo en la Visión (C-14) |
| C-29 | ERS § 2.1.4 (el pase de lista admite LATE) vs. ERS UC-08 (paso 5 «PUNCTUAL … ABSENT», FA-2, Puntos de Extensión «alumno inicialmente ausente») y wireframe p. 15 (solo Puntual y Falta) | El flujo de UC-08 y el wireframe no reflejan la corrección de C-08 | **Resuelta (22/10/2026).** UC-08 (paso 5, FA-2 y Puntos de Extensión) ya admite LATE en el pase de lista. El wireframe p. 15 no se actualizará; la pantalla sigue la ERS y agrega la opción Tardanza |
| C-30 | ERS UC-24 (FA-4 cita RF-52 y RF-50; FA-5 cita RF-52, RF-12 y RF-49) vs. matriz RF×UC (RF-12 solo en UC-02; RF-49 solo en UC-25; RF-02 sigue marcado en UC-24 aunque el paso 2 ahora cita RF-01) | Trazabilidad incompleta. Además, RF-12 es el correo del resultado de matrícula; el de recuperación es RF-52 | **Resuelta (22/10/2026).** FA-5 de UC-24 cita solo RF-52 y la matriz asigna RF-01, RF-48, RF-49, RF-50 y RF-52 a UC-24 (ya no RF-02) |
| C-31 | ERS UC-24: el actor ya incluye a «cualquier rol», pero el objetivo y las precondiciones siguen siendo solo del administrador («El administrador cuenta con credenciales…») y FA-5 dice «vigencia limitada» sin valor | Redacción incompleta | **Resuelta (22/10/2026).** El objetivo y las precondiciones de UC-24 incluyen a cualquier usuario y el enlace de recuperación vale 30 minutos, igual que en la arquitectura |
| C-32 | Plan § 3.2 (Vite 7, TS 5.9, ESLint 9 + plugins, Vitest 3/4, MSW 2) vs. repositorio (Vite 8, TS 6, oxlint) | Versiones del frontend | **Resuelta (28/10/2026).** Se mantienen las del repositorio (ya en `main`); `lint` sigue siendo oxlint; `strict` y `noUncheckedIndexedAccess` activos. Vitest 5 y MSW 3 por compatibilidad con Vite 8 |
| C-33 | CLAUDE.md (`docker compose up -d postgres mailpit`) vs. Mailpit con `profiles: ["correo"]` | Mailpit no arrancaba | **Resuelta.** Mailpit sin perfil de compose |
| C-34 | CLAUDE.md (`gesedu.asistencia.time-zone`) vs. código (`gesedu.zona-horaria`) | Nombre de la propiedad de zona horaria | **Resuelta.** Unificada en `gesedu.asistencia.time-zone` |
| C-35 | Plan § 4.1 (`cambio_auditoria.registro_id`) vs. V9 (`registro_auditoria_id`) | Nombre de columna | Se mantiene el código |
| C-36 | Encargo (tag `demo-23-10` en `main`) vs. repositorio (sin tag) | Tag inexistente | **Resuelta.** Creado localmente sobre `8f876c6` (sin push) |
| C-37 | ERS UC-02 (pasos 3–4, FA-2, FA-3) vs. plan fase 1 tarea 5 | Las validaciones dependen del tipo de estudiante (nuevo ingreso: admisión; recurrente: matrícula activa y deudas) y el administrador determina el tipo | Se ramifica por `tipo_estudiante`, editable por el administrador al evaluar |
| C-38 | ERS UC-13, UC-14 y UC-18 («Administrador (Directivo)») vs. § 5.3 | Permiso por cargo | Se mantiene RBAC por permiso (C-13); el `cargo` solo se muestra |
| C-39 | ERS UC-07 FA-1, UC-08 FA-5, UC-09 FA-2 vs. § 5.2 (409 `ASISTENCIA_YA_REGISTRADA`) | La ERS muestra el registro existente, no un error | 409 con el registro existente en el cuerpo |
| C-40 | ERS § 2.3 (UC-22 depende de la disponibilidad recolectada) vs. fase 3 | Prerrequisito no validado | Advertencia en la completitud, sin bloquear |
| C-41 | ERS UC-05 («informa al apoderado») vs. arquitectura § 6.2 | Canal del aviso sin definir | Correo con `Notificador` y estado visible en pantalla |
| C-42 | § 5.4 (`HORARIO_NO_PUBLICADO`) y UC-10 FA-2 a FA-4 vs. § 5.2 | Códigos ausentes del catálogo | **Resuelta.** Agregados en `CodigoError` y `docs/api/errores.md` (`PERIODO_INVALIDO`, `CALENDARIO_NO_DISPONIBLE`, `LIMITE_PETICIONES_EXCEDIDO`) |
| C-43 | Catálogo v4 RF-02, RF-05 y RF-07 («actualizar») vs. plan sin endpoints | Alcance | **Resuelta (decisión del usuario).** Dentro del alcance, en la fase 2 (tareas 2.8 a 2.10), solo Administrador y con `@Auditable` |
| C-44 | `CLAUDE.md` y plan § 2.1 (grafo: `horarios → {academico, seguridad, evaluacion}`) vs. UC-23 (el alumno y el apoderado ven «su sección») | Dependencia faltante | **Resuelta (fase 3).** `horarios.aplicacion` usa `matricula.aplicacion.ConsultaMatriculaService.seccionesDeMiAlcance()`. El grafo sigue acíclico (`matricula` no depende de `horarios` ni de `evaluacion`; `evaluacion → matricula` ya existía). ArchUnit solo prohíbe ciclos, así que no hizo falta un puerto SPI |
| C-45 | Plan § 4.1 (`asignacion_docente` única por curso y sección) vs. UC-21 (varios docentes pueden dictar bloques del mismo curso en una sección) | Un responsable por curso | **Resuelta (fase 3, S-13).** Al publicar responde el docente con más bloques; en empate, el que ya era responsable y, si no, el de id menor (`ResponsablesPorCurso`) |
| C-46 | ERS UC-20 FA-2 («carga horaria asociada al docente») y RF-46 vs. modelo (no hay carga por docente) | Origen de la carga del docente | **Resuelta (fase 3).** `vinculacion_docente.carga_semanal_referencia`, que el administrador fija al vincular al docente; es lo que compara la advertencia «Faltan al menos N bloques» (wireframe p. 28). Es informativa: no impide confirmar |
| C-47 | ERS UC-13 (crea el año y sus bimestres) vs. el resto de la ERS (hay un año «vigente» o «activo») | No hay caso de uso que active un año | **Resuelta (fase 6, tarea 6.1b; decisión del usuario).** La activación es una acción aparte y una extensión de UC-13: `PATCH /anios-escolares/{anio}/activacion` (permiso `ACADEMICO_CONFIGURAR`, `@Auditable` `ACTIVAR_ANIO_ESCOLAR`) activa un año PLANIFICADO y cierra el ACTIVO anterior en una sola transacción. La SPA obtiene el año activo de la API y se elimina la constante `ANIO_TRABAJO` (S-42) |
| C-48 | Plan § 5.2 (endpoints de asistencia) vs. UC-08 paso 3 («la sección del tutor») y el menú de la SPA | Ningún endpoint dice cuál es la sección que tiene a cargo el tutor, y la SPA solo recibe el código por la URL (`/asistencia/$seccion/registrar`) | **Resuelta (fase 4, tarea 4.2).** Se agrega `GET /asistencia-alumnos/mi-seccion` (código, nombre y cantidad de alumnos; 403 si no es tutor, UC-08 FA-3); la opción del menú «Asistencia de mi sección» entra por ahí. Pendiente de reflejar en la tabla del plan § 5.1 |
| C-49 | Plan § 5.1 (`/reportes/asistencia?ambito&mes&anio…`, con `mes` y `anio` obligatorios) vs. «nunca se usa la hora del dispositivo» | La SPA proponía como mes por omisión el de su propio equipo (en la demo, noviembre de 2026 frente a marzo de 2027 del servidor) | **Resuelta (fase 4, tarea 4.6).** `mes` y `anio` son opcionales y van juntos: sin ninguno, el servidor usa su mes actual; la SPA ofrece «Mes actual» y solo manda un periodo si el usuario lo elige |
| C-50 | ERS UC-10 FA-2 («no se genera el archivo») vs. la vista previa en pantalla del wireframe p. 17 | ¿Qué responde el servidor cuando no hay registros? | **Resuelta (fase 4, tarea 4.5).** 422 `SIN_REGISTROS_ASISTENCIA` en JSON, Excel y PDF: ninguno entrega un reporte vacío ni porcentajes |
| C-51 | Wireframe p. 13 («Últimos 5 días lectivos» y «Aula 204» en la primera clase) vs. modelo (sin calendario con feriados ni entidad Aula) | No se puede listar «días lectivos» sin registro ni mostrar aula | **Resuelta (fase 4).** «Mi asistencia» muestra hoy y los últimos días **con registro** (S-62); la primera clase se muestra con su hora (sin curso ni aula, C-22) |
| C-52 | ERS UC-12 / UC-07 («calendario académico disponible») y la demo del plan (año 2027) vs. la fecha real de la demo (2026) | Sin un día lectivo real no se puede mostrar la marcación ni el cierre | **Resuelta (fase 4, tarea 4.6).** El perfil `demo` (solo ese) usa un reloj propio que arranca el lunes 15/03/2027 a las 7:40 y avanza en tiempo real (`GESEDU_DEMO_RELOJ_INICIO`); en producción el reloj es siempre el del sistema en la zona institucional. La semilla `V1003` deja el periodo de disponibilidad abierto hasta el 30/06/2027 para que el reloj de la demo no lo cierre |
| C-53 | Arquitectura § 9.2 / RF-27 (la regla se aplica a los registros *del bimestre*, varios por competencia) vs. wireframes p. 22–24 (un nivel por competencia y bimestre; la regla actúa *entre* bimestres) | Granularidad de `RegistroLogro` y alcance de las estrategias | **Resuelta (fase 5, P-02).** Un único logro por (alumno, competencia, bimestre), como los wireframes (S-12, S-66): la nota bimestral es ese logro pasado por la regla del curso y la **anual** aplica la regla a las notas bimestrales con los pesos por bimestre (RF-38). `ULTIMO_REGISTRO` usa el bimestre de mayor número y el empate de la moda lo gana el más reciente (S-04) |
| C-54 | RF-27 («calcular automáticamente, al cierre de cada bimestre») y UC-18 (el administrador selecciona y ejecuta «Consolidar bimestre») vs. wireframe p. 25 (botón habilitado solo al 100 %) y ERS UC-18 FA-2 (lista de docentes pendientes) | ¿La consolidación es un job o una acción? ¿Qué pasa con notas incompletas o cambiadas después? | **Resuelta (fase 5, tarea 5.5).** Es una acción manual y atómica del administrador (permiso `NOTA_CONSOLIDAR`, sin validar el cargo de Directivo, S-69), idempotente, que se bloquea con 422 `BIMESTRE_INCOMPLETO` y la lista de docentes mientras algún curso no llegue al 100 % (S-70). Editar un logro de un bimestre ya consolidado, mientras sigue vigente, lo marca **DESACTUALIZADO** (S-68) y el último bimestre exige los anteriores al día (C-26) |
| C-55 | ERS UC-19 («informe de progreso del periodo lectivo») y RF-36 vs. UC-19 FA-2 («libreta de notas final») y wireframe p. 26–27 (un nivel grande por curso) | Qué es el informe, qué la libreta y qué nota tiene un *curso* si el modelo califica por competencia | **Resuelta (fase 5, tarea 5.6).** El informe de progreso es el PDF de **un bimestre consolidado** y la libreta es la del **año** (parcial o final, con la columna *Final*); ambos usan `GeneradorPdf`. Un curso **no tiene nota propia** (ni el modelo ni las reglas definen un promedio del curso): la pantalla muestra el nivel de cada competencia, sin inventar un agregado |
| C-56 | ERS UC-19 (el alumno ve «cursos de su sección») y wireframes (siempre el año en curso) vs. la necesidad de mostrar la libreta FINAL de un año ya cerrado | ¿Dónde se ve la libreta de un año anterior si la matrícula vigente excluye los años cerrados? | **Resuelta (fase 5, tarea 5.6).** Los cursos y la libreta aceptan `anio`; sin matrícula en ese año, los cursos salen de las notas del alumno. El alcance sigue siendo «yo» o «mis hijos» y la lista de libretas por año sale en `/alumnos/{id}/libretas` |

## 14. Supuestos y preguntas abiertas

### Supuestos (validar con el equipo o la institución)
- **S-01** Pantallas `/recuperar-contrasena` y `/restablecer-contrasena` sin wireframe, con el mismo estilo que /login. Enlace válido **30 min** (arquitectura § 4.3), de un solo uso, guardado como hash SHA-256; al usarse se revocan los refresh tokens. Política de contraseñas: mínimo 8 caracteres, con letra y número.
- **S-02** Pantallas sin wireframe para RF-03, RF-04, RF-06, RF-46, RF-47 y RF-48, porque son RF del catálogo sin UC propio.
- **S-03** **Duración del bloqueo temporal: 15 minutos** (la arquitectura no la fija); el administrador puede desbloquear antes (acción del wireframe).
- **S-04** **Empate en la moda: gana el nivel del registro más reciente** (supuesto de la arquitectura § 9.2, pendiente de confirmar con la institución).
- **S-05** Redondeo HALF_UP y pesos iguales por defecto: **ya definidos en RF-38**, no son supuestos.
- **S-06** Refresh token de 7 días con rotación y revocación de toda la familia si se reutiliza; el token de acceso vive en memoria en la SPA.
- **S-07** Día lectivo = fecha dentro de un bimestre del año ACTIVO cuyo día de la semana tiene bloques configurados. No hay feriados, porque el modelo no tiene esa entidad (P-04).
- **S-08** El estado de admisión lo registra el administrador en la solicitud: el proceso de admisión está fuera del sistema.
- **S-09** La cuenta del apoderado la crea el administrador antes de su primera solicitud. La cuenta del alumno se crea inactiva al registrar la solicitud y se habilita con UC-24 cuando hay una matrícula activa (P-03).
- **S-10** La penalidad se aplica una sola vez: `porcentaje × monto_base`, el día después del vencimiento; no se acumula por días ni por meses (wireframe: «aplicada el 01/09»).
- **S-11** El número de operación es único entre los comprobantes PENDIENTES o APROBADOS; uno rechazado u observado libera el número para corregirlo.
- **S-12** Un RegistroLogro por (alumno, competencia, bimestre), como la rejilla del wireframe; editar una celda actualiza el registro y se audita. La nota bimestral es la estrategia aplicada a los logros del bimestre (hoy, uno) y la anual es la estrategia aplicada a los logros del año con los pesos por bimestre de RF-38. Si P-02 cambia, basta con quitar el `UNIQUE`.
- **S-13** `asignacion_docente` (docente–curso–sección) se crea o actualiza al publicar el horario, a partir de las asignaciones publicadas; la publicación exige un único docente por curso y sección.
- **S-14** El editor de pesos por bimestre se agrega a la pantalla «Configuración del curso» (el wireframe no lo muestra).
- **S-15** Al publicar, se copia el borrador a un HorarioPublicado inmutable; el borrador se conserva como base de la siguiente versión.
- **S-16** El correo de UC-02 se envía después del commit y de forma asíncrona; si falla, se registra y se reintenta (máximo 3 veces). No revierte la matrícula.
- **S-17** El alumno ve su propia matrícula y su asistencia en el inicio (wireframe p. 2); no hay UC específico.
- **S-18** La «cuota de ingreso si aplica» de UC-02, paso 7, se modela como un concepto opcional del cronograma (concepto `CUOTA_INGRESO`, solo para nuevo ingreso; vacío en la semilla).
- **S-19** Archivos: PDF, JPG y PNG de hasta 5 MB; URL prefirmadas de 5 min.
- **S-20** En local, `GestorArchivosDisco` imita las URL prefirmadas en el mismo origen (`/api/v1/archivos-locales/{token}`).
- **S-21** Ids `VARCHAR(36)` con UUIDv7 generado por Hibernate 7 (`@UuidGenerator(style = VERSION_7)`): es un String, como dice la ERS.
- **S-22** Sin librería de componentes UI; CSS propio.
- **S-23** Los montos viajan como string decimal en JSON.
- **S-24** Cobertura mínima orientativa del 80 % en el dominio.
- **S-25** Las rutas de la SPA usan los identificadores legibles de los wireframes (`3A`, `matematica`, `MAT-2027-00482`, `2026-0315`); el backend los resuelve dentro del año activo.
- **S-26** Migraciones V1 a V11 editables hasta el primer despliegue compartido (§ 4.4); desde entonces, solo hacia adelante con `V12+`.
- **S-27** La demo del 23/10 no incluye refresh token ni recuperación de contraseña; el token de acceso (15 min) no se renueva.
- **S-57** *(fase 4)* Día lectivo (UC-07 FA-3, UC-12 paso 2, reportes): fecha dentro de un bimestre del año ACTIVO con bloques ese día de la semana; sin feriados (S-07, P-04). Es el mismo criterio para marcar, pasar lista, cerrar el día y calcular los reportes.
- **S-58** *(fase 4)* Una marcación docente **antes** de la primera clase es PUNTUAL y una desde `SchoolDayClosingTime` (inclusive) se rechaza con 422 `JORNADA_CERRADA`; **S-59** no hay hora mínima de apertura de la marcación.
- **S-60** *(fase 4)* El límite de puntualidad del docente se mide con el inicio de **su primera clase de ese día**, no con el primer bloque del colegio (UC-07 paso 5).
- **S-61** *(fase 4)* La marcación docente no se audita (el registro es la evidencia); sí se auditan el cambio de FALTA a TARDANZA (UC-09, C-07) y el cierre diario (UC-12, como «Sistema», solo si crea faltas).
- **S-62** *(fase 4)* «Mi asistencia» muestra hoy y los últimos días con registro; los reportes calculan los alumnos con la matrícula de hoy y los docentes con el horario vigente (un mes pasado usa ese universo).
- **S-63** *(fase 4)* La demo usa un reloj propio solo con el perfil `demo` (C-52). Detalle completo de los supuestos de la fase 4 en `docs/AVANCE.md` § «Fase 4 — notas y supuestos».
- **S-66** *(fase 5, aprobado el 05/11/2026)* **Un logro por (alumno, competencia, bimestre)** (S-12; respuesta a P-02). La nota bimestral es ese logro pasado por la estrategia; la **nota anual** aplica la estrategia a las notas bimestrales de la competencia, con los pesos por bimestre en el promedio ponderado (RF-38; peso 1 si no hay fila). `ULTIMO_REGISTRO` toma el bimestre de **mayor número** (no `fecha_registro`) y el empate de la moda lo gana el bimestre más reciente (S-04). `LogroPonderado.orden` es el número del bimestre.
- **S-67** *(fase 5)* **El registro de logros (UC-15) solo es posible en el bimestre vigente** (`fecha_inicio ≤ hoy ≤ fecha_fin` con el `Clock`); fuera de él, 422 `BIMESTRE_NO_VIGENTE`. Solo el docente de la asignación registra o edita (el administrador no). Los alumnos de la rejilla son los de matrícula confirmada de la sección, en orden alfabético.
- **S-68** *(fase 5)* **Corrección posterior a la consolidación.** Se pueden editar logros de un bimestre ya consolidado **mientras ese bimestre siga vigente**; la consolidación queda **«desactualizada»** y la respuesta avisa que hay que reconsolidar. Terminado el bimestre, editar devuelve 422 `BIMESTRE_NO_VIGENTE`. **No se puede consolidar el último bimestre (C-26) si algún bimestre anterior está desactualizado** (ni sin consolidar). Toda edición queda auditada con el valor anterior y el nuevo.
- **S-69** *(fase 5)* **Quién consolida.** Consolidar un bimestre (UC-18) requiere solo el permiso `NOTA_CONSOLIDAR` del rol Administrador; no se valida el cargo de Directivo (C-38). La restricción del bimestre vigente (S-67) **no** aplica a la consolidación: el administrador puede consolidar un bimestre después de su fecha de fin.
- **S-70** *(fase 5)* **Consolidar un bimestre se bloquea** (422 `BIMESTRE_INCOMPLETO`, con la lista de docentes pendientes, UC-18 FA-2) mientras algún curso-sección tenga menos del 100 % de sus celdas (alumnos × competencias) con logro, como el wireframe. Reconsolidar reemplaza las notas del mismo periodo (idempotente, RF-27).
- **S-71** *(fase 5)* **Último bimestre (C-26, UC-18 FA-1).** Consolidarlo exige los bimestres anteriores consolidados y al día (S-68), recalcula las notas anuales de todas las asignaciones y deja la libreta FINAL. UC-17 calcula la anual de una asignación (idempotente) y puede adelantarse. Si a un alumno le falta la nota bimestral de una competencia, esa anual se calcula con las que existen solo en UC-17; la consolidación final exige que no falte ninguna.
- **S-72** *(fase 5)* **Libreta.** Una por (alumno, año): PARCIAL tras consolidar el primer bimestre y FINAL con el último. Muestra solo lo consolidado. El informe de progreso (PDF) y la libreta llevan alumno, sección, año, y por curso y competencia el nivel esperado, los niveles por bimestre y el anual. Alumno y apoderado ven la sección de su matrícula vigente; otro alumno → 403 `FUERA_DE_ALCANCE`.
- **S-73** *(fase 5)* Se auditan con `@Auditable` el registro y la edición de logros (RF-49), el cambio de regla y de pesos, el cálculo de notas finales y la consolidación. Códigos de error nuevos (`docs/api/errores.md`): `BIMESTRE_NO_VIGENTE` (nuevo, tarea 5.2); `NIVEL_LOGRO_INVALIDO` y `BIMESTRE_INCOMPLETO` (este último, para bloquear la consolidación de UC-18, ya existían en `CodigoError`).
- **S-74** *(fase 5)* La semilla `V1005` incluye los logros del bimestre I de 2027 (Lucía y 3° A) y **un año escolar anterior completo** con una sección con los cuatro bimestres consolidados, notas anuales y libreta FINAL, para mostrar la libreta FINAL en la demo (el modelo lo permite: los años cerrados conviven con el 2027; si el reloj de la demo lo impidiera, se avisa antes de implementarlo).
- **S-77** *(fase 6, aprobado el 05/11/2026)* **Alta de cuentas por invitación, sin contraseña temporal.** Al crear una cuenta (UC-24) el administrador no define ni ve la contraseña: la cuenta nace PENDIENTE (inactiva, sin contraseña) y recibe un correo de bienvenida con un enlace para definirla. Reutiliza el mecanismo de FA-5 (token aleatorio de 32 bytes, solo se guarda su hash SHA-256, un solo uso) con **72 horas** de validez; «Reenviar invitación» emite otro y anula el anterior. Mientras no la defina, la cuenta no puede iniciar sesión (`CREDENCIALES_INVALIDAS`, igual que una cuenta inexistente). Un enlace sirve a las dos cosas: en una cuenta sin contraseña activa la cuenta; en una activa, la restablece. Desactivar una cuenta anula sus enlaces pendientes. Justificación: ISO/IEC 27002:2022, control 5.17 (información de autenticación: el secreto lo conoce solo su titular). Se prueba con Mailpit.
- **S-82** *(fase 6, aprobado el 05/11/2026; cierra P-03)* **Vínculo académico al habilitar una cuenta (UC-24 FA-2).** El **apoderado** se crea y se habilita **sin hijos** («Sin hijo previo»): necesita su cuenta antes de su primera solicitud (UC-01). El **alumno** no se crea desde Usuarios —nace con la solicitud de matrícula— y solo se **habilita**, y únicamente con una matrícula confirmada en un año no cerrado (si no, 422 `VINCULO_ACADEMICO_INEXISTENTE`); el vínculo se comprueba también al aceptar la invitación. El personal (administrador, docente) no exige vínculo: la vinculación del docente se configura en Horarios (RF-46). El puerto es `seguridad.aplicacion.VerificadorVinculoAcademico`, que implementa `matricula`. FA-2 se prueba con alumnos.
- **S-83** *(fase 6, tarea 6.4; revisa S-62)* **Reportes de meses pasados.** (a) Los días esperados de cada docente salen de la **versión del horario vigente en cada fecha**: la última publicada hasta el final de ese día en la zona institucional. Un día anterior a la primera publicación no aporta días esperados; si en todo el periodo no hay ninguna versión publicada, el reporte de docentes responde 422 `CALENDARIO_NO_DISPONIBLE`. (b) Los alumnos se listan **con la sección que tenían cada día**, según el nuevo historial de secciones de la matrícula (`matricula.historial_seccion`, migración `V12`): un alumno trasladado a mitad de mes aparece en las dos secciones, cada una con los días que estuvo allí, y nunca cuenta dos veces el mismo día. El historial se llena al confirmar la matrícula y en cada cambio de sección (RF-05); las matrículas anteriores a la migración arrancan con su sección actual desde la fecha de matrícula (los traslados previos solo quedan en la auditoría `CAMBIAR_SECCION_MATRICULA`), y una matrícula sin historial se trata como siempre en su sección actual. Una matrícula ANULADA ya no aparece en los reportes.
- **S-84** *(fase 6, tarea 6.5)* **Sin enumeración de cuentas** (ISO/IEC 27002:2022, control 8.5, autenticación segura). Tres puntos del inicio de sesión y la recuperación podían revelar si un correo tiene cuenta: (a) «cuenta bloqueada» solo aparecía en correos registrados; ahora un correo sin cuenta (o con cuenta sin contraseña) también se «bloquea» a los 5 intentos con la misma duración y el mismo mensaje (`BloqueoFicticio`: caché Caffeine **acotada a 10 000 correos y con expiración igual a la duración del bloqueo**, sin tocar la base, de modo que probar muchos correos inexistentes no hace crecer la memoria); (b) la solicitud de recuperación hacía trabajo distinto según existiera la cuenta: ahora ambos caminos consultan, generan el token y calculan su hash, y solo la cuenta real guarda el enlace y manda el correo; (c) ya estaban cubiertos el hash ficticio de BCrypt para igualar el tiempo del login, la inactividad que solo se informa con la contraseña correcta y la respuesta 202 idéntica de la recuperación. **Riesgos residuales aceptados:** (1) con varias instancias del backend cada una cuenta por separado, así que el bloqueo ficticio (y el límite por IP) se reparte entre ellas y el atacante gana hasta N×5 intentos antes de ver el bloqueo; (2) si la caché expulsa un correo por el tope, su contador vuelve a cero (un atacante que sature la caché solo consigue que el bloqueo ficticio se pierda, nunca el real); (3) la diferencia de milisegundos entre la escritura del enlace y su ausencia; lo mitigan el límite de peticiones por IP y que el correo salga fuera del hilo de la petición. UC-24 al crear una cuenta (administrador autenticado) sigue informando `CORREO_DUPLICADO` (FA-1).
- **S-85** *(fase 6, tarea 6.5)* **Revisión de cabeceras y secretos.** Cabeceras de la API (`CabecerasSeguridadIT`): `X-Content-Type-Options: nosniff` y `Cache-Control: no-store` (por omisión de Spring Security), `X-Frame-Options: DENY`, `Content-Security-Policy: frame-ancestors 'none'; base-uri 'none'` (sin `default-src` para no romper la Swagger UI), `Referrer-Policy: no-referrer`, `Permissions-Policy` sin geolocalización, cámara ni micrófono, y `Strict-Transport-Security` (1 año, subdominios) **solo en peticiones HTTPS** (detrás del balanceador, por `forward-headers-strategy`). Las cabeceras de la SPA (CSP completa, HSTS) las pone quien la sirva (S3 + CloudFront en AWS; ver `docs/despliegue`). Secretos: ningún secreto real en el repositorio; `application.yml` no trae valores por defecto de claves, y solo `application-local.yml` y `.env.example` tienen claves de desarrollo, marcadas como tales; `infra/postgres/init` crea los roles con las contraseñas de desarrollo local. **Cerrado (tarea 6.8):** `ValidacionClaveJwt` impide arrancar con cualquier perfil distinto de `local`, `demo` o `test` (incluido ninguno) si `GESEDU_JWT_SECRET` es la clave de desarrollo del repositorio o tiene menos de 256 bits, con un mensaje que nombra la variable y el perfil (`ValidacionClaveJwtIT`). Alcance: solo la clave del JWT; `GESEDU_ARCHIVOS_SECRETO` también trae un valor de desarrollo, pero solo se usa con `gesedu.archivos.tipo=disco` (el perfil `aws` usa S3). `@PreAuthorize` en todo endpoint salvo seis públicos explícitos se exige con `EndpointsProtegidosIT`.
- **S-86** *(fase 6, tarea 6.7; resuelve el hallazgo de la prueba k6 de UC-01)* **Código de alumno con una secuencia de PostgreSQL.** El alta de un alumno (`RegistroFamilias.obtenerORegistrar`) tomaba `pg_advisory_xact_lock(7311001)`, un candado global que se mantenía hasta el commit de toda la solicitud y atendía las altas de una en una (p95 de 6,3 a 6,8 s con 200 apoderados, `docs/despliegue/carga/README.md`). Ahora el número del código `AAAA-NNNN` sale de `seguridad.seq_codigo_alumno` (migración **V13**, inicializada después del mayor número existente; la semilla de la demo la reajusta en `V1006`), y el candado queda **solo por documento** (`pg_advisory_xact_lock(hashtextextended(documento, …))`) para que dos envíos simultáneos del mismo documento sigan sin crear dos cuentas. **Consecuencias aceptadas:** (a) `nextval` no es transaccional (documentación de PostgreSQL sobre secuencias, «nextval»): si la transacción se revierte, el número no se devuelve y la numeración queda con **saltos**; (b) la numeración es **continua entre años** (antes reiniciaba en cada año, porque `MAX()` se calculaba por año); (c) si un código ya existiera por una carga manual, el servicio salta al siguiente. **RNF-09 no cambia:** las vacantes se siguen protegiendo con el `UPDATE` condicional de `academico.seccion` y no usan este candado; lo comprueban `VacantesConcurrentesIT` (20 hilos por la última vacante, 3 repeticiones) y, para los códigos, `SolicitudApoderadoIT` (12 altas simultáneas de alumnos distintos con códigos únicos, y 4 envíos del mismo documento con una sola solicitud). Los códigos de docente (`DOC-NNN`) siguen con su candado consultivo: no están en la ruta crítica de UC-01.

### Preguntas abiertas
- ~~**P-01** Fecha exacta de la demo~~ **Resuelta:** 23/10/2026.
- **P-08** Fecha de inicio de la fase 0 completa (después de la demo) para convertir S1–S8 en fechas.
- **P-02** ~~¿El docente registra **varios** logros por competencia dentro de un bimestre (RF-27, arquitectura § 9.2) o **uno** por bimestre (wireframes p. 22–24)? Afecta a la UI de UC-15 y al alcance de las estrategias.~~ **Resuelta (05/11/2026):** un único logro por competencia y bimestre, como en los wireframes (S-12, S-66); la corrección tras consolidar se rige por S-68.
- ~~**P-03** ¿Quién crea la cuenta del apoderado de una familia nueva antes de UC-01? (S-09)~~ **Resuelta (05/11/2026):** el administrador, desde Usuarios, sin exigir hijos (S-82).
- **P-04** ¿Hay que registrar feriados o días no lectivos dentro de los bimestres? (S-07)
- **P-05** Valores institucionales de TeacherLateThresholdMinutes, StudentLateCutoffTime y SchoolDayClosingTime para producción (la ERS dice que los define la institución).
- **P-06** Confirmar la duración del bloqueo (S-03) y el desempate de la moda (S-04) con la institución.
- **P-07** Actualizar el Modelo Conceptual (C-22, sin Aula) y la Visión (C-14). La arquitectura, la ERS y la matriz ya quedaron alineadas (C-01, C-15, C-17, C-29 a C-31); el wireframe p. 15 se queda como está (C-29).

## 15. Al aprobar el plan
1. Guardar este plan en `docs/PLAN.md` (reemplaza la versión borrada que todavía figura en `HEAD`).
2. Crear `CLAUDE.md` en la raíz con: stack y versiones fijadas (§ 3); estructura (§ 2); reglas de dependencia entre módulos y capas (§ 2.1); convenciones de código (español para el dominio, `record` para DTO, `@Transactional` solo en `aplicacion`, `@Auditable` en las operaciones críticas, BigDecimal para montos, `Clock` inyectado y nunca `LocalDate.now()` sin reloj, errores con `ExcepcionNegocio(CodigoError)`, nada de entidades en los controladores, permisos con `@PreAuthorize` y alcance en el servicio, migraciones nuevas solo hacia adelante); cómo correr y probar en PowerShell y bash (§ 11); prioridad de documentos y la regla de citar RF, RNF, UC y DA con la numeración v4.
3. No escribir código hasta entonces. Después de crearlos, empezar la fase 0.

## Verificación del plan (cuando se implemente)
- Fase 0: `docker compose up -d`; `.\mvnw.cmd -B verify` / `./mvnw -B verify` en verde (ArchUnit, Testcontainers con postgres:18, auditoría inmutable, DA-10); `npm run lint && npm run typecheck && npm test && npm run build`; CI en verde en el PR.
- Fase 1: ejecutar el guion de § 11 de punta a punta en una máquina Windows 11 limpia (IntelliJ `.run/`) y en otra con bash; comprobar en Mailpit los correos de recuperación y de matrícula, en Swagger los endpoints ★ y en psql los conteos de vacantes, obligaciones y auditoría.
- Fases 2 a 6: los criterios de aceptación de cada fase (flujos básicos y alternativos de la ERS) automatizados como pruebas de integración y endpoint, más una demo interna por fase.
