# Despliegue en AWS

**Estado: sin desplegar.** El perfil `aws` existe (`backend/src/main/resources/application-aws.yml`) pero no hay infraestructura creada. El diseño sale de `docs/PLAN.md` § 12 y de la arquitectura v2.0 (DA-07).

## Piezas
| Pieza | Servicio | Notas |
|---|---|---|
| API | Elastic Beanstalk (Corretto 25, Amazon Linux 2023) | JAR `gesedu-api.jar` (`finalName`); `SPRING_PROFILES_ACTIVE=aws`; puerto 5000 (`SERVER_PORT`); health check `/actuator/health`; sin estado (JWT; los candados de ShedLock están en la base) |
| Base de datos | RDS for PostgreSQL 18 | Crear la base y los roles una vez con `rds-bootstrap.sql`; Flyway corre al arrancar como `gesedu_migrador`; la app usa `gesedu_app`; JDBC con `sslmode=verify-full` |
| SPA | S3 (`gesedu-web`, acceso por OAC) + CloudFront | `npm run build` → `dist/`; `/*` a S3 con fallback a `index.html` y `/api/*` al balanceador sin caché y reenviando `Authorization` y cookies; mismo origen, sin CORS entre SPA y API |
| Documentos | S3 privado `gesedu-documentos` (SSE-S3, versionado) | URL prefirmadas de 5 min (PDF, JPG y PNG ≤ 5 MB); regla CORS con el origen real de la SPA (PUT y GET) |
| Correo | SES | Dominio verificado y `GESEDU_MAIL_FROM`; el perfil fija `notificaciones.tipo: ses` |
| Secretos | Secrets Manager / Parameter Store | Nada de secretos en el repositorio ni en el JAR |

## Variables de entorno (perfil `aws`)
Obligatorias: `GESEDU_DB_URL`, `GESEDU_DB_APP_USER`, `GESEDU_DB_APP_PASSWORD`, `GESEDU_DB_MIGRATOR_USER`, `GESEDU_DB_MIGRATOR_PASSWORD`, `GESEDU_JWT_SECRET`, `GESEDU_S3_BUCKET`, `AWS_REGION`, `GESEDU_MAIL_FROM`, `GESEDU_WEB_ORIGIN`. Con valor por omisión (revisarlos): `GESEDU_COOKIE_SECURE=true`, `GESEDU_JWT_MINUTOS=15`, `GESEDU_REFRESH_DIAS=7`, `GESEDU_BLOQUEO_MINUTOS=15`, `GESEDU_LIMITE_AUTH_POR_MINUTO=30`, `GESEDU_TIME_ZONE=America/Lima` y los parámetros de asistencia (`GESEDU_ASISTENCIA_*`). El perfil `aws` **no** debe combinarse con `demo` (reloj de 2027 y cuentas conocidas).

## Pasos
1. Crear RDS y ejecutar `rds-bootstrap.sql` con el usuario maestro y dos contraseñas generadas.
2. Crear los buckets, la regla CORS del bucket de documentos y la identidad de SES; dar al rol de la instancia de Beanstalk acceso solo al bucket de documentos y `ses:SendEmail`.
3. `./mvnw -B -DskipTests package` y subir `target/gesedu-api.jar` a Beanstalk con las variables.
4. `npm ci && npm run build` y `aws s3 sync dist s3://gesedu-web --delete`; invalidar CloudFront.
5. Antes del primer despliegue compartido, poner la etiqueta `esquema-congelado` (las migraciones pasan a «solo hacia adelante»).

## Pendiente
CI/CD de despliegue (hoy el CI solo verifica), logs JSON a CloudWatch, la verificación de arranque de la clave JWT y el dimensionamiento del pool de conexiones tras resolver el hallazgo de `carga/README.md`.
