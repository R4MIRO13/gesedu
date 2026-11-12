-- Preparación de los smoke tests de Playwright (docs/pruebas/compatibilidad.md). Se ejecuta antes de cada navegador
-- (proyecto preparar-<navegador>) para que chromium, firefox y msedge partan de los mismos datos de la demo.
--
-- SOLO para la base de desarrollo local con la semilla de demo (perfiles local,demo). La guardia aborta en cualquier otra
-- base. No toca el esquema auditoria (es de solo inserción): las entradas de cada corrida se acumulan, y está bien.
-- Lo ejecuta e2e/preparar.setup.ts:
--   docker exec -i -e PGPASSWORD=gesedu_app gesedu-postgres psql -h 127.0.0.1 -U gesedu_app -d gesedu -v ON_ERROR_STOP=1

\set ON_ERROR_STOP on

DO $$
BEGIN
    IF current_database() <> 'gesedu' THEN
        RAISE EXCEPTION 'preparar-datos.sql solo corre en la base local «gesedu» (es %)', current_database();
    END IF;
    IF NOT EXISTS (SELECT 1 FROM seguridad.usuario WHERE correo = 'admin@demo.gesedu.pe')
       OR NOT EXISTS (SELECT 1 FROM seguridad.usuario WHERE correo = 'docente2@demo.gesedu.pe')
       OR NOT EXISTS (SELECT 1 FROM academico.anio_escolar WHERE anio = 2027) THEN
        RAISE EXCEPTION 'La base no tiene la semilla de demo (arranca el backend con los perfiles local,demo)';
    END IF;
END $$;

BEGIN;

-- 1. Horario 2027 publicado (como el paso 12 del guion o el atajo docs/demo/atajos/publicar-horario.sql), solo si falta
WITH nueva AS (
    INSERT INTO horarios.horario_publicado (id, anio_escolar_id, numero_version, fecha_hora_publicacion, vigente, publicado_por)
    SELECT uuidv7()::text, a.id, 1, now(), TRUE, u.id
    FROM academico.anio_escolar a, seguridad.usuario u
    WHERE a.anio = 2027 AND u.correo = 'admin@demo.gesedu.pe'
      AND NOT EXISTS (SELECT 1 FROM horarios.horario_publicado hp WHERE hp.anio_escolar_id = a.id)
    RETURNING id, anio_escolar_id
)
INSERT INTO horarios.asignacion_publicada (id, horario_publicado_id, bloque_id, seccion_id, curso_id, docente_id)
SELECT uuidv7()::text, n.id, ah.bloque_id, ah.seccion_id, ah.curso_id, ah.docente_id
FROM nueva n
JOIN horarios.horario_borrador hb ON hb.anio_escolar_id = n.anio_escolar_id
JOIN horarios.asignacion_horario ah ON ah.horario_borrador_id = hb.id;

-- 2. Pase de lista de 3° A del lunes 15/03/2027 sin registrar (UC-08)
DELETE FROM asistencia.asistencia_alumno aa
USING matricula.matricula m, academico.seccion s, academico.grado g, academico.anio_escolar a
WHERE aa.alumno_id = m.alumno_id AND m.seccion_id = s.id AND s.grado_id = g.id AND s.anio_escolar_id = a.id
  AND a.anio = 2027 AND g.numero_nivel = 3 AND s.letra = 'A' AND m.estado = 'CONFIRMADA'
  AND aa.fecha = DATE '2027-03-15';

-- 3. Marcación de Rosa (DOC-001) del lunes 15/03/2027 sin registrar (UC-07)
DELETE FROM asistencia.asistencia_docente ad
USING seguridad.docente d
WHERE ad.docente_id = d.id AND d.codigo_docente = 'DOC-001' AND ad.fecha = DATE '2027-03-15';

-- 4. Las dos celdas C3 de Matemática 3° A del bimestre I sin calificar, como en la semilla V1005 (UC-15)
DELETE FROM evaluacion.registro_logro l
USING evaluacion.asignacion_docente ad, academico.curso c, academico.seccion s, academico.grado g,
      academico.anio_escolar a, academico.competencia k, academico.bimestre b, seguridad.alumno al
WHERE l.asignacion_id = ad.id AND ad.curso_id = c.id AND ad.seccion_id = s.id AND s.grado_id = g.id
  AND s.anio_escolar_id = a.id AND l.competencia_id = k.id AND l.bimestre_id = b.id AND l.alumno_id = al.id
  AND a.anio = 2027 AND c.slug = 'matematica' AND g.numero_nivel = 3 AND s.letra = 'A'
  AND k.codigo = 'C3' AND b.numero = 1 AND al.codigo_alumno IN ('2027-0504', '2027-0505');

COMMIT;

SELECT 'preparado' AS estado,
       (SELECT count(*) FROM horarios.horario_publicado hp JOIN academico.anio_escolar a ON a.id = hp.anio_escolar_id
         WHERE a.anio = 2027 AND hp.vigente) AS horarios_vigentes_2027;
