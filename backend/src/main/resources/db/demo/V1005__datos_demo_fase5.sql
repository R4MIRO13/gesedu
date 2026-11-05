-- Semilla de la fase 5 (evaluación): solo con el perfil demo.
-- 1) Asignaciones docente-curso-sección del año 2027, con los responsables del borrador del horario (S-13); al publicar el
--    horario se sincronizan y no cambian si el responsable es el mismo.
-- 2) Logros del bimestre I de 2027 de todos los alumnos matriculados. Matemática de 3° A (la dicta Fernando Medina, docente2@)
--    queda INCOMPLETA a propósito (a dos alumnos les falta la competencia C3) para mostrar el aviso de UC-15 y que UC-18 no
--    deje consolidar hasta que Fernando los complete. Lucía García Rojas (2026-0315) tiene todo registrado.
-- 3) Un año anterior COMPLETO: 2° A de 2026 (año cerrado) con sus cuatro bimestres consolidados, las notas anuales y la
--    libreta FINAL de cada alumno, para mostrar la libreta final con el apoderado de Valeria Torres (familia.torres).
-- Los niveles salen de un cálculo determinista sobre códigos estables (no de uuid), así que la semilla es reproducible.

-- ---- 1) Asignaciones 2027 ----
INSERT INTO evaluacion.asignacion_docente (id, docente_id, curso_id, seccion_id, regla_consolidacion)
SELECT DISTINCT ON (ah.curso_id, ah.seccion_id) uuidv7()::text, ah.docente_id, ah.curso_id, ah.seccion_id, 'PROMEDIO_PONDERADO'
FROM horarios.asignacion_horario ah
ORDER BY ah.curso_id, ah.seccion_id, ah.docente_id;

-- Como en el wireframe de «Configuración del curso»: Matemática de 3° A usa la moda
UPDATE evaluacion.asignacion_docente a
SET regla_consolidacion = 'MODA'
FROM academico.curso c, academico.seccion s, academico.grado g
WHERE c.id = a.curso_id AND c.slug = 'matematica'
  AND s.id = a.seccion_id AND g.id = s.grado_id AND g.numero_nivel = 3 AND s.letra = 'A';

-- ---- 2) Logros del bimestre I de 2027 ----
INSERT INTO evaluacion.registro_logro (id, asignacion_id, alumno_id, competencia_id, bimestre_id, nivel_logro, fecha_registro)
SELECT uuidv7()::text, a.id, m.alumno_id, k.id, b.id,
       (ARRAY['AD', 'A', 'A', 'A', 'B', 'B'])[1 + abs(hashtext(al.codigo_alumno || c.slug || k.codigo)) % 6],
       TIMESTAMPTZ '2027-03-12 10:00:00-05'
FROM evaluacion.asignacion_docente a
JOIN academico.curso c ON c.id = a.curso_id
JOIN academico.seccion s ON s.id = a.seccion_id
JOIN academico.grado g ON g.id = s.grado_id
JOIN academico.anio_escolar an ON an.id = s.anio_escolar_id AND an.anio = 2027
JOIN academico.bimestre b ON b.anio_escolar_id = an.id AND b.numero = 1
JOIN matricula.matricula m ON m.seccion_id = s.id AND m.estado = 'CONFIRMADA'
JOIN seguridad.alumno al ON al.id = m.alumno_id
JOIN seguridad.usuario u ON u.id = al.usuario_id
JOIN academico.competencia k ON k.curso_id = c.id
WHERE NOT (
    -- Matemática de 3° A: a los dos últimos alumnos (por apellidos, sin contar a Lucía) les falta la competencia C3
    c.slug = 'matematica' AND g.numero_nivel = 3 AND s.letra = 'A' AND k.codigo = 'C3'
    AND al.codigo_alumno <> '2026-0315'
    AND m.alumno_id IN (
        SELECT m2.alumno_id
        FROM matricula.matricula m2
        JOIN seguridad.alumno al2 ON al2.id = m2.alumno_id
        JOIN seguridad.usuario u2 ON u2.id = al2.usuario_id
        WHERE m2.seccion_id = s.id AND m2.estado = 'CONFIRMADA' AND al2.codigo_alumno <> '2026-0315'
        ORDER BY u2.apellidos DESC, u2.nombres DESC, al2.codigo_alumno DESC
        LIMIT 2));

