-- Esquema evaluacion (docs/PLAN.md § 4.1): asignaciones docentes, pesos, logros, notas consolidadas y libretas.

-- Docente–curso–sección; se crea o actualiza al publicar el horario (S-13). Lleva la regla de consolidación (UC-16).
CREATE TABLE evaluacion.asignacion_docente (
    id                 VARCHAR(36) PRIMARY KEY,
    docente_id         VARCHAR(36) NOT NULL REFERENCES seguridad.docente (id),
    curso_id           VARCHAR(36) NOT NULL REFERENCES academico.curso (id),
    seccion_id         VARCHAR(36) NOT NULL REFERENCES academico.seccion (id),
    regla_consolidacion VARCHAR(20) NOT NULL DEFAULT 'PROMEDIO_PONDERADO'
        CHECK (regla_consolidacion IN ('PROMEDIO_PONDERADO', 'MODA', 'ULTIMO_REGISTRO')),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_asignacion_docente UNIQUE (curso_id, seccion_id)
);

CREATE INDEX ix_asignacion_docente ON evaluacion.asignacion_docente (docente_id);

-- Peso de cada bimestre en la nota anual (RF-38); 1 por defecto
CREATE TABLE evaluacion.peso_bimestre (
    id            VARCHAR(36)  PRIMARY KEY,
    asignacion_id VARCHAR(36)  NOT NULL REFERENCES evaluacion.asignacion_docente (id),
    bimestre_id   VARCHAR(36)  NOT NULL REFERENCES academico.bimestre (id),
    peso          NUMERIC(5,2) NOT NULL DEFAULT 1 CHECK (peso > 0),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_peso_bimestre UNIQUE (asignacion_id, bimestre_id)
);

-- Un registro por (alumno, competencia, bimestre) como la rejilla de los wireframes (S-12, C-12)
CREATE TABLE evaluacion.registro_logro (
    id             VARCHAR(36) PRIMARY KEY,
    asignacion_id  VARCHAR(36) NOT NULL REFERENCES evaluacion.asignacion_docente (id),
    alumno_id      VARCHAR(36) NOT NULL REFERENCES seguridad.alumno (id),
    competencia_id VARCHAR(36) NOT NULL REFERENCES academico.competencia (id),
    bimestre_id    VARCHAR(36) NOT NULL REFERENCES academico.bimestre (id),
    nivel_logro    VARCHAR(2)  NOT NULL CHECK (nivel_logro IN ('AD', 'A', 'B', 'C')),
    fecha_registro TIMESTAMPTZ NOT NULL,
    version        BIGINT      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_registro_logro UNIQUE (alumno_id, competencia_id, bimestre_id)
);

CREATE INDEX ix_registro_logro_asignacion ON evaluacion.registro_logro (asignacion_id, bimestre_id);

CREATE TABLE evaluacion.consolidacion_bimestral (
    id                  VARCHAR(36) PRIMARY KEY,
    bimestre_id         VARCHAR(36) NOT NULL UNIQUE REFERENCES academico.bimestre (id),
    fecha_consolidacion TIMESTAMPTZ NOT NULL,
    estado              VARCHAR(12) NOT NULL DEFAULT 'COMPLETADA' CHECK (estado IN ('COMPLETADA')),
    ejecutada_por       VARCHAR(36) REFERENCES seguridad.usuario (id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE evaluacion.libreta (
    id                  VARCHAR(36) PRIMARY KEY,
    alumno_id           VARCHAR(36) NOT NULL REFERENCES seguridad.alumno (id),
    anio_escolar_id     VARCHAR(36) NOT NULL REFERENCES academico.anio_escolar (id),
    estado              VARCHAR(10) NOT NULL DEFAULT 'PARCIAL' CHECK (estado IN ('PARCIAL', 'FINAL')),
    fecha_actualizacion TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_libreta UNIQUE (alumno_id, anio_escolar_id)
);

-- Nota bimestral o anual por competencia. El cálculo es idempotente: hace upsert sobre los índices parciales.
CREATE TABLE evaluacion.nota_consolidada (
    id              VARCHAR(36) PRIMARY KEY,
    alumno_id       VARCHAR(36) NOT NULL REFERENCES seguridad.alumno (id),
    competencia_id  VARCHAR(36) NOT NULL REFERENCES academico.competencia (id),
    asignacion_id   VARCHAR(36) NOT NULL REFERENCES evaluacion.asignacion_docente (id),
    anio_escolar_id VARCHAR(36) NOT NULL REFERENCES academico.anio_escolar (id),
    bimestre_id     VARCHAR(36) REFERENCES academico.bimestre (id),
    tipo            VARCHAR(10) NOT NULL CHECK (tipo IN ('BIMESTRAL', 'ANUAL')),
    nivel_logro     VARCHAR(2)  NOT NULL CHECK (nivel_logro IN ('AD', 'A', 'B', 'C')),
    calculada_en    TIMESTAMPTZ NOT NULL,
    libreta_id      VARCHAR(36) REFERENCES evaluacion.libreta (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_nota_bimestre CHECK ((tipo = 'BIMESTRAL') = (bimestre_id IS NOT NULL))
);

CREATE UNIQUE INDEX ux_nota_bimestral ON evaluacion.nota_consolidada (alumno_id, competencia_id, bimestre_id)
    WHERE tipo = 'BIMESTRAL';
CREATE UNIQUE INDEX ux_nota_anual ON evaluacion.nota_consolidada (alumno_id, competencia_id, anio_escolar_id)
    WHERE tipo = 'ANUAL';
