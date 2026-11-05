-- Datos semilla de la DEMO para la fase 3 (perfil demo; nunca en producción): configuración académica y horarios.
-- Deja listos, con la contraseña Gesedu2027!, los usuarios para probar cada caso de uso nuevo:
--   directivo@ / admin@     UC-13 (crear el año 2028), UC-14 (cursos y copia de 2027), secciones, configuración de horarios,
--                           UC-22 (publicar) y UC-23
--   docente@                UC-20 con la disponibilidad ya confirmada (FA-1, FA-2: desmarcar bloques) y UC-23 (sus clases)
--   docente.nuevo@          UC-20 desde cero: vinculada al año 2027 y sin disponibilidad confirmada (flujo básico y FA-2)
--   docente2@               UC-23 con otro docente (no ve las clases de los demás)
--   alumno@ / apoderado@    UC-23 (3° A) y familia.torres@ (1° A, hijo Tomás)

-- Dos docentes nuevas: Elena Torres (Comunicación, sin acceso) y Marta Ibáñez (Inglés, con cuenta para UC-20 desde cero)
INSERT INTO seguridad.usuario (id, nombres, apellidos, documento_identidad, correo, hash_contrasena, activo, rol_acceso_id)
SELECT uuidv7()::text, d.nombres, d.apellidos, d.dni, d.correo,
       CASE WHEN d.correo IS NULL THEN NULL ELSE crypt('Gesedu2027!', gen_salt('bf', 12)) END,
       d.correo IS NOT NULL, r.id
FROM (VALUES
    ('Elena', 'Torres Rivas', '41889977', NULL),
    ('Marta', 'Ibáñez Ruiz',  '42335588', 'docente.nuevo@demo.gesedu.pe')
) AS d (nombres, apellidos, dni, correo)
CROSS JOIN seguridad.rol_acceso r WHERE r.codigo = 'DOCENTE';

INSERT INTO seguridad.docente (id, usuario_id, codigo_docente, especialidad)
SELECT uuidv7()::text, u.id, d.codigo, d.especialidad
FROM (VALUES
    ('41889977', 'DOC-009', 'Comunicación'),
    ('42335588', 'DOC-010', 'Inglés')
) AS d (dni, codigo, especialidad)
JOIN seguridad.usuario u ON u.documento_identidad = d.dni;

-- Fernando Medina también inicia sesión (UC-23: un docente solo ve lo suyo)
UPDATE seguridad.usuario
SET correo = 'docente2@demo.gesedu.pe', hash_contrasena = crypt('Gesedu2027!', gen_salt('bf', 12)), activo = TRUE
WHERE documento_identidad = '10254789';

-- Vinculación 2027 y cursos que dicta cada una (RF-47)
INSERT INTO horarios.vinculacion_docente (id, docente_id, anio_escolar_id, habilitado, carga_semanal_referencia)
SELECT uuidv7()::text, d.id, a.id, TRUE, 0
FROM seguridad.docente d CROSS JOIN academico.anio_escolar a
WHERE a.anio = 2027 AND d.codigo_docente IN ('DOC-009', 'DOC-010');

INSERT INTO horarios.asociacion_docente_curso (id, vinculacion_id, curso_id)
SELECT uuidv7()::text, v.id, c.id
FROM (VALUES ('DOC-009', 'comunicacion'), ('DOC-010', 'ingles')) AS x (codigo, slug)
JOIN seguridad.docente d ON d.codigo_docente = x.codigo
JOIN horarios.vinculacion_docente v ON v.docente_id = d.id
JOIN academico.curso c ON c.slug = x.slug AND c.anio_escolar_id = v.anio_escolar_id;

-- Elena confirmó todos los bloques; Marta no ha confirmado nada (UC-20 desde cero)
INSERT INTO horarios.disponibilidad_docente (id, vinculacion_id, fecha_hora_confirmacion)
SELECT uuidv7()::text, v.id, TIMESTAMPTZ '2027-01-21 09:30:00-05'
FROM horarios.vinculacion_docente v JOIN seguridad.docente d ON d.id = v.docente_id WHERE d.codigo_docente = 'DOC-009';