-- ---- 3) Año anterior completo: 2° A de 2026 ----
INSERT INTO academico.bimestre (id, anio_escolar_id, numero, fecha_inicio, fecha_fin)
SELECT uuidv7()::text, a.id, b.numero, b.inicio, b.fin
FROM academico.anio_escolar a
CROSS JOIN (VALUES
    (1, DATE '2026-02-16', DATE '2026-04-24'),
    (2, DATE '2026-04-27', DATE '2026-07-03'),
    (3, DATE '2026-07-13', DATE '2026-09-18'),
    (4, DATE '2026-09-28', DATE '2026-12-18')
) AS b (numero, inicio, fin)
WHERE a.anio = 2026;

-- Los mismos cursos y competencias del 2027
INSERT INTO academico.curso (id, anio_escolar_id, nombre, slug, horas_lectivas)
SELECT uuidv7()::text, a26.id, c.nombre, c.slug, c.horas_lectivas
FROM academico.curso c
JOIN academico.anio_escolar a27 ON a27.id = c.anio_escolar_id AND a27.anio = 2027
CROSS JOIN academico.anio_escolar a26
WHERE a26.anio = 2026;

INSERT INTO academico.competencia (id, curso_id, codigo, nombre, nivel_esperado, orden)
SELECT uuidv7()::text, c26.id, k.codigo, k.nombre, k.nivel_esperado, k.orden
FROM academico.curso c26
JOIN academico.anio_escolar a26 ON a26.id = c26.anio_escolar_id AND a26.anio = 2026
JOIN academico.curso c27 ON c27.slug = c26.slug
JOIN academico.anio_escolar a27 ON a27.id = c27.anio_escolar_id AND a27.anio = 2027
JOIN academico.competencia k ON k.curso_id = c27.id;

-- Los responsables de 2° A son los docentes que hoy dictan cada curso
INSERT INTO evaluacion.asignacion_docente (id, docente_id, curso_id, seccion_id, regla_consolidacion)
SELECT uuidv7()::text,
       (SELECT a27.docente_id
        FROM evaluacion.asignacion_docente a27
        JOIN academico.curso c27 ON c27.id = a27.curso_id
        JOIN academico.anio_escolar an27 ON an27.id = c27.anio_escolar_id AND an27.anio = 2027
        WHERE c27.slug = c26.slug
        ORDER BY a27.id LIMIT 1),
       c26.id, s.id, 'PROMEDIO_PONDERADO'
FROM academico.curso c26
JOIN academico.anio_escolar a26 ON a26.id = c26.anio_escolar_id AND a26.anio = 2026
JOIN academico.grado g ON g.anio_escolar_id = a26.id AND g.numero_nivel = 2
JOIN academico.seccion s ON s.grado_id = g.id AND s.letra = 'A';

-- Logros de los cuatro bimestres de cada alumno matriculado en 2° A de 2026
INSERT INTO evaluacion.registro_logro (id, asignacion_id, alumno_id, competencia_id, bimestre_id, nivel_logro, fecha_registro)
SELECT uuidv7()::text, a.id, m.alumno_id, k.id, b.id,
       (ARRAY['AD', 'A', 'A', 'A', 'B', 'B', 'C'])[1 + abs(hashtext(al.codigo_alumno || c.slug || k.codigo || b.numero)) % 7],
       (b.fecha_fin - 7)::timestamp AT TIME ZONE 'America/Lima'
