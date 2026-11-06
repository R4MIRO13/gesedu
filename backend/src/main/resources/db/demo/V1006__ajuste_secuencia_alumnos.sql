-- Los datos de demo insertan alumnos con códigos propios (2026-04xx, 2027-05xx) después de V13: la secuencia de códigos
-- debe continuar después del mayor, para que el primer alumno nuevo de la demo no choque con la semilla.
SELECT setval('seguridad.seq_codigo_alumno',
              GREATEST(COALESCE(MAX(CAST(substring(codigo_alumno FROM 6) AS integer)), 0) + 1,
                       (SELECT last_value FROM seguridad.seq_codigo_alumno)), FALSE)
FROM seguridad.alumno
WHERE codigo_alumno ~ '^[0-9]{4}-[0-9]+$';
