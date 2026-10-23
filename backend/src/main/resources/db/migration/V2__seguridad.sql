-- Esquema seguridad (mínimo de la demo del 23/10): roles, permisos, usuarios y administradores.
-- Pendiente para después (docs/PLAN.md § 4.1): docente, apoderado, alumno, refresh_token y token_recuperacion.

CREATE TABLE seguridad.rol_acceso (
    id          VARCHAR(36)  PRIMARY KEY,
    codigo      VARCHAR(30)  NOT NULL UNIQUE,
    nombre      VARCHAR(80)  NOT NULL,
    descripcion VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE seguridad.permiso (
    id          VARCHAR(36)  PRIMARY KEY,
    codigo      VARCHAR(60)  NOT NULL UNIQUE,
    modulo      VARCHAR(30)  NOT NULL,
    accion      VARCHAR(30)  NOT NULL,
    descripcion VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE seguridad.rol_permiso (
    rol_acceso_id VARCHAR(36) NOT NULL REFERENCES seguridad.rol_acceso (id),
    permiso_id    VARCHAR(36) NOT NULL REFERENCES seguridad.permiso (id),
    PRIMARY KEY (rol_acceso_id, permiso_id)
);

CREATE TABLE seguridad.usuario (
    id                  VARCHAR(36)  PRIMARY KEY,
    nombres             VARCHAR(100) NOT NULL,
    apellidos           VARCHAR(100) NOT NULL,
    documento_identidad VARCHAR(20)  NOT NULL UNIQUE,
    correo              CITEXT,
    hash_contrasena     VARCHAR(100),
    activo              BOOLEAN      NOT NULL DEFAULT TRUE,
    intentos_fallidos   INTEGER      NOT NULL DEFAULT 0 CHECK (intentos_fallidos >= 0),
    bloqueado_hasta     TIMESTAMPTZ,
    ultimo_acceso       TIMESTAMPTZ,
    rol_acceso_id       VARCHAR(36)  NOT NULL REFERENCES seguridad.rol_acceso (id),
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Una cuenta activa necesita credenciales (RF-52)
    CONSTRAINT ck_usuario_activo_con_credenciales
        CHECK (NOT activo OR (correo IS NOT NULL AND hash_contrasena IS NOT NULL))
);

CREATE UNIQUE INDEX ux_usuario_correo ON seguridad.usuario (correo) WHERE correo IS NOT NULL;
CREATE INDEX ix_usuario_rol ON seguridad.usuario (rol_acceso_id);

-- Las cuentas no se eliminan: se desactivan (RF-01). La aplicación no puede borrar usuarios.
REVOKE DELETE ON seguridad.usuario FROM gesedu_app;

CREATE TABLE seguridad.administrador (
    id         VARCHAR(36)  PRIMARY KEY,
    usuario_id VARCHAR(36)  NOT NULL UNIQUE REFERENCES seguridad.usuario (id),
    cargo      VARCHAR(80)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
