-- Esquema academico (mínimo de la demo del 23/10): solo el año escolar, que necesitan los bloques horarios.
-- Pendiente para después: bimestre, grado, seccion, curso y competencia (docs/PLAN.md § 4.1).

CREATE TABLE academico.anio_escolar (
    id           VARCHAR(36) PRIMARY KEY,
    anio         INTEGER     NOT NULL UNIQUE CHECK (anio BETWEEN 2000 AND 2100),
    fecha_inicio DATE        NOT NULL,
    fecha_fin    DATE        NOT NULL,
    estado       VARCHAR(12) NOT NULL CHECK (estado IN ('PLANIFICADO', 'ACTIVO', 'CERRADO')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_anio_escolar_fechas CHECK (fecha_fin > fecha_inicio)
);

-- Solo puede haber un año escolar ACTIVO
CREATE UNIQUE INDEX ux_anio_escolar_activo ON academico.anio_escolar (estado) WHERE estado = 'ACTIVO';
