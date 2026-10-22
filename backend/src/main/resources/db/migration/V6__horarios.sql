-- Esquema horarios (mínimo de la demo del 23/10): solo los bloques horarios (RF-46).
-- No existe la entidad Aula: cada sección ocupa siempre la misma aula (ERS § 2.3).
-- Pendiente para después: disponibilidad, vinculaciones, borrador, asignaciones y horarios publicados.

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
