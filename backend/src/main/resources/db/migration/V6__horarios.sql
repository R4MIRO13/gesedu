-- Esquema horarios (docs/PLAN.md § 4.1): bloques, disponibilidad, vinculaciones, borrador y horarios publicados.
-- No existe la entidad Aula: cada sección ocupa siempre la misma aula (ERS § 2.3, C-03).

CREATE TABLE horarios.bloque_horario (
    id              VARCHAR(36) PRIMARY KEY,
    anio_escolar_id VARCHAR(36) NOT NULL REFERENCES academico.anio_escolar (id),
    dia_semana      VARCHAR(10) NOT NULL
        CHECK (dia_semana IN ('LUNES', 'MARTES', 'MIERCOLES', 'JUEVES', 'VIERNES', 'SABADO', 'DOMINGO')),
    hora_inicio     TIME        NOT NULL,
    hora_fin        TIME        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_bloque_horario_horas CHECK (hora_fin > hora_inicio),
    CONSTRAINT ux_bloque_horario UNIQUE (anio_escolar_id, dia_semana, hora_inicio),
    -- Defensa en la base de datos: dos bloques del mismo día no pueden superponerse
    CONSTRAINT ex_bloque_horario_sin_cruce EXCLUDE USING gist (
        anio_escolar_id WITH =,
        dia_semana WITH =,
        tsrange(DATE '2000-01-01' + hora_inicio, DATE '2000-01-01' + hora_fin) WITH &&
    )
);

-- Periodo en que los docentes pueden registrar su disponibilidad (UC-20, FE-1)
CREATE TABLE horarios.periodo_registro_disponibilidad (
    id               VARCHAR(36) PRIMARY KEY,
    anio_escolar_id  VARCHAR(36) NOT NULL UNIQUE REFERENCES academico.anio_escolar (id),
    fecha_hora_inicio TIMESTAMPTZ NOT NULL,
    fecha_hora_fin    TIMESTAMPTZ NOT NULL,
    estado           VARCHAR(10) NOT NULL DEFAULT 'HABILITADO' CHECK (estado IN ('HABILITADO', 'CERRADO')),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_periodo_disponibilidad CHECK (fecha_hora_fin > fecha_hora_inicio)
);

CREATE TABLE horarios.vinculacion_docente (
    id                       VARCHAR(36) PRIMARY KEY,
    docente_id               VARCHAR(36) NOT NULL REFERENCES seguridad.docente (id),
    anio_escolar_id          VARCHAR(36) NOT NULL REFERENCES academico.anio_escolar (id),
    habilitado               BOOLEAN     NOT NULL DEFAULT TRUE,
    carga_semanal_referencia INTEGER     NOT NULL DEFAULT 0 CHECK (carga_semanal_referencia >= 0),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_vinculacion_docente UNIQUE (docente_id, anio_escolar_id)
);