INSERT INTO horarios.bloque_disponible (id, disponibilidad_id, bloque_id)
SELECT uuidv7()::text, dd.id, b.id
FROM horarios.disponibilidad_docente dd
JOIN horarios.vinculacion_docente v ON v.id = dd.vinculacion_id
JOIN seguridad.docente d ON d.id = v.docente_id AND d.codigo_docente = 'DOC-009'
CROSS JOIN horarios.bloque_horario b WHERE b.anio_escolar_id = v.anio_escolar_id;

-- Carga semanal de referencia de cada docente: los bloques que dicta en un horario completo (UC-20, FA-2)
UPDATE horarios.vinculacion_docente v
SET carga_semanal_referencia = c.carga
FROM (VALUES
    ('DOC-001', 20), ('DOC-002', 15), ('DOC-003', 15), ('DOC-004', 25), ('DOC-005', 40), ('DOC-006', 30),
    ('DOC-007', 30), ('DOC-008', 20), ('DOC-009', 25), ('DOC-010', 10)
) AS c (codigo, carga)
JOIN seguridad.docente d ON d.codigo_docente = c.codigo
WHERE v.docente_id = d.id;

-- Periodo de registro de disponibilidad abierto hasta el 30/06/2027: la demo corre con el reloj de 2027 (RelojDemoConfig)
INSERT INTO horarios.periodo_registro_disponibilidad (id, anio_escolar_id, fecha_hora_inicio, fecha_hora_fin, estado)
SELECT uuidv7()::text, a.id, now() - INTERVAL '7 days', TIMESTAMPTZ '2027-06-30 23:59:00-05', 'HABILITADO'
FROM academico.anio_escolar a WHERE a.anio = 2027;

-- Borrador 2027 casi completo (UC-22): 8 secciones con sus 22 horas, 3° A con 19 (faltan 1 h de Inglés y 2 h de
-- Educación Física) y 4° B con 23 (1 h de Educación Física de más). Sin cruces: lo generó el mismo criterio del
-- validador (un docente o una sección por bloque, solo bloques disponibles). Quedan en el borrador las 20 clases de
-- V1001 (Rosa en 4° B el martes de 9:15 y las 19 de 3° A).
INSERT INTO horarios.asignacion_horario (id, horario_borrador_id, bloque_id, seccion_id, curso_id, docente_id,
                                          asociacion_docente_curso_id)
