-- Datos semilla de la DEMO para la fase 4 (perfil demo; nunca en producción): asistencia.
-- La demo corre con un reloj propio (perfil demo): empieza el lunes 15/03/2027 a las 7:40 a. m. y avanza en tiempo real
-- (GESEDU_DEMO_RELOJ_INICIO lo cambia). Este archivo deja lo que ocurrió ANTES de ese lunes:
--   · 3° A (tutora Rosa Quispe, docente@) con seis alumnos matriculados: Lucía García (alumno@) y cinco más, para pasar lista
--   · asistencia de los alumnos de 3° A del 01/03 al 12/03 (con algunos días sin registro) para los reportes y el inicio de Lucía
--   · asistencia de los docentes del 01/03 al 12/03 y los cierres diarios de esas fechas; Rosa con la secuencia del wireframe
--   · el lunes 15/03: tres docentes ya marcaron (para «Asistencia docente») y Rosa NO, para que marque ella en la demo
-- Los docentes con clases en el borrador son DOC-001 a DOC-009 (Elena Torres, DOC-009, entra con V1003); la historia de
-- asistencia se siembra para DOC-001 a DOC-008 y Marta (DOC-010) no dicta. Quien no tiene historial figura como «no registrado».
-- Para UC-07 y UC-12 hace falta el horario publicado: ver docs/demo/atajos/publicar-horario.sql o el paso 12 del guion.

-- Apoderado sin acceso que reúne a los cinco alumnos nuevos
INSERT INTO seguridad.usuario (id, nombres, apellidos, documento_identidad, activo, rol_acceso_id)
SELECT uuidv7()::text, 'Familia', 'Demo Asistencia', '40200001', FALSE, r.id
FROM seguridad.rol_acceso r WHERE r.codigo = 'APODERADO';

INSERT INTO seguridad.apoderado (id, usuario_id)
SELECT uuidv7()::text, u.id FROM seguridad.usuario u WHERE u.documento_identidad = '40200001';

-- Cinco alumnos nuevos de 3° A (sin acceso), con su solicitud aprobada y matrícula 2027 confirmada
INSERT INTO seguridad.usuario (id, nombres, apellidos, documento_identidad, activo, rol_acceso_id)
SELECT uuidv7()::text, x.nombres, x.apellidos, x.dni, FALSE, r.id
FROM (VALUES
    ('Ana',      'Bravo Díaz',      '77200001'),
    ('Diego',    'Castro Soto',     '77200002'),
    ('Valentina','Huamán León',     '77200003'),
    ('Joaquín',  'Mendoza Ruiz',    '77200004'),
    ('Camila',   'Paredes Vega',    '77200005')
) AS x (nombres, apellidos, dni)
CROSS JOIN seguridad.rol_acceso r WHERE r.codigo = 'ALUMNO';

INSERT INTO seguridad.alumno (id, usuario_id, codigo_alumno, fecha_nacimiento, apoderado_id, parentesco_apoderado)
SELECT uuidv7()::text, ua.id, x.codigo, x.nacimiento, ap.id, 'MADRE'
FROM (VALUES
    ('77200001', '2027-0501', DATE '2012-03-14'),
    ('77200002', '2027-0502', DATE '2012-06-02'),
    ('77200003', '2027-0503', DATE '2012-09-21'),
    ('77200004', '2027-0504', DATE '2012-11-30'),
    ('77200005', '2027-0505', DATE '2012-01-08')
) AS x (dni, codigo, nacimiento)
JOIN seguridad.usuario ua ON ua.documento_identidad = x.dni
JOIN seguridad.usuario up ON up.documento_identidad = '40200001'
JOIN seguridad.apoderado ap ON ap.usuario_id = up.id;

INSERT INTO matricula.solicitud_matricula (id, codigo_seguimiento, anio_escolar_id, alumno_id, apoderado_id, grado_id,
                                            fecha_solicitud, tipo_estudiante, estado_documentacion, estado_admision,
                                            estado, costos_aceptados)