-- Cursos que un docente puede dictar (RF-47: solo se ofrecen los docentes asociados al curso)
CREATE TABLE horarios.asociacion_docente_curso (
    id              VARCHAR(36) PRIMARY KEY,
    vinculacion_id  VARCHAR(36) NOT NULL REFERENCES horarios.vinculacion_docente (id),
    curso_id        VARCHAR(36) NOT NULL REFERENCES academico.curso (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_asociacion_docente_curso UNIQUE (vinculacion_id, curso_id)
);

-- Bloques semanales de cada curso por grado, inicializados desde curso.horas_lectivas (C-10, RF-43)
CREATE TABLE horarios.carga_horaria_curso (
    id                VARCHAR(36) PRIMARY KEY,
    grado_id          VARCHAR(36) NOT NULL REFERENCES academico.grado (id),
    curso_id          VARCHAR(36) NOT NULL REFERENCES academico.curso (id),
    bloques_semanales INTEGER     NOT NULL CHECK (bloques_semanales > 0),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_carga_horaria UNIQUE (grado_id, curso_id)
);

-- Solo se conserva la última disponibilidad confirmada (RF-46, UC-20 FA-1)
CREATE TABLE horarios.disponibilidad_docente (
    id                       VARCHAR(36) PRIMARY KEY,
    vinculacion_id           VARCHAR(36) NOT NULL UNIQUE REFERENCES horarios.vinculacion_docente (id),
    fecha_hora_confirmacion  TIMESTAMPTZ NOT NULL,
    observaciones            TEXT,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE horarios.bloque_disponible (
    id                 VARCHAR(36) PRIMARY KEY,
    disponibilidad_id  VARCHAR(36) NOT NULL REFERENCES horarios.disponibilidad_docente (id),
    bloque_id          VARCHAR(36) NOT NULL REFERENCES horarios.bloque_horario (id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_bloque_disponible UNIQUE (disponibilidad_id, bloque_id)
);

-- Un solo borrador por año; la versión protege las ediciones concurrentes (If-Match → 409)
CREATE TABLE horarios.horario_borrador (
    id                   VARCHAR(36) PRIMARY KEY,
    anio_escolar_id      VARCHAR(36) NOT NULL UNIQUE REFERENCES academico.anio_escolar (id),
    fecha_creacion       TIMESTAMPTZ NOT NULL,
    fecha_actualizacion  TIMESTAMPTZ NOT NULL,
    version              BIGINT      NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Los dos índices únicos son la última defensa contra cruces de docente y de sección (RF-41, arquitectura § 9.3)
CREATE TABLE horarios.asignacion_horario (
    id                          VARCHAR(36) PRIMARY KEY,
    horario_borrador_id         VARCHAR(36) NOT NULL REFERENCES horarios.horario_borrador (id),
    bloque_id                   VARCHAR(36) NOT NULL REFERENCES horarios.bloque_horario (id),
    seccion_id                  VARCHAR(36) NOT NULL REFERENCES academico.seccion (id),
    curso_id                    VARCHAR(36) NOT NULL REFERENCES academico.curso (id),
    docente_id                  VARCHAR(36) NOT NULL REFERENCES seguridad.docente (id),
    asociacion_docente_curso_id VARCHAR(36) NOT NULL REFERENCES horarios.asociacion_docente_curso (id),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_asignacion_docente_bloque UNIQUE (horario_borrador_id, bloque_id, docente_id),
    CONSTRAINT ux_asignacion_seccion_bloque UNIQUE (horario_borrador_id, bloque_id, seccion_id)
);

-- Horario publicado: copia inmutable del borrador (S-15, RF-44, RF-45)
CREATE TABLE horarios.horario_publicado (
    id                      VARCHAR(36) PRIMARY KEY,
    anio_escolar_id         VARCHAR(36) NOT NULL REFERENCES academico.anio_escolar (id),
    numero_version          INTEGER     NOT NULL CHECK (numero_version > 0),
    fecha_hora_publicacion  TIMESTAMPTZ NOT NULL,
    vigente                 BOOLEAN     NOT NULL,
    publicado_por           VARCHAR(36) NOT NULL REFERENCES seguridad.usuario (id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_horario_publicado_version UNIQUE (anio_escolar_id, numero_version)
);

-- A lo sumo una versión vigente por año
CREATE UNIQUE INDEX ux_horario_publicado_vigente ON horarios.horario_publicado (anio_escolar_id) WHERE vigente;

CREATE TABLE horarios.asignacion_publicada (
    id                    VARCHAR(36) PRIMARY KEY,
    horario_publicado_id  VARCHAR(36) NOT NULL REFERENCES horarios.horario_publicado (id),
    bloque_id             VARCHAR(36) NOT NULL REFERENCES horarios.bloque_horario (id),
    seccion_id            VARCHAR(36) NOT NULL REFERENCES academico.seccion (id),
    curso_id              VARCHAR(36) NOT NULL REFERENCES academico.curso (id),
    docente_id            VARCHAR(36) NOT NULL REFERENCES seguridad.docente (id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_publicada_docente_bloque UNIQUE (horario_publicado_id, bloque_id, docente_id),
    CONSTRAINT ux_publicada_seccion_bloque UNIQUE (horario_publicado_id, bloque_id, seccion_id)
);

CREATE INDEX ix_asignacion_publicada_docente ON horarios.asignacion_publicada (docente_id);
CREATE INDEX ix_asignacion_publicada_seccion ON horarios.asignacion_publicada (seccion_id);

-- Inmutabilidad (RF-44): las asignaciones publicadas no se modifican ni se borran, y de un horario publicado
-- solo puede cambiar la marca "vigente" (al publicar una versión nueva, la anterior deja de ser vigente).
CREATE FUNCTION horarios.rechazar_modificacion_publicada() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'los horarios publicados son inmutables';
END
$$;

CREATE TRIGGER tr_asignacion_publicada_inmutable
    BEFORE UPDATE OR DELETE ON horarios.asignacion_publicada
    FOR EACH ROW EXECUTE FUNCTION horarios.rechazar_modificacion_publicada();

CREATE FUNCTION horarios.proteger_horario_publicado() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'los horarios publicados son inmutables';
    END IF;
    IF NEW.anio_escolar_id <> OLD.anio_escolar_id
        OR NEW.numero_version <> OLD.numero_version
        OR NEW.fecha_hora_publicacion <> OLD.fecha_hora_publicacion
        OR NEW.publicado_por <> OLD.publicado_por THEN
        RAISE EXCEPTION 'los horarios publicados son inmutables';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER tr_horario_publicado_inmutable
    BEFORE UPDATE OR DELETE ON horarios.horario_publicado
    FOR EACH ROW EXECUTE FUNCTION horarios.proteger_horario_publicado();
