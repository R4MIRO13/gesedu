-- Esquema pagos (docs/PLAN.md § 4.1): cronograma y política de cobro, obligaciones, comprobantes y boletas.
-- Montos NUMERIC(12,2) (RNF-11). La FK de obligacion_pago hacia matricula.matricula se agrega en V5, porque
-- esa tabla todavía no existe.

CREATE TABLE pagos.cronograma_cobro (
    id                VARCHAR(36)   PRIMARY KEY,
    anio_escolar_id   VARCHAR(36)   NOT NULL REFERENCES academico.anio_escolar (id),
    -- CUOTA_INGRESO es opcional y solo para nuevo ingreso (S-18)
    concepto          VARCHAR(20)   NOT NULL CHECK (concepto IN ('MATRICULA', 'PENSION', 'CUOTA_INGRESO')),
    periodo           VARCHAR(20)   NOT NULL,
    monto             NUMERIC(12,2) NOT NULL CHECK (monto >= 0),
    fecha_vencimiento DATE          NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ux_cronograma_cobro UNIQUE (anio_escolar_id, concepto, periodo)
);

CREATE TABLE pagos.politica_cobro (
    id                   VARCHAR(36)  PRIMARY KEY,
    anio_escolar_id      VARCHAR(36)  NOT NULL UNIQUE REFERENCES academico.anio_escolar (id),
    porcentaje_penalidad NUMERIC(5,2) NOT NULL CHECK (porcentaje_penalidad BETWEEN 0 AND 100),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE pagos.obligacion_pago (
    id                    VARCHAR(36)   PRIMARY KEY,
    matricula_id          VARCHAR(36)   NOT NULL,
    anio_escolar_id       VARCHAR(36)   NOT NULL REFERENCES academico.anio_escolar (id),
    alumno_id             VARCHAR(36)   NOT NULL REFERENCES seguridad.alumno (id),
    apoderado_id          VARCHAR(36)   NOT NULL REFERENCES seguridad.apoderado (id),
    concepto              VARCHAR(20)   NOT NULL CHECK (concepto IN ('MATRICULA', 'PENSION', 'CUOTA_INGRESO')),
    periodo               VARCHAR(20)   NOT NULL,
    monto_base            NUMERIC(12,2) NOT NULL CHECK (monto_base >= 0),
    fecha_vencimiento     DATE          NOT NULL,
    monto_penalidad       NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (monto_penalidad >= 0),
    -- La penalidad se aplica una sola vez (S-10): el job es idempotente gracias a esta marca
    penalidad_aplicada_en TIMESTAMPTZ,
    estado                VARCHAR(10)   NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE', 'PAGADA', 'VENCIDA')),
    version               BIGINT        NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ux_obligacion_pago UNIQUE (matricula_id, concepto, periodo)
);

CREATE INDEX ix_obligacion_alumno ON pagos.obligacion_pago (alumno_id);
CREATE INDEX ix_obligacion_estado_vencimiento ON pagos.obligacion_pago (estado, fecha_vencimiento);
CREATE INDEX ix_obligacion_anio ON pagos.obligacion_pago (anio_escolar_id);

CREATE TABLE pagos.comprobante_pago (
    id                     VARCHAR(36)   PRIMARY KEY,
    obligacion_id          VARCHAR(36)   NOT NULL REFERENCES pagos.obligacion_pago (id),
    apoderado_id           VARCHAR(36)   NOT NULL REFERENCES seguridad.apoderado (id),
    numero_operacion       VARCHAR(40)   NOT NULL,
    fecha_pago             DATE          NOT NULL,
    monto_presentado       NUMERIC(12,2) NOT NULL CHECK (monto_presentado > 0),
    medio_pago             VARCHAR(30)   NOT NULL,
    -- Clave del objeto en el Gestor de Archivos (S3 o disco)
    referencia_comprobante VARCHAR(255)  NOT NULL,
    estado_revision        VARCHAR(10)   NOT NULL DEFAULT 'PENDIENTE'
        CHECK (estado_revision IN ('PENDIENTE', 'APROBADO', 'OBSERVADO', 'RECHAZADO')),
    observacion            TEXT,
    revisado_por           VARCHAR(36)   REFERENCES seguridad.usuario (id),
    revisado_en            TIMESTAMPTZ,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- RF-08, S-11: el número de operación es único entre comprobantes PENDIENTES o APROBADOS; uno observado o
-- rechazado libera el número para corregirlo.
CREATE UNIQUE INDEX ux_comprobante_operacion_vigente ON pagos.comprobante_pago (numero_operacion)
    WHERE estado_revision IN ('PENDIENTE', 'APROBADO');
CREATE UNIQUE INDEX ux_comprobante_aprobado ON pagos.comprobante_pago (obligacion_id)
    WHERE estado_revision = 'APROBADO';
CREATE UNIQUE INDEX ux_comprobante_pendiente ON pagos.comprobante_pago (obligacion_id)
    WHERE estado_revision = 'PENDIENTE';

CREATE SEQUENCE pagos.seq_boleta START 1;

-- Boleta emitida al validar un comprobante (UC-05): B001-00001
CREATE TABLE pagos.registro_pago (
    id             VARCHAR(36)   PRIMARY KEY,
    comprobante_id VARCHAR(36)   NOT NULL UNIQUE REFERENCES pagos.comprobante_pago (id),
    obligacion_id  VARCHAR(36)   NOT NULL REFERENCES pagos.obligacion_pago (id),
    numero_boleta  VARCHAR(12)   NOT NULL UNIQUE,
    fecha_registro TIMESTAMPTZ   NOT NULL,
    monto_total    NUMERIC(12,2) NOT NULL CHECK (monto_total > 0),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