SELECT uuidv7()::text, 'MAT-2027-0046' || row_number() OVER (ORDER BY al.codigo_alumno), a.id, al.id, al.apoderado_id, g.id,
       DATE '2027-01-10', 'RECURRENTE', 'COMPLETA', 'NO_APLICA', 'APROBADA', TRUE
FROM academico.anio_escolar a
JOIN academico.grado g ON g.anio_escolar_id = a.id AND g.numero_nivel = 3
JOIN seguridad.alumno al ON al.codigo_alumno LIKE '2027-050%'
WHERE a.anio = 2027;

-- La sección ya cuenta estos cupos en sus vacantes (26 matriculados): solo se agregan las filas de matrícula
INSERT INTO matricula.matricula (id, solicitud_id, alumno_id, anio_escolar_id, seccion_id, fecha_matricula, estado)
SELECT uuidv7()::text, sm.id, sm.alumno_id, sm.anio_escolar_id, s.id, DATE '2027-01-11', 'CONFIRMADA'
FROM matricula.solicitud_matricula sm
JOIN academico.seccion s ON s.grado_id = sm.grado_id AND s.letra = 'A'
WHERE sm.codigo_seguimiento LIKE 'MAT-2027-0046_';

-- Días lectivos del 01/03 al 12/03 (lunes a viernes; hay bloques los cinco días)
CREATE TEMP TABLE dias_demo AS
SELECT d::date AS fecha, row_number() OVER (ORDER BY d)::int AS n
FROM generate_series(DATE '2027-03-01', DATE '2027-03-12', INTERVAL '1 day') AS d
WHERE EXTRACT(ISODOW FROM d) BETWEEN 1 AND 5;

-- Asistencia de los alumnos de 3° A: casi todos puntuales, con algunas tardanzas, faltas y días sin registro
INSERT INTO asistencia.asistencia_alumno (id, alumno_id, fecha, estado, created_at, updated_at)
SELECT uuidv7()::text, a.id, d.fecha,
       CASE WHEN (d.n + a.k) % 7 = 0 THEN 'FALTA'
            WHEN (d.n * a.k) % 5 = 0 THEN 'TARDANZA'
            ELSE 'PUNTUAL' END,
       (d.fecha + TIME '08:05') AT TIME ZONE 'America/Lima', (d.fecha + TIME '08:05') AT TIME ZONE 'America/Lima'
FROM dias_demo d
CROSS JOIN (
    SELECT al.id, row_number() OVER (ORDER BY al.codigo_alumno)::int AS k
    FROM seguridad.alumno al
    JOIN matricula.matricula m ON m.alumno_id = al.id AND m.estado = 'CONFIRMADA'
    JOIN academico.seccion s ON s.id = m.seccion_id AND s.letra = 'A'
    JOIN academico.grado g ON g.id = s.grado_id AND g.numero_nivel = 3
    JOIN academico.anio_escolar ae ON ae.id = g.anio_escolar_id AND ae.anio = 2027
) a
WHERE NOT ((d.n + 2 * a.k) % 9 = 0);

-- Asistencia de los docentes con clases (DOC-001 a DOC-008): puntuales, con una tardanza y una falta del cierre cada tanto
INSERT INTO asistencia.asistencia_docente (id, docente_id, fecha, hora_registro, estado, created_at, updated_at)
SELECT uuidv7()::text, dc.id, d.fecha,
       CASE WHEN e.estado = 'FALTA' THEN NULL
            ELSE (d.fecha + TIME '07:30' + (dc.k * 2 + d.n % 3) * INTERVAL '1 minute'
                  + CASE WHEN e.estado = 'TARDANZA' THEN INTERVAL '25 minutes' ELSE INTERVAL '0' END) AT TIME ZONE 'America/Lima' END,
       e.estado,
       (d.fecha + TIME '15:30') AT TIME ZONE 'America/Lima', (d.fecha + TIME '15:30') AT TIME ZONE 'America/Lima'
