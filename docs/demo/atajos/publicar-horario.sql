-- Atajo de la demo (fase 4): publica la versión 1 del horario 2027 con las clases del borrador, tal como las dejó el
-- administrador en el paso 12 del guion (UC-22), sin recorrer ese paso. Sirve para llegar directo a la asistencia con una
-- base recién reiniciada, porque UC-07 y UC-12 necesitan un horario publicado.
-- Úsalo UNA vez y solo si todavía no publicaste el horario (si ya hay una versión vigente falla por el índice único):
--   docker exec -i -e PGPASSWORD=gesedu_app gesedu-postgres psql -h 127.0.0.1 -U gesedu_app -d gesedu < docs/demo/atajos/publicar-horario.sql
-- El borrador de la semilla no está completo (3° A tiene 19 h y 4° B 23 h): el atajo no valida completitud; para mostrar
-- esa validación hay que hacer el paso 12 con la interfaz.
WITH nueva AS (
    INSERT INTO horarios.horario_publicado (id, anio_escolar_id, numero_version, fecha_hora_publicacion, vigente, publicado_por)
    SELECT uuidv7()::text, a.id, 1, now(), TRUE, u.id
    FROM academico.anio_escolar a, seguridad.usuario u
    WHERE a.anio = 2027 AND u.correo = 'admin@demo.gesedu.pe'
    RETURNING id, anio_escolar_id
)
INSERT INTO horarios.asignacion_publicada (id, horario_publicado_id, bloque_id, seccion_id, curso_id, docente_id)
SELECT uuidv7()::text, n.id, ah.bloque_id, ah.seccion_id, ah.curso_id, ah.docente_id
FROM nueva n
JOIN horarios.horario_borrador hb ON hb.anio_escolar_id = n.anio_escolar_id
JOIN horarios.asignacion_horario ah ON ah.horario_borrador_id = hb.id;
