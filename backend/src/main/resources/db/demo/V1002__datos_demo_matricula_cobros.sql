-- Datos semilla de la DEMO, parte P2 de docs/PLAN.md § 7.2 (perfil demo; nunca en producción).
-- Cobros 2027, año 2026 cerrado con una deuda y las solicitudes de matrícula MAT-2027-00479 a 00486 para UC-02.

-- Cronograma 2027: matrícula + 10 pensiones de S/ 350.00 = 11 compromisos, S/ 3,850.00 (UC-02, pasos 7 y 12)
INSERT INTO pagos.cronograma_cobro (id, anio_escolar_id, concepto, periodo, monto, fecha_vencimiento)
SELECT uuidv7()::text, a.id, 'MATRICULA', '2027', 350.00, DATE '2027-02-28'
FROM academico.anio_escolar a WHERE a.anio = 2027
UNION ALL
SELECT uuidv7()::text, a.id, 'PENSION', to_char(make_date(2027, m, 1), 'YYYY-MM'), 350.00, make_date(2027, m, 5)
FROM academico.anio_escolar a CROSS JOIN generate_series(3, 12) AS m WHERE a.anio = 2027;

INSERT INTO pagos.politica_cobro (id, anio_escolar_id, porcentaje_penalidad)
SELECT uuidv7()::text, a.id, 5.00 FROM academico.anio_escolar a WHERE a.anio = 2027;

-- Año 2026 cerrado: grados 2° a 4° con la sección A, para el historial de los alumnos recurrentes
INSERT INTO academico.anio_escolar (id, anio, fecha_inicio, fecha_fin, estado)
VALUES (uuidv7()::text, 2026, DATE '2026-02-16', DATE '2026-12-18', 'CERRADO');

INSERT INTO academico.grado (id, anio_escolar_id, numero_nivel)
SELECT uuidv7()::text, a.id, n.nivel
FROM academico.anio_escolar a CROSS JOIN (VALUES (2), (3), (4)) AS n (nivel)
WHERE a.anio = 2026;

INSERT INTO academico.seccion (id, grado_id, anio_escolar_id, letra, capacidad, vacantes_disponibles)
SELECT uuidv7()::text, g.id, g.anio_escolar_id, 'A', 30, 29
FROM academico.grado g JOIN academico.anio_escolar a ON a.id = g.anio_escolar_id
WHERE a.anio = 2026;

-- Ocho familias con una solicitud pendiente cada una. Las cuentas no tienen contraseña (no inician sesión), pero los
-- apoderados tienen correo para que el aviso de UC-02 llegue a Mailpit.
CREATE TEMP TABLE tmp_familia (
    n int, a_nombres text, a_apellidos text, a_dni text, a_codigo text, a_nac date,
    p_nombres text, p_apellidos text, p_dni text, p_correo text, parentesco text,
    tipo text, grado int, admision text, documentacion text
) ON COMMIT DROP;

INSERT INTO tmp_familia VALUES
    -- 1: FA-3, debe S/ 700.00 del 2026
    (1, 'Diego',   'Paredes Luna',    '77100001', '2026-0401', DATE '2011-03-14', 'Elena',  'Luna Campos',  '40100001', 'familia.paredes@demo.gesedu.pe',  'MADRE', 'RECURRENTE',    4, 'NO_APLICA',  'COMPLETA'),
    -- 2: FA-1, no admitida
    (2, 'Camila',  'Soto Vera',       '77100002', '2026-0402', DATE '2014-07-02', 'Jorge',  'Soto Ramos',   '40100002', 'familia.soto@demo.gesedu.pe',     'PADRE', 'NUEVO_INGRESO', 1, 'NO_ADMITIDO', 'COMPLETA'),
    -- 3: FA-2, ya tiene matrícula 2027 en 3° B
    (3, 'Mateo',   'Ríos Cano',       '77100003', '2026-0403', DATE '2012-01-25', 'Paula',  'Cano Tello',   '40100003', 'familia.rios@demo.gesedu.pe',     'MADRE', 'RECURRENTE',    3, 'NO_APLICA',  'COMPLETA'),
    -- 4: flujo básico, 3° A (4 vacantes)
    (4, 'Valeria', 'Torres Gil',      '77100004', '2026-0404', DATE '2012-09-09', 'Marcos', 'Torres Lima',  '40100004', 'familia.torres@demo.gesedu.pe',   'PADRE', 'RECURRENTE',    3, 'NO_APLICA',  'COMPLETA'),
    -- 5 y 6: compiten por la última vacante de 2° B (RNF-09)
    (5, 'Andrés',  'Quispe Rojas',    '77100005', '2026-0405', DATE '2013-04-18', 'Ana',    'Rojas Cueva',  '40100005', 'familia.quispe@demo.gesedu.pe',   'MADRE', 'NUEVO_INGRESO', 2, 'ADMITIDO',   'COMPLETA'),
    (6, 'Sofía',   'Vargas Díaz',     '77100006', '2026-0406', DATE '2013-11-30', 'Luis',   'Vargas Mora',  '40100006', 'familia.vargas@demo.gesedu.pe',   'PADRE', 'NUEVO_INGRESO', 2, 'ADMITIDO',   'COMPLETA'),
    -- 7: documentación incompleta
    (7, 'Joaquín', 'Méndez Paz',      '77100007', '2026-0407', DATE '2010-06-11', 'Carla',  'Paz Rey',      '40100007', 'familia.mendez@demo.gesedu.pe',   'MADRE', 'RECURRENTE',    5, 'NO_APLICA',  'INCOMPLETA'),
    -- 8: FA-5, desistimiento
    (8, 'Renata',  'Castillo Ibáñez', '77100008', '2026-0408', DATE '2011-12-05', 'Pedro',  'Castillo Soria','40100008', 'familia.castillo@demo.gesedu.pe', 'PADRE', 'RECURRENTE',    4, 'NO_APLICA',  'COMPLETA');

