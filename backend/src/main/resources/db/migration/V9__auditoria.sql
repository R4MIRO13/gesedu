-- Esquema auditoria: tablas de SOLO INSERCIÓN (RF-49, RF-51, RNF-08, arquitectura DA-06).
-- Dos defensas: permisos (gesedu_app solo tiene INSERT y SELECT) y un trigger que rechaza UPDATE, DELETE y
-- TRUNCATE incluso al dueño de las tablas.

CREATE TABLE auditoria.registro_auditoria (
    id                  VARCHAR(36)  PRIMARY KEY,
    fecha_hora          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- NULL = «Sistema» (tareas programadas)
    usuario_id          VARCHAR(36)  REFERENCES seguridad.usuario (id),
    accion              VARCHAR(60)  NOT NULL,
    modulo              VARCHAR(40)  NOT NULL,
    tipo_entidad        VARCHAR(60)  NOT NULL,
    id_entidad_afectada VARCHAR(60),
    descripcion         VARCHAR(500) NOT NULL
);

CREATE TABLE auditoria.cambio_auditoria (
    id                    VARCHAR(36) PRIMARY KEY,
    registro_auditoria_id VARCHAR(36) NOT NULL REFERENCES auditoria.registro_auditoria (id),
    campo                 VARCHAR(80) NOT NULL,
    valor_anterior        TEXT,
    valor_nuevo           TEXT
);

-- Consulta con filtros por fecha, usuario, módulo y acción (RF-51, RNF-03)
CREATE INDEX ix_registro_auditoria_fecha   ON auditoria.registro_auditoria (fecha_hora DESC);
CREATE INDEX ix_registro_auditoria_usuario ON auditoria.registro_auditoria (usuario_id, fecha_hora DESC);
CREATE INDEX ix_registro_auditoria_modulo  ON auditoria.registro_auditoria (modulo, fecha_hora DESC);
CREATE INDEX ix_registro_auditoria_accion  ON auditoria.registro_auditoria (accion, fecha_hora DESC);
CREATE INDEX ix_cambio_auditoria_registro  ON auditoria.cambio_auditoria (registro_auditoria_id);

CREATE FUNCTION auditoria.rechazar_modificacion() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'Las tablas de auditoría son de solo inserción: % no está permitido en %.%',
        TG_OP, TG_TABLE_SCHEMA, TG_TABLE_NAME;
END
$$;

CREATE TRIGGER tg_registro_auditoria_solo_insercion
    BEFORE UPDATE OR DELETE ON auditoria.registro_auditoria
    FOR EACH ROW EXECUTE FUNCTION auditoria.rechazar_modificacion();

CREATE TRIGGER tg_registro_auditoria_sin_truncate
    BEFORE TRUNCATE ON auditoria.registro_auditoria
    FOR EACH STATEMENT EXECUTE FUNCTION auditoria.rechazar_modificacion();

CREATE TRIGGER tg_cambio_auditoria_solo_insercion
    BEFORE UPDATE OR DELETE ON auditoria.cambio_auditoria
    FOR EACH ROW EXECUTE FUNCTION auditoria.rechazar_modificacion();

CREATE TRIGGER tg_cambio_auditoria_sin_truncate
    BEFORE TRUNCATE ON auditoria.cambio_auditoria
    FOR EACH STATEMENT EXECUTE FUNCTION auditoria.rechazar_modificacion();

-- Permisos: solo INSERT y SELECT para la aplicación
REVOKE ALL ON auditoria.registro_auditoria FROM gesedu_app;
REVOKE ALL ON auditoria.cambio_auditoria   FROM gesedu_app;
GRANT SELECT, INSERT ON auditoria.registro_auditoria TO gesedu_app;
GRANT SELECT, INSERT ON auditoria.cambio_auditoria   TO gesedu_app;
