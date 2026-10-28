-- Esquema academico (docs/PLAN.md § 4.1): año escolar, bimestres, grados, secciones, cursos y competencias.

CREATE TABLE academico.anio_escolar (
    id           VARCHAR(36) PRIMARY KEY,
    anio         INTEGER     NOT NULL UNIQUE CHECK (anio BETWEEN 2000 AND 2100),
    fecha_inicio DATE        NOT NULL,
    fecha_fin    DATE        NOT NULL,
    estado       VARCHAR(12) NOT NULL CHECK (estado IN ('PLANIFICADO', 'ACTIVO', 'CERRADO')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_anio_escolar_fechas CHECK (fecha_fin > fecha_inicio),
    -- UC-13: el año escolar dura entre 10 y 12 meses
    CONSTRAINT ck_anio_escolar_duracion CHECK (
        fecha_fin >= (fecha_inicio + INTERVAL '10 months')::date
        AND fecha_fin <= (fecha_inicio + INTERVAL '12 months')::date)
);

-- Solo puede haber un año escolar ACTIVO
CREATE UNIQUE INDEX ux_anio_escolar_activo ON academico.anio_escolar (estado) WHERE estado = 'ACTIVO';

-- Cuatro bimestres por año; la base impide que se superpongan (UC-13, FA-2). Que caigan dentro del año
-- se valida en la aplicación.
CREATE TABLE academico.bimestre (
    id              VARCHAR(36) PRIMARY KEY,
    anio_escolar_id VARCHAR(36) NOT NULL REFERENCES academico.anio_escolar (id),
    numero          INTEGER     NOT NULL CHECK (numero BETWEEN 1 AND 4),
    fecha_inicio    DATE        NOT NULL,
    fecha_fin       DATE        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_bimestre_fechas CHECK (fecha_fin > fecha_inicio),
    CONSTRAINT ux_bimestre_numero UNIQUE (anio_escolar_id, numero),
    CONSTRAINT ex_bimestre_sin_cruce EXCLUDE USING gist (
        anio_escolar_id WITH =,
        daterange(fecha_inicio, fecha_fin, '[]') WITH &&
    )
);

CREATE TABLE academico.grado (
    id              VARCHAR(36) PRIMARY KEY,
    anio_escolar_id VARCHAR(36) NOT NULL REFERENCES academico.anio_escolar (id),
    numero_nivel    INTEGER     NOT NULL CHECK (numero_nivel BETWEEN 1 AND 5),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_grado UNIQUE (anio_escolar_id, numero_nivel)
);

-- Sección con capacidad y vacantes (RF-04, RNF-09). Las vacantes se descuentan con un UPDATE condicional y el
-- CHECK garantiza que nunca sean negativas ni superen la capacidad.
CREATE TABLE academico.seccion (
    id                   VARCHAR(36) PRIMARY KEY,
    grado_id             VARCHAR(36) NOT NULL REFERENCES academico.grado (id),
    anio_escolar_id      VARCHAR(36) NOT NULL REFERENCES academico.anio_escolar (id),
    letra                VARCHAR(2)  NOT NULL,
    capacidad            INTEGER     NOT NULL CHECK (capacidad > 0),
    vacantes_disponibles INTEGER     NOT NULL,
    tutor_docente_id     VARCHAR(36) REFERENCES seguridad.docente (id),
    version              BIGINT      NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_seccion_vacantes CHECK (vacantes_disponibles >= 0 AND vacantes_disponibles <= capacidad),
    CONSTRAINT ux_seccion_letra UNIQUE (grado_id, letra),
    -- Un docente es tutor de una sola sección por año
    CONSTRAINT ux_seccion_tutor UNIQUE (anio_escolar_id, tutor_docente_id)
);

CREATE TABLE academico.curso (
    id              VARCHAR(36)  PRIMARY KEY,
    anio_escolar_id VARCHAR(36)  NOT NULL REFERENCES academico.anio_escolar (id),
    nombre          VARCHAR(80)  NOT NULL,
    slug            VARCHAR(80)  NOT NULL,
    horas_lectivas  INTEGER      NOT NULL CHECK (horas_lectivas > 0),
    habilitado      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_curso_nombre UNIQUE (anio_escolar_id, nombre),
    CONSTRAINT ux_curso_slug UNIQUE (anio_escolar_id, slug)
);

CREATE TABLE academico.competencia (
    id             VARCHAR(36)  PRIMARY KEY,
    curso_id       VARCHAR(36)  NOT NULL REFERENCES academico.curso (id),
    codigo         VARCHAR(10)  NOT NULL,
    nombre         VARCHAR(160) NOT NULL,
    nivel_esperado TEXT,
    orden          INTEGER      NOT NULL DEFAULT 1,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_competencia_codigo UNIQUE (curso_id, codigo)
);