INSERT INTO seguridad.usuario (id, nombres, apellidos, documento_identidad, correo, activo, rol_acceso_id)
SELECT uuidv7()::text, f.p_nombres, f.p_apellidos, f.p_dni, f.p_correo, FALSE, r.id
FROM tmp_familia f CROSS JOIN seguridad.rol_acceso r WHERE r.codigo = 'APODERADO';

INSERT INTO seguridad.apoderado (id, usuario_id, numero_telefono)
SELECT uuidv7()::text, u.id, '9' || f.p_dni
FROM tmp_familia f JOIN seguridad.usuario u ON u.documento_identidad = f.p_dni;

INSERT INTO seguridad.usuario (id, nombres, apellidos, documento_identidad, activo, rol_acceso_id)
SELECT uuidv7()::text, f.a_nombres, f.a_apellidos, f.a_dni, FALSE, r.id
FROM tmp_familia f CROSS JOIN seguridad.rol_acceso r WHERE r.codigo = 'ALUMNO';

INSERT INTO seguridad.alumno (id, usuario_id, codigo_alumno, fecha_nacimiento, apoderado_id, parentesco_apoderado)
SELECT uuidv7()::text, ua.id, f.a_codigo, f.a_nac, ap.id, f.parentesco
FROM tmp_familia f
JOIN seguridad.usuario ua ON ua.documento_identidad = f.a_dni
JOIN seguridad.usuario up ON up.documento_identidad = f.p_dni
JOIN seguridad.apoderado ap ON ap.usuario_id = up.id;

-- Matrícula 2026 de los alumnos recurrentes, en el grado anterior al que solicitan
INSERT INTO matricula.solicitud_matricula (id, codigo_seguimiento, anio_escolar_id, alumno_id, apoderado_id, grado_id,
                                            fecha_solicitud, tipo_estudiante, estado_documentacion, estado_admision,
                                            estado, costos_aceptados)
SELECT uuidv7()::text, 'MAT-2026-' || lpad((100 + f.n)::text, 5, '0'), g.anio_escolar_id, al.id, al.apoderado_id,
       g.id, DATE '2026-01-12', 'RECURRENTE', 'COMPLETA', 'NO_APLICA', 'APROBADA', TRUE
FROM tmp_familia f
JOIN seguridad.alumno al ON al.codigo_alumno = f.a_codigo
JOIN academico.anio_escolar a ON a.anio = 2026
JOIN academico.grado g ON g.anio_escolar_id = a.id AND g.numero_nivel = f.grado - 1
WHERE f.tipo = 'RECURRENTE';

INSERT INTO matricula.matricula (id, solicitud_id, alumno_id, anio_escolar_id, seccion_id, fecha_matricula, estado)
SELECT uuidv7()::text, sm.id, sm.alumno_id, sm.anio_escolar_id, s.id, DATE '2026-01-15', 'CONFIRMADA'
FROM matricula.solicitud_matricula sm
JOIN academico.seccion s ON s.grado_id = sm.grado_id AND s.letra = 'A'
WHERE sm.codigo_seguimiento LIKE 'MAT-2026-%';

-- Diego Paredes dejó dos pensiones vencidas del 2026: S/ 700.00 (UC-02, FA-3)
INSERT INTO pagos.obligacion_pago (id, matricula_id, anio_escolar_id, alumno_id, apoderado_id, concepto, periodo,
                                    monto_base, fecha_vencimiento, estado)
