-- Extensiones y un esquema por módulo (arquitectura v1.0, DA-03).
-- btree_gist, citext y pgcrypto son extensiones "trusted": las puede crear el dueño de la base de datos.
CREATE EXTENSION IF NOT EXISTS btree_gist;  -- restricciones EXCLUDE (bloques y bimestres sin superposición)
CREATE EXTENSION IF NOT EXISTS citext;      -- correo sin distinguir mayúsculas
CREATE EXTENSION IF NOT EXISTS pgcrypto;    -- crypt() para las contraseñas de la semilla de demo

CREATE SCHEMA seguridad;
CREATE SCHEMA academico;
CREATE SCHEMA matricula;
CREATE SCHEMA pagos;
CREATE SCHEMA asistencia;
CREATE SCHEMA evaluacion;
CREATE SCHEMA horarios;
CREATE SCHEMA auditoria;
CREATE SCHEMA transversal;

-- Permisos de gesedu_app sobre lo que cree gesedu_migrador a partir de ahora. Las tablas de auditoría
-- recortan estos permisos en V9 (solo INSERT y SELECT).
DO $$
DECLARE
    esquema text;
BEGIN
    FOREACH esquema IN ARRAY ARRAY['seguridad', 'academico', 'matricula', 'pagos', 'asistencia',
                                   'evaluacion', 'horarios', 'auditoria', 'transversal']
    LOOP
        EXECUTE format('GRANT USAGE ON SCHEMA %I TO gesedu_app', esquema);
        EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO gesedu_app', esquema);
        EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I GRANT USAGE, SELECT ON SEQUENCES TO gesedu_app', esquema);
    END LOOP;
END
$$;
