-- Código de alumno (AAAA-NNNN): el número sale de una secuencia en lugar de MAX()+1 bajo un candado consultivo global.
-- El candado serializaba todas las altas de alumnos hasta el commit de la solicitud de matrícula completa (prueba k6
-- de UC-01, RNF-12). nextval no es transaccional: si una transacción se revierte quedan saltos en la numeración, y la
-- numeración continúa entre años (ya no reinicia cada año). Migración hacia adelante: no edita las anteriores.
CREATE SEQUENCE seguridad.seq_codigo_alumno START 1;

-- Continúa después del mayor número que ya exista, de cualquier año
SELECT setval('seguridad.seq_codigo_alumno', COALESCE(MAX(CAST(substring(codigo_alumno FROM 6) AS integer)), 0) + 1, FALSE)
FROM seguridad.alumno
WHERE codigo_alumno ~ '^[0-9]{4}-[0-9]+$';
