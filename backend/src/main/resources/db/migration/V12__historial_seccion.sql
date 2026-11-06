-- Historial de secciones de una matrícula (RF-05, UC-10/UC-11): desde qué fecha el alumno pertenece a cada sección.
-- Sirve para que un reporte de asistencia de un mes pasado atribuya cada día a la sección que el alumno tenía entonces.
-- Migración hacia adelante: las matrículas existentes arrancan con su sección actual desde la fecha de matrícula (los
-- traslados anteriores a esta migración no se conocen; solo quedan en la auditoría CAMBIAR_SECCION_MATRICULA).

CREATE TABLE matricula.historial_seccion (
    id           VARCHAR(36) PRIMARY KEY,
    matricula_id VARCHAR(36) NOT NULL REFERENCES matricula.matricula (id),
    seccion_id   VARCHAR(36) NOT NULL REFERENCES academico.seccion (id),
    desde        DATE        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_historial_seccion_matricula ON matricula.historial_seccion (matricula_id, desde);
CREATE INDEX ix_historial_seccion_seccion ON matricula.historial_seccion (seccion_id);

INSERT INTO matricula.historial_seccion (id, matricula_id, seccion_id, desde)
SELECT uuidv7()::text, m.id, m.seccion_id, m.fecha_matricula
FROM matricula.matricula m;
