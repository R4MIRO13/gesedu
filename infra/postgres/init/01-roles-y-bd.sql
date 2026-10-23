-- Se ejecuta una sola vez, al inicializar el volumen de PostgreSQL (docker-entrypoint-initdb.d).
-- SOLO PARA DESARROLLO LOCAL Y TESTCONTAINERS: las contraseñas son de juguete.
-- En RDS los roles se crean una vez con el usuario maestro y secretos reales (docs/despliegue/aws.md).

-- gesedu_migrador: dueño del esquema; lo usa Flyway.
CREATE ROLE gesedu_migrador LOGIN PASSWORD 'gesedu_migrador';

-- gesedu_app: lo usa la aplicación; sin permisos de DDL y, en auditoría, solo INSERT y SELECT (RNF-08).
CREATE ROLE gesedu_app LOGIN PASSWORD 'gesedu_app';

CREATE DATABASE gesedu OWNER gesedu_migrador ENCODING 'UTF8';

REVOKE ALL ON DATABASE gesedu FROM PUBLIC;
GRANT CONNECT ON DATABASE gesedu TO gesedu_app;
GRANT CONNECT, CREATE, TEMPORARY ON DATABASE gesedu TO gesedu_migrador;
