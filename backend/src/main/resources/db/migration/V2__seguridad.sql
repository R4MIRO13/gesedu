-- Esquema seguridad (docs/PLAN.md § 4.1): roles, permisos, usuarios, perfiles (administrador, docente,
-- apoderado, alumno) y tokens técnicos (refresh y recuperación).

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

CREATE TABLE seguridad.docente (
    id             VARCHAR(36)  PRIMARY KEY,
    usuario_id     VARCHAR(36)  NOT NULL UNIQUE REFERENCES seguridad.usuario (id),
    codigo_docente VARCHAR(20)  NOT NULL UNIQUE,
    especialidad   VARCHAR(80),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE seguridad.apoderado (
    id              VARCHAR(36)  PRIMARY KEY,
    usuario_id      VARCHAR(36)  NOT NULL UNIQUE REFERENCES seguridad.usuario (id),
    numero_telefono VARCHAR(20),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Un alumno tiene un apoderado responsable ("es responsable de" 1..*); fecha de nacimiento y parentesco (C-22)
CREATE TABLE seguridad.alumno (
    id                   VARCHAR(36)  PRIMARY KEY,
    usuario_id           VARCHAR(36)  NOT NULL UNIQUE REFERENCES seguridad.usuario (id),
    codigo_alumno        VARCHAR(20)  NOT NULL UNIQUE,
    fecha_nacimiento     DATE         NOT NULL,
    apoderado_id         VARCHAR(36)  NOT NULL REFERENCES seguridad.apoderado (id),
    parentesco_apoderado VARCHAR(30)  NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_alumno_apoderado ON seguridad.alumno (apoderado_id);

-- Refresh token rotativo (DA-04, S-06): solo se guarda el hash SHA-256. Todos los tokens de una cadena de
-- rotaciones comparten la familia; reutilizar uno ya rotado revoca la familia entera.
CREATE TABLE seguridad.refresh_token (
    id               VARCHAR(36)  PRIMARY KEY,
    usuario_id       VARCHAR(36)  NOT NULL REFERENCES seguridad.usuario (id),
    token_hash       VARCHAR(64)  NOT NULL UNIQUE,
    familia          VARCHAR(36)  NOT NULL,
    expira_en        TIMESTAMPTZ  NOT NULL,
    revocado_en      TIMESTAMPTZ,
    reemplazado_por  VARCHAR(36),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_refresh_token_usuario ON seguridad.refresh_token (usuario_id) WHERE revocado_en IS NULL;
CREATE INDEX ix_refresh_token_familia ON seguridad.refresh_token (familia);

-- Recuperación de contraseña (RF-52, S-01): enlace de un solo uso que vence a los 30 minutos
CREATE TABLE seguridad.token_recuperacion (
    id          VARCHAR(36)  PRIMARY KEY,
    usuario_id  VARCHAR(36)  NOT NULL REFERENCES seguridad.usuario (id),
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    expira_en   TIMESTAMPTZ  NOT NULL,
    usado_en    TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_token_recuperacion_pendiente ON seguridad.token_recuperacion (usuario_id) WHERE usado_en IS NULL;
