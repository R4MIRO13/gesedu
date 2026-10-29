-- Esquema asistencia (docs/PLAN.md § 4.1; nombres en español según la arquitectura, C-09).

CREATE TABLE asistencia.asistencia_alumno (
    id         VARCHAR(36) PRIMARY KEY,
    alumno_id  VARCHAR(36) NOT NULL REFERENCES seguridad.alumno (id),
    fecha      DATE        NOT NULL,
    estado     VARCHAR(10) NOT NULL CHECK (estado IN ('PUNTUAL', 'TARDANZA', 'FALTA')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_asistencia_alumno UNIQUE (alumno_id, fecha)
);

CREATE INDEX ix_asistencia_alumno_fecha ON asistencia.asistencia_alumno (fecha);

-- Sin hora de registro solo existe la FALTA creada por el cierre diario (UC-12)
CREATE TABLE asistencia.asistencia_docente (
    id            VARCHAR(36) PRIMARY KEY,
    docente_id    VARCHAR(36) NOT NULL REFERENCES seguridad.docente (id),
    fecha         DATE        NOT NULL,
    hora_registro TIMESTAMPTZ,
    estado        VARCHAR(10) NOT NULL CHECK (estado IN ('PUNTUAL', 'TARDANZA', 'FALTA')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_asistencia_docente UNIQUE (docente_id, fecha),
    CONSTRAINT ck_asistencia_docente_hora CHECK ((estado = 'FALTA') = (hora_registro IS NULL))
);

CREATE INDEX ix_asistencia_docente_fecha ON asistencia.asistencia_docente (fecha);

-- Fechas que el cierre diario ya procesó (UC-12): evita repetir el trabajo y deja constancia de los fallos (FA-4).
-- El plan no la nombra; la agrega la tarea 0.6 (supuesto S-30).
CREATE TABLE asistencia.cierre_diario (
    id          VARCHAR(36) PRIMARY KEY,
    fecha       DATE        NOT NULL UNIQUE,
    estado      VARCHAR(10) NOT NULL CHECK (estado IN ('EXITOSO', 'FALLIDO')),
    detalle     TEXT,
    ejecutado_en TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