SELECT uuidv7()::text, hb.id, b.id, s.id, c.id, d.id, ad.id
FROM (VALUES
('1', 'A', 'LUNES', TIME '07:45', 'ciencia-y-tecnologia', 'DOC-005'),
    ('1', 'A', 'LUNES', TIME '08:30', 'comunicacion', 'DOC-004'),
    ('1', 'A', 'LUNES', TIME '09:15', 'ciencias-sociales', 'DOC-006'),
    ('1', 'A', 'LUNES', TIME '10:15', 'ingles', 'DOC-007'),
    ('1', 'A', 'LUNES', TIME '11:45', 'matematica', 'DOC-002'),
    ('1', 'A', 'MARTES', TIME '07:45', 'ciencia-y-tecnologia', 'DOC-005'),
    ('1', 'A', 'MARTES', TIME '08:30', 'matematica', 'DOC-002'),
    ('1', 'A', 'MARTES', TIME '09:15', 'comunicacion', 'DOC-004'),
    ('1', 'A', 'MARTES', TIME '10:15', 'ingles', 'DOC-007'),
    ('1', 'A', 'MIERCOLES', TIME '07:45', 'ciencia-y-tecnologia', 'DOC-005'),
    ('1', 'A', 'MIERCOLES', TIME '08:30', 'matematica', 'DOC-002'),
    ('1', 'A', 'MIERCOLES', TIME '09:15', 'comunicacion', 'DOC-004'),
    ('1', 'A', 'MIERCOLES', TIME '10:15', 'ciencias-sociales', 'DOC-006'),
    ('1', 'A', 'MIERCOLES', TIME '11:00', 'educacion-fisica', 'DOC-008'),
    ('1', 'A', 'JUEVES', TIME '07:45', 'matematica', 'DOC-002'),
    ('1', 'A', 'JUEVES', TIME '08:30', 'comunicacion', 'DOC-004'),
    ('1', 'A', 'JUEVES', TIME '09:15', 'ciencias-sociales', 'DOC-006'),
    ('1', 'A', 'JUEVES', TIME '10:15', 'educacion-fisica', 'DOC-008'),
    ('1', 'A', 'VIERNES', TIME '07:45', 'ciencia-y-tecnologia', 'DOC-005'),
    ('1', 'A', 'VIERNES', TIME '08:30', 'matematica', 'DOC-002'),
    ('1', 'A', 'VIERNES', TIME '09:15', 'comunicacion', 'DOC-004'),
    ('1', 'A', 'VIERNES', TIME '10:15', 'ingles', 'DOC-007'),
    ('1', 'B', 'LUNES', TIME '07:45', 'comunicacion', 'DOC-004'),
    ('1', 'B', 'LUNES', TIME '08:30', 'ciencia-y-tecnologia', 'DOC-005'),
    ('1', 'B', 'LUNES', TIME '09:15', 'ingles', 'DOC-007'),
    ('1', 'B', 'LUNES', TIME '10:15', 'ciencias-sociales', 'DOC-006'),
    ('1', 'B', 'LUNES', TIME '12:30', 'matematica', 'DOC-002'),
    ('1', 'B', 'MARTES', TIME '07:45', 'matematica', 'DOC-002'),
    ('1', 'B', 'MARTES', TIME '08:30', 'comunicacion', 'DOC-004'),
    ('1', 'B', 'MARTES', TIME '09:15', 'ingles', 'DOC-007'),
    ('1', 'B', 'MARTES', TIME '10:15', 'educacion-fisica', 'DOC-008'),
    ('1', 'B', 'MIERCOLES', TIME '07:45', 'matematica', 'DOC-002'),
    ('1', 'B', 'MIERCOLES', TIME '08:30', 'ciencia-y-tecnologia', 'DOC-005'),
    ('1', 'B', 'MIERCOLES', TIME '09:15', 'ciencias-sociales', 'DOC-006'),
    ('1', 'B', 'MIERCOLES', TIME '10:15', 'comunicacion', 'DOC-004'),
    ('1', 'B', 'JUEVES', TIME '07:45', 'ciencia-y-tecnologia', 'DOC-005'),
    ('1', 'B', 'JUEVES', TIME '08:30', 'matematica', 'DOC-002'),
    ('1', 'B', 'JUEVES', TIME '09:15', 'comunicacion', 'DOC-004'),
    ('1', 'B', 'JUEVES', TIME '10:15', 'ingles', 'DOC-007'),
    ('1', 'B', 'VIERNES', TIME '07:45', 'matematica', 'DOC-002'),
    ('1', 'B', 'VIERNES', TIME '08:30', 'ciencia-y-tecnologia', 'DOC-005'),
    ('1', 'B', 'VIERNES', TIME '09:15', 'ciencias-sociales', 'DOC-006'),
    ('1', 'B', 'VIERNES', TIME '10:15', 'comunicacion', 'DOC-004'),
    ('1', 'B', 'VIERNES', TIME '11:00', 'educacion-fisica', 'DOC-008'),
    ('2', 'A', 'LUNES', TIME '07:45', 'matematica', 'DOC-003'),
    ('2', 'A', 'LUNES', TIME '08:30', 'ciencias-sociales', 'DOC-006'),
    ('2', 'A', 'LUNES', TIME '09:15', 'ciencia-y-tecnologia', 'DOC-005'),
    ('2', 'A', 'LUNES', TIME '10:15', 'comunicacion', 'DOC-004'),
    ('2', 'A', 'LUNES', TIME '11:00', 'educacion-fisica', 'DOC-008'),
    ('2', 'A', 'MARTES', TIME '07:45', 'matematica', 'DOC-003'),
    ('2', 'A', 'MARTES', TIME '08:30', 'ciencias-sociales', 'DOC-006'),
    ('2', 'A', 'MARTES', TIME '09:15', 'ciencia-y-tecnologia', 'DOC-005'),
    ('2', 'A', 'MARTES', TIME '10:15', 'comunicacion', 'DOC-004'),
    ('2', 'A', 'MIERCOLES', TIME '07:45', 'matematica', 'DOC-003'),
    ('2', 'A', 'MIERCOLES', TIME '08:30', 'comunicacion', 'DOC-004'),
    ('2', 'A', 'MIERCOLES', TIME '09:15', 'ciencia-y-tecnologia', 'DOC-005'),
    ('2', 'A', 'MIERCOLES', TIME '10:15', 'ingles', 'DOC-007'),
    ('2', 'A', 'JUEVES', TIME '07:45', 'matematica', 'DOC-003'),
    ('2', 'A', 'JUEVES', TIME '08:30', 'ciencia-y-tecnologia', 'DOC-005'),
    ('2', 'A', 'JUEVES', TIME '09:15', 'ingles', 'DOC-007'),
    ('2', 'A', 'JUEVES', TIME '10:15', 'comunicacion', 'DOC-004'),
    ('2', 'A', 'JUEVES', TIME '11:00', 'educacion-fisica', 'DOC-008'),
    ('2', 'A', 'VIERNES', TIME '07:45', 'matematica', 'DOC-003'),
    ('2', 'A', 'VIERNES', TIME '08:30', 'comunicacion', 'DOC-004'),
    ('2', 'A', 'VIERNES', TIME '09:15', 'ingles', 'DOC-007'),
    ('2', 'A', 'VIERNES', TIME '10:15', 'ciencias-sociales', 'DOC-006'),
    ('2', 'B', 'LUNES', TIME '07:45', 'ciencias-sociales', 'DOC-006'),
    ('2', 'B', 'LUNES', TIME '08:30', 'matematica', 'DOC-003'),
    ('2', 'B', 'LUNES', TIME '09:15', 'comunicacion', 'DOC-004'),
    ('2', 'B', 'LUNES', TIME '10:15', 'ciencia-y-tecnologia', 'DOC-005'),
    ('2', 'B', 'LUNES', TIME '11:00', 'ingles', 'DOC-007'),
    ('2', 'B', 'MARTES', TIME '07:45', 'ciencias-sociales', 'DOC-006'),
    ('2', 'B', 'MARTES', TIME '08:30', 'matematica', 'DOC-003'),
    ('2', 'B', 'MARTES', TIME '09:15', 'educacion-fisica', 'DOC-008'),
    ('2', 'B', 'MARTES', TIME '11:00', 'comunicacion', 'DOC-004'),
    ('2', 'B', 'MIERCOLES', TIME '07:45', 'comunicacion', 'DOC-004'),
    ('2', 'B', 'MIERCOLES', TIME '08:30', 'matematica', 'DOC-003'),
    ('2', 'B', 'MIERCOLES', TIME '09:15', 'ingles', 'DOC-007'),
    ('2', 'B', 'MIERCOLES', TIME '10:15', 'ciencia-y-tecnologia', 'DOC-005'),
    ('2', 'B', 'JUEVES', TIME '07:45', 'comunicacion', 'DOC-004'),
    ('2', 'B', 'JUEVES', TIME '08:30', 'matematica', 'DOC-003'),
    ('2', 'B', 'JUEVES', TIME '09:15', 'ciencia-y-tecnologia', 'DOC-005'),
    ('2', 'B', 'JUEVES', TIME '10:15', 'ciencias-sociales', 'DOC-006'),
    ('2', 'B', 'VIERNES', TIME '07:45', 'comunicacion', 'DOC-004'),
    ('2', 'B', 'VIERNES', TIME '08:30', 'matematica', 'DOC-003'),
    ('2', 'B', 'VIERNES', TIME '09:15', 'ciencia-y-tecnologia', 'DOC-005'),
    ('2', 'B', 'VIERNES', TIME '10:15', 'educacion-fisica', 'DOC-008'),
    ('2', 'B', 'VIERNES', TIME '11:00', 'ingles', 'DOC-007'),
    ('3', 'B', 'LUNES', TIME '07:45', 'matematica', 'DOC-001'),
    ('3', 'B', 'LUNES', TIME '08:30', 'comunicacion', 'DOC-009'),
    ('3', 'B', 'LUNES', TIME '11:00', 'ciencia-y-tecnologia', 'DOC-005'),
    ('3', 'B', 'LUNES', TIME '11:45', 'ingles', 'DOC-007'),
    ('3', 'B', 'MARTES', TIME '07:45', 'matematica', 'DOC-001'),
    ('3', 'B', 'MARTES', TIME '08:30', 'ingles', 'DOC-007'),
    ('3', 'B', 'MARTES', TIME '09:15', 'comunicacion', 'DOC-009'),
    ('3', 'B', 'MARTES', TIME '10:15', 'ciencias-sociales', 'DOC-006'),
    ('3', 'B', 'MIERCOLES', TIME '07:45', 'matematica', 'DOC-001'),
    ('3', 'B', 'MIERCOLES', TIME '08:30', 'comunicacion', 'DOC-009'),
    ('3', 'B', 'MIERCOLES', TIME '09:15', 'educacion-fisica', 'DOC-008'),
    ('3', 'B', 'MIERCOLES', TIME '11:00', 'ciencia-y-tecnologia', 'DOC-005'),
    ('3', 'B', 'JUEVES', TIME '07:45', 'matematica', 'DOC-001'),
    ('3', 'B', 'JUEVES', TIME '08:30', 'comunicacion', 'DOC-009'),
    ('3', 'B', 'JUEVES', TIME '10:15', 'ciencia-y-tecnologia', 'DOC-005'),
    ('3', 'B', 'JUEVES', TIME '11:00', 'ciencias-sociales', 'DOC-006'),
    ('3', 'B', 'JUEVES', TIME '11:45', 'ingles', 'DOC-007'),
    ('3', 'B', 'VIERNES', TIME '07:45', 'matematica', 'DOC-001'),
    ('3', 'B', 'VIERNES', TIME '08:30', 'comunicacion', 'DOC-009'),
    ('3', 'B', 'VIERNES', TIME '09:15', 'educacion-fisica', 'DOC-008'),
    ('3', 'B', 'VIERNES', TIME '10:15', 'ciencia-y-tecnologia', 'DOC-005'),
    ('3', 'B', 'VIERNES', TIME '11:00', 'ciencias-sociales', 'DOC-006'),
    ('4', 'A', 'LUNES', TIME '07:45', 'comunicacion', 'DOC-009'),
    ('4', 'A', 'LUNES', TIME '08:30', 'matematica', 'DOC-001'),
    ('4', 'A', 'LUNES', TIME '09:15', 'educacion-fisica', 'DOC-008'),
    ('4', 'A', 'LUNES', TIME '11:45', 'ciencia-y-tecnologia', 'DOC-005'),
    ('4', 'A', 'MARTES', TIME '07:45', 'comunicacion', 'DOC-009'),
    ('4', 'A', 'MARTES', TIME '08:30', 'matematica', 'DOC-001'),
    ('4', 'A', 'MARTES', TIME '09:15', 'ciencias-sociales', 'DOC-006'),
    ('4', 'A', 'MARTES', TIME '11:00', 'ingles', 'DOC-007'),
    ('4', 'A', 'MIERCOLES', TIME '07:45', 'comunicacion', 'DOC-009'),
    ('4', 'A', 'MIERCOLES', TIME '08:30', 'matematica', 'DOC-001'),
    ('4', 'A', 'MIERCOLES', TIME '10:15', 'educacion-fisica', 'DOC-008'),
    ('4', 'A', 'MIERCOLES', TIME '11:00', 'ciencias-sociales', 'DOC-006'),
    ('4', 'A', 'MIERCOLES', TIME '11:45', 'ciencia-y-tecnologia', 'DOC-005'),
    ('4', 'A', 'JUEVES', TIME '07:45', 'comunicacion', 'DOC-009'),
    ('4', 'A', 'JUEVES', TIME '08:30', 'ciencias-sociales', 'DOC-006'),
    ('4', 'A', 'JUEVES', TIME '09:15', 'matematica', 'DOC-001'),
    ('4', 'A', 'JUEVES', TIME '11:00', 'ciencia-y-tecnologia', 'DOC-005'),
    ('4', 'A', 'JUEVES', TIME '12:30', 'ingles', 'DOC-007'),
    ('4', 'A', 'VIERNES', TIME '07:45', 'comunicacion', 'DOC-009'),
    ('4', 'A', 'VIERNES', TIME '08:30', 'matematica', 'DOC-001'),
    ('4', 'A', 'VIERNES', TIME '11:00', 'ciencia-y-tecnologia', 'DOC-005'),
    ('4', 'A', 'VIERNES', TIME '11:45', 'ingles', 'DOC-007'),
    ('4', 'B', 'LUNES', TIME '07:45', 'ingles', 'DOC-007'),
    ('4', 'B', 'LUNES', TIME '08:30', 'educacion-fisica', 'DOC-008'),
    ('4', 'B', 'LUNES', TIME '09:15', 'matematica', 'DOC-001'),
    ('4', 'B', 'LUNES', TIME '10:15', 'comunicacion', 'DOC-009'),
    ('4', 'B', 'LUNES', TIME '12:30', 'ciencia-y-tecnologia', 'DOC-005'),
    ('4', 'B', 'MARTES', TIME '07:45', 'ingles', 'DOC-007'),
    ('4', 'B', 'MARTES', TIME '08:30', 'comunicacion', 'DOC-009'),
    ('4', 'B', 'MARTES', TIME '11:45', 'educacion-fisica', 'DOC-008'),
    ('4', 'B', 'MARTES', TIME '12:30', 'ciencia-y-tecnologia', 'DOC-005'),
    ('4', 'B', 'MIERCOLES', TIME '07:45', 'ciencias-sociales', 'DOC-006'),
    ('4', 'B', 'MIERCOLES', TIME '08:30', 'educacion-fisica', 'DOC-008'),
    ('4', 'B', 'MIERCOLES', TIME '09:15', 'matematica', 'DOC-001'),
    ('4', 'B', 'MIERCOLES', TIME '10:15', 'comunicacion', 'DOC-009'),
    ('4', 'B', 'JUEVES', TIME '07:45', 'ciencias-sociales', 'DOC-006'),
    ('4', 'B', 'JUEVES', TIME '08:30', 'matematica', 'DOC-001'),
    ('4', 'B', 'JUEVES', TIME '09:15', 'comunicacion', 'DOC-009'),
    ('4', 'B', 'JUEVES', TIME '11:45', 'ciencia-y-tecnologia', 'DOC-005'),
    ('4', 'B', 'VIERNES', TIME '07:45', 'ciencias-sociales', 'DOC-006'),
    ('4', 'B', 'VIERNES', TIME '08:30', 'ingles', 'DOC-007'),
    ('4', 'B', 'VIERNES', TIME '09:15', 'matematica', 'DOC-001'),
    ('4', 'B', 'VIERNES', TIME '10:15', 'comunicacion', 'DOC-009'),
    ('4', 'B', 'VIERNES', TIME '11:45', 'ciencia-y-tecnologia', 'DOC-005'),
    ('5', 'A', 'LUNES', TIME '07:45', 'educacion-fisica', 'DOC-008'),
    ('5', 'A', 'LUNES', TIME '08:30', 'ingles', 'DOC-007'),
    ('5', 'A', 'LUNES', TIME '09:15', 'comunicacion', 'DOC-009'),
    ('5', 'A', 'LUNES', TIME '10:15', 'matematica', 'DOC-001'),
    ('5', 'A', 'LUNES', TIME '11:00', 'ciencias-sociales', 'DOC-006'),
    ('5', 'A', 'LUNES', TIME '13:15', 'ciencia-y-tecnologia', 'DOC-005'),
    ('5', 'A', 'MARTES', TIME '10:15', 'matematica', 'DOC-001'),
    ('5', 'A', 'MARTES', TIME '11:00', 'comunicacion', 'DOC-009'),
    ('5', 'A', 'MIERCOLES', TIME '07:45', 'educacion-fisica', 'DOC-008'),
    ('5', 'A', 'MIERCOLES', TIME '08:30', 'ciencias-sociales', 'DOC-006'),
    ('5', 'A', 'MIERCOLES', TIME '09:15', 'comunicacion', 'DOC-009'),
    ('5', 'A', 'MIERCOLES', TIME '10:15', 'matematica', 'DOC-001'),
    ('5', 'A', 'MIERCOLES', TIME '12:30', 'ciencia-y-tecnologia', 'DOC-005'),
    ('5', 'A', 'JUEVES', TIME '07:45', 'ingles', 'DOC-007'),
    ('5', 'A', 'JUEVES', TIME '10:15', 'matematica', 'DOC-001'),
    ('5', 'A', 'JUEVES', TIME '11:00', 'comunicacion', 'DOC-009'),
    ('5', 'A', 'JUEVES', TIME '12:30', 'ciencia-y-tecnologia', 'DOC-005'),
    ('5', 'A', 'VIERNES', TIME '07:45', 'ingles', 'DOC-007'),
    ('5', 'A', 'VIERNES', TIME '08:30', 'ciencias-sociales', 'DOC-006'),
    ('5', 'A', 'VIERNES', TIME '09:15', 'comunicacion', 'DOC-009'),
    ('5', 'A', 'VIERNES', TIME '10:15', 'matematica', 'DOC-001'),
    ('5', 'A', 'VIERNES', TIME '12:30', 'ciencia-y-tecnologia', 'DOC-005'),
    ('5', 'B', 'LUNES', TIME '09:15', 'matematica', 'DOC-003'),
    ('5', 'B', 'LUNES', TIME '11:00', 'comunicacion', 'DOC-009'),
    ('5', 'B', 'MARTES', TIME '07:45', 'educacion-fisica', 'DOC-008'),
    ('5', 'B', 'MARTES', TIME '10:15', 'matematica', 'DOC-003'),
    ('5', 'B', 'MARTES', TIME '11:00', 'ciencias-sociales', 'DOC-006'),
    ('5', 'B', 'MARTES', TIME '11:45', 'comunicacion', 'DOC-009'),
    ('5', 'B', 'MARTES', TIME '13:15', 'ciencia-y-tecnologia', 'DOC-005'),
    ('5', 'B', 'MIERCOLES', TIME '09:15', 'matematica', 'DOC-003'),
    ('5', 'B', 'MIERCOLES', TIME '11:00', 'comunicacion', 'DOC-009'),
    ('5', 'B', 'MIERCOLES', TIME '11:45', 'ingles', 'DOC-007'),
    ('5', 'B', 'MIERCOLES', TIME '13:15', 'ciencia-y-tecnologia', 'DOC-005'),
    ('5', 'B', 'JUEVES', TIME '08:30', 'ingles', 'DOC-007'),
    ('5', 'B', 'JUEVES', TIME '09:15', 'matematica', 'DOC-003'),
    ('5', 'B', 'JUEVES', TIME '10:15', 'comunicacion', 'DOC-009'),
    ('5', 'B', 'JUEVES', TIME '11:45', 'ciencias-sociales', 'DOC-006'),
    ('5', 'B', 'JUEVES', TIME '13:15', 'ciencia-y-tecnologia', 'DOC-005'),
    ('5', 'B', 'VIERNES', TIME '07:45', 'educacion-fisica', 'DOC-008'),
    ('5', 'B', 'VIERNES', TIME '09:15', 'matematica', 'DOC-003'),
    ('5', 'B', 'VIERNES', TIME '11:00', 'comunicacion', 'DOC-009'),
    ('5', 'B', 'VIERNES', TIME '11:45', 'ciencias-sociales', 'DOC-006'),
    ('5', 'B', 'VIERNES', TIME '12:30', 'ingles', 'DOC-007'),
    ('5', 'B', 'VIERNES', TIME '13:15', 'ciencia-y-tecnologia', 'DOC-005')
) AS x (nivel, letra, dia, hora, slug, docente)
JOIN academico.anio_escolar a ON a.anio = 2027
JOIN horarios.horario_borrador hb ON hb.anio_escolar_id = a.id
JOIN horarios.bloque_horario b ON b.anio_escolar_id = a.id AND b.dia_semana = x.dia AND b.hora_inicio = x.hora
JOIN academico.grado g ON g.anio_escolar_id = a.id AND g.numero_nivel = x.nivel::int
JOIN academico.seccion s ON s.grado_id = g.id AND s.letra = x.letra
JOIN academico.curso c ON c.anio_escolar_id = a.id AND c.slug = x.slug
JOIN seguridad.docente d ON d.codigo_docente = x.docente
JOIN horarios.vinculacion_docente v ON v.docente_id = d.id AND v.anio_escolar_id = a.id
JOIN horarios.asociacion_docente_curso ad ON ad.vinculacion_id = v.id AND ad.curso_id = c.id;
