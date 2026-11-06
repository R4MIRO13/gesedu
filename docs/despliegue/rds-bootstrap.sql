-- Se ejecuta UNA vez en RDS for PostgreSQL 18 con el usuario maestro (psql). Reemplaza las dos contraseñas por
-- valores generados (Secrets Manager); nunca uses las de desarrollo. Equivale a infra/postgres/init/01-roles-y-bd.sql.
-- Uso:  psql "host=<endpoint> dbname=postgres user=<maestro> sslmode=verify-full" -v migrador_pw="'...'" -v app_pw="'...'" -f rds-bootstrap.sql
CREATE ROLE gesedu_migrador LOGIN PASSWORD :migrador_pw;
CREATE ROLE gesedu_app LOGIN PASSWORD :app_pw;
-- En RDS el usuario maestro no es superusuario: debe poder asumir el rol dueño para crear la base a su nombre
GRANT gesedu_migrador TO CURRENT_USER;
CREATE DATABASE gesedu OWNER gesedu_migrador ENCODING 'UTF8';
REVOKE ALL ON DATABASE gesedu FROM PUBLIC;
GRANT CONNECT ON DATABASE gesedu TO gesedu_app;
GRANT CONNECT, CREATE, TEMPORARY ON DATABASE gesedu TO gesedu_migrador;