FROM evaluacion.asignacion_docente a
JOIN academico.curso c ON c.id = a.curso_id
JOIN academico.seccion s ON s.id = a.seccion_id
JOIN academico.anio_escolar an ON an.id = s.anio_escolar_id AND an.anio = 2026
JOIN academico.bimestre b ON b.anio_escolar_id = an.id
JOIN matricula.matricula m ON m.seccion_id = s.id AND m.estado = 'CONFIRMADA'
JOIN seguridad.alumno al ON al.id = m.alumno_id
JOIN academico.competencia k ON k.curso_id = c.id;

-- Una libreta FINAL por alumno
INSERT INTO evaluacion.libreta (id, alumno_id, anio_escolar_id, estado, fecha_actualizacion)
SELECT uuidv7()::text, x.alumno_id, x.anio_escolar_id, 'FINAL', TIMESTAMPTZ '2026-12-18 12:00:00-05'
FROM (SELECT DISTINCT l.alumno_id, b.anio_escolar_id
      FROM evaluacion.registro_logro l
      JOIN academico.bimestre b ON b.id = l.bimestre_id
      JOIN academico.anio_escolar an ON an.id = b.anio_escolar_id AND an.anio = 2026) x;

-- Notas bimestrales: con un logro por bimestre la regla da ese mismo nivel (S-66)
INSERT INTO evaluacion.nota_consolidada (id, alumno_id, competencia_id, asignacion_id, anio_escolar_id, bimestre_id, tipo, nivel_logro, calculada_en, libreta_id)
SELECT uuidv7()::text, l.alumno_id, l.competencia_id, l.asignacion_id, b.anio_escolar_id, b.id, 'BIMESTRAL', l.nivel_logro,
       (b.fecha_fin)::timestamp AT TIME ZONE 'America/Lima', lib.id
FROM evaluacion.registro_logro l
JOIN academico.bimestre b ON b.id = l.bimestre_id
JOIN academico.anio_escolar an ON an.id = b.anio_escolar_id AND an.anio = 2026
JOIN evaluacion.libreta lib ON lib.alumno_id = l.alumno_id AND lib.anio_escolar_id = b.anio_escolar_id;

-- Notas anuales: promedio con pesos iguales (AD=4, A=3, B=2, C=1) redondeado HALF_UP al nivel más cercano (RF-38)
INSERT INTO evaluacion.nota_consolidada (id, alumno_id, competencia_id, asignacion_id, anio_escolar_id, bimestre_id, tipo, nivel_logro, calculada_en, libreta_id)
SELECT uuidv7()::text, x.alumno_id, x.competencia_id, x.asignacion_id, x.anio_escolar_id, NULL, 'ANUAL',
       (ARRAY['C', 'B', 'A', 'AD'])[floor(x.promedio + 0.5)::int],
       TIMESTAMPTZ '2026-12-18 12:00:00-05', lib.id
FROM (SELECT l.alumno_id, l.competencia_id, l.asignacion_id, b.anio_escolar_id,
             avg(CASE l.nivel_logro WHEN 'AD' THEN 4 WHEN 'A' THEN 3 WHEN 'B' THEN 2 ELSE 1 END) AS promedio
      FROM evaluacion.registro_logro l
      JOIN academico.bimestre b ON b.id = l.bimestre_id
      JOIN academico.anio_escolar an ON an.id = b.anio_escolar_id AND an.anio = 2026
      GROUP BY l.alumno_id, l.competencia_id, l.asignacion_id, b.anio_escolar_id) x
JOIN evaluacion.libreta lib ON lib.alumno_id = x.alumno_id AND lib.anio_escolar_id = x.anio_escolar_id;

-- Los cuatro bimestres de 2026 están consolidados
INSERT INTO evaluacion.consolidacion_bimestral (id, bimestre_id, fecha_consolidacion, estado)
SELECT uuidv7()::text, b.id, (b.fecha_fin)::timestamp AT TIME ZONE 'America/Lima', 'COMPLETADA'
FROM academico.bimestre b
JOIN academico.anio_escolar an ON an.id = b.anio_escolar_id AND an.anio = 2026;
