-- Esquema matricula (docs/PLAN.md § 4.1): solicitudes, documentos y matrículas.

CREATE SEQUENCE matricula.seq_solicitud START 1;

CREATE TABLE matricula.solicitud_matricula (
    id                   VARCHAR(36)  PRIMARY KEY,
    -- MAT-2027-00482
    codigo_seguimiento   VARCHAR(20)  NOT NULL UNIQUE,
    anio_escolar_id      VARCHAR(36)  NOT NULL REFERENCES academico.anio_escolar (id),
    alumno_id            VARCHAR(36)  NOT NULL REFERENCES seguridad.alumno (id),
    apoderado_id         VARCHAR(36)  NOT NULL REFERENCES seguridad.apoderado (id),
    grado_id             VARCHAR(36)  NOT NULL REFERENCES academico.grado (id),
    fecha_solicitud      DATE         NOT NULL,
    tipo_estudiante      VARCHAR(15)  NOT NULL CHECK (tipo_estudiante IN ('NUEVO_INGRESO', 'RECURRENTE')),
    estado_documentacion VARCHAR(12)  NOT NULL DEFAULT 'COMPLETA' CHECK (estado_documentacion IN ('COMPLETA', 'INCOMPLETA')),
    -- La admisión se hace fuera del sistema; la registra el administrador (S-08)
    estado_admision      VARCHAR(12)  NOT NULL DEFAULT 'NO_APLICA' CHECK (estado_admision IN ('ADMITIDO', 'NO_ADMITIDO', 'NO_APLICA')),
    estado               VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE_REVISION'
        CHECK (estado IN ('PENDIENTE_REVISION', 'APROBADA', 'RECHAZADA')),
    motivo_rechazo       VARCHAR(25)
        CHECK (motivo_rechazo IN ('NO_ADMITIDO', 'DUPLICIDAD', 'DEUDAS', 'SIN_VACANTE', 'VOLUNTAD_APODERADO')),
    observacion          TEXT,
    costos_aceptados     BOOLEAN      NOT NULL DEFAULT FALSE,
    evaluado_por         VARCHAR(36)  REFERENCES seguridad.usuario (id),
    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_solicitud_rechazo CHECK (estado <> 'RECHAZADA' OR motivo_rechazo IS NOT NULL)
);

-- UC-01, FA-2: una sola solicitud en trámite por alumno y año
CREATE UNIQUE INDEX ux_solicitud_en_tramite ON matricula.solicitud_matricula (alumno_id, anio_escolar_id)
    WHERE estado = 'PENDIENTE_REVISION';
CREATE INDEX ix_solicitud_estado ON matricula.solicitud_matricula (estado, fecha_solicitud);
CREATE INDEX ix_solicitud_apoderado ON matricula.solicitud_matricula (apoderado_id);

CREATE TABLE matricula.documento_solicitud (
    id            VARCHAR(36)  PRIMARY KEY,
    solicitud_id  VARCHAR(36)  NOT NULL REFERENCES matricula.solicitud_matricula (id),
    tipo          VARCHAR(30)  NOT NULL CHECK (tipo IN ('CERTIFICADO_ESTUDIOS', 'FICHA_MATRICULA_ANTERIOR', 'OTRO')),
    clave_objeto  VARCHAR(255) NOT NULL,
    nombre_archivo VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    tamano_bytes  BIGINT       NOT NULL CHECK (tamano_bytes > 0),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_documento_solicitud ON matricula.documento_solicitud (solicitud_id);

CREATE TABLE matricula.matricula (
    id              VARCHAR(36) PRIMARY KEY,
    solicitud_id    VARCHAR(36) NOT NULL UNIQUE REFERENCES matricula.solicitud_matricula (id),
    alumno_id       VARCHAR(36) NOT NULL REFERENCES seguridad.alumno (id),
    anio_escolar_id VARCHAR(36) NOT NULL REFERENCES academico.anio_escolar (id),
    seccion_id      VARCHAR(36) NOT NULL REFERENCES academico.seccion (id),
    fecha_matricula DATE        NOT NULL,
    estado          VARCHAR(12) NOT NULL DEFAULT 'CONFIRMADA' CHECK (estado IN ('CONFIRMADA', 'ANULADA')),
    confirmada_por  VARCHAR(36) REFERENCES seguridad.usuario (id),
    version         BIGINT      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- RNF-09, RF-05: un alumno tiene a lo sumo una matrícula confirmada por año
CREATE UNIQUE INDEX ux_matricula_confirmada ON matricula.matricula (alumno_id, anio_escolar_id)
    WHERE estado = 'CONFIRMADA';
CREATE INDEX ix_matricula_seccion ON matricula.matricula (seccion_id) WHERE estado = 'CONFIRMADA';

-- Ahora que existe la matrícula, se cierra la FK pendiente de V4
ALTER TABLE pagos.obligacion_pago
    ADD CONSTRAINT fk_obligacion_matricula FOREIGN KEY (matricula_id) REFERENCES matricula.matricula (id);