FROM dias_demo d
CROSS JOIN (
    SELECT dd.id, row_number() OVER (ORDER BY dd.codigo_docente)::int AS k
    FROM seguridad.docente dd WHERE dd.codigo_docente BETWEEN 'DOC-002' AND 'DOC-008'
) dc
CROSS JOIN LATERAL (
    SELECT CASE WHEN (d.n + dc.k) % 11 = 0 THEN 'FALTA' WHEN (d.n * dc.k) % 6 = 0 THEN 'TARDANZA' ELSE 'PUNTUAL' END AS estado
) e;

-- Rosa Quispe (DOC-001), la docente de la demo: la secuencia de «Mi asistencia» del wireframe (lun 08/03 a vie 12/03)
INSERT INTO asistencia.asistencia_docente (id, docente_id, fecha, hora_registro, estado, created_at, updated_at)
SELECT uuidv7()::text, dc.id, r.fecha,
       CASE WHEN r.hora IS NULL THEN NULL ELSE (r.fecha + r.hora) AT TIME ZONE 'America/Lima' END, r.estado,
       (r.fecha + TIME '15:30') AT TIME ZONE 'America/Lima', (r.fecha + TIME '15:30') AT TIME ZONE 'America/Lima'
FROM (VALUES
    (DATE '2027-03-01', TIME '07:41', 'PUNTUAL'),
    (DATE '2027-03-02', TIME '07:44', 'PUNTUAL'),
    (DATE '2027-03-03', TIME '07:39', 'PUNTUAL'),
    (DATE '2027-03-04', TIME '07:55', 'TARDANZA'),
    (DATE '2027-03-05', TIME '07:42', 'PUNTUAL'),
    (DATE '2027-03-08', TIME '07:41', 'PUNTUAL'),
    (DATE '2027-03-09', NULL,         'FALTA'),
    (DATE '2027-03-10', TIME '07:38', 'PUNTUAL'),
    (DATE '2027-03-11', TIME '07:52', 'TARDANZA'),
    (DATE '2027-03-12', TIME '07:41', 'PUNTUAL')
) AS r (fecha, hora, estado)
CROSS JOIN seguridad.docente dc WHERE dc.codigo_docente = 'DOC-001';

-- Cierres diarios ya procesados (UC-12) de esas fechas
INSERT INTO asistencia.cierre_diario (id, fecha, estado, detalle, ejecutado_en, created_at, updated_at)
SELECT uuidv7()::text, d.fecha, 'EXITOSO', 'Cierre de la demostración: faltas generadas para los docentes que no marcaron',
       (d.fecha + TIME '15:30:10') AT TIME ZONE 'America/Lima', (d.fecha + TIME '15:30:10') AT TIME ZONE 'America/Lima',
       (d.fecha + TIME '15:30:10') AT TIME ZONE 'America/Lima'
FROM dias_demo d;

-- Lunes 15/03: tres docentes ya marcaron antes de las 7:40; Rosa y los demás todavía no
INSERT INTO asistencia.asistencia_docente (id, docente_id, fecha, hora_registro, estado, created_at, updated_at)
SELECT uuidv7()::text, dc.id, DATE '2027-03-15', (DATE '2027-03-15' + x.hora) AT TIME ZONE 'America/Lima', 'PUNTUAL',
       (DATE '2027-03-15' + x.hora) AT TIME ZONE 'America/Lima', (DATE '2027-03-15' + x.hora) AT TIME ZONE 'America/Lima'
FROM (VALUES ('DOC-002', TIME '07:31'), ('DOC-003', TIME '07:36'), ('DOC-005', TIME '07:38')) AS x (codigo, hora)
JOIN seguridad.docente dc ON dc.codigo_docente = x.codigo;

DROP TABLE dias_demo;