SELECT uuidv7()::text, m.id, m.anio_escolar_id, m.alumno_id, al.apoderado_id, 'PENSION', p.periodo, 350.00,
       p.vence, 'VENCIDA'
FROM matricula.matricula m
JOIN seguridad.alumno al ON al.id = m.alumno_id AND al.codigo_alumno = '2026-0401'
CROSS JOIN (VALUES ('2026-03', DATE '2026-03-05'), ('2026-04', DATE '2026-04-05')) AS p (periodo, vence);

-- Mateo Ríos ya está matriculado en 3° B para 2027 (UC-02, FA-2: duplicidad)
INSERT INTO matricula.solicitud_matricula (id, codigo_seguimiento, anio_escolar_id, alumno_id, apoderado_id, grado_id,
                                            fecha_solicitud, tipo_estudiante, estado_documentacion, estado_admision,
                                            estado, costos_aceptados)
SELECT uuidv7()::text, 'MAT-2027-00470', a.id, al.id, al.apoderado_id, g.id, DATE '2027-01-08', 'RECURRENTE',
       'COMPLETA', 'NO_APLICA', 'APROBADA', TRUE
FROM academico.anio_escolar a
JOIN academico.grado g ON g.anio_escolar_id = a.id AND g.numero_nivel = 3
CROSS JOIN seguridad.alumno al
WHERE a.anio = 2027 AND al.codigo_alumno = '2026-0403';

INSERT INTO matricula.matricula (id, solicitud_id, alumno_id, anio_escolar_id, seccion_id, fecha_matricula, estado)
SELECT uuidv7()::text, sm.id, sm.alumno_id, sm.anio_escolar_id, s.id, DATE '2027-01-09', 'CONFIRMADA'
FROM matricula.solicitud_matricula sm
JOIN academico.seccion s ON s.grado_id = sm.grado_id AND s.letra = 'B'
WHERE sm.codigo_seguimiento = 'MAT-2027-00470';

-- Compromisos 2027 de las matrículas ya confirmadas (Lucía García y Mateo Ríos)
INSERT INTO pagos.obligacion_pago (id, matricula_id, anio_escolar_id, alumno_id, apoderado_id, concepto, periodo,
                                    monto_base, fecha_vencimiento)
SELECT uuidv7()::text, m.id, m.anio_escolar_id, m.alumno_id, al.apoderado_id, c.concepto, c.periodo, c.monto,
       c.fecha_vencimiento
FROM matricula.matricula m
JOIN academico.anio_escolar a ON a.id = m.anio_escolar_id AND a.anio = 2027
JOIN seguridad.alumno al ON al.id = m.alumno_id
JOIN pagos.cronograma_cobro c ON c.anio_escolar_id = m.anio_escolar_id
WHERE m.estado = 'CONFIRMADA';

-- Las ocho solicitudes pendientes de 2027 (MAT-2027-00479 a 00486)
INSERT INTO matricula.solicitud_matricula (id, codigo_seguimiento, anio_escolar_id, alumno_id, apoderado_id, grado_id,
                                            fecha_solicitud, tipo_estudiante, estado_documentacion, estado_admision)
SELECT uuidv7()::text, 'MAT-2027-' || lpad((478 + f.n)::text, 5, '0'), a.id, al.id, al.apoderado_id, g.id,
       DATE '2027-01-14' + f.n, f.tipo, f.documentacion, f.admision
FROM tmp_familia f
JOIN seguridad.alumno al ON al.codigo_alumno = f.a_codigo
JOIN academico.anio_escolar a ON a.anio = 2027
JOIN academico.grado g ON g.anio_escolar_id = a.id AND g.numero_nivel = f.grado;

-- Documentos de cada solicitud (los archivos no se siembran: el Gestor de Archivos solo guarda lo que se sube)
INSERT INTO matricula.documento_solicitud (id, solicitud_id, tipo, clave_objeto, nombre_archivo, content_type, tamano_bytes)
SELECT uuidv7()::text, sm.id, d.tipo, 'solicitudes/' || sm.codigo_seguimiento || '/' || d.archivo, d.archivo,
       'application/pdf', 184320
FROM matricula.solicitud_matricula sm
CROSS JOIN (VALUES ('CERTIFICADO_ESTUDIOS', 'certificado-estudios.pdf'),
                   ('FICHA_MATRICULA_ANTERIOR', 'ficha-matricula-anterior.pdf')) AS d (tipo, archivo)
WHERE sm.estado = 'PENDIENTE_REVISION';
