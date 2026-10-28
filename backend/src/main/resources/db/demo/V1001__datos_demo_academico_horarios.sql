-- Datos semilla de la DEMO, parte P1 de docs/PLAN.md § 7.2 (perfil demo; nunca en producción).
-- Continúa V1000: bimestres, grados, secciones, cursos, docentes, borrador parcial y la matrícula de Lucía.

-- Cuatro bimestres sin superponerse y dentro del año (UC-13)
INSERT INTO academico.bimestre (id, anio_escolar_id, numero, fecha_inicio, fecha_fin)
SELECT uuidv7()::text, a.id, b.numero, b.inicio, b.fin
FROM academico.anio_escolar a
CROSS JOIN (VALUES
    (1, DATE '2027-02-15', DATE '2027-04-30'),
    (2, DATE '2027-05-03', DATE '2027-07-09'),
    (3, DATE '2027-07-26', DATE '2027-10-01'),
    (4, DATE '2027-10-04', DATE '2027-12-20')
) AS b (numero, inicio, fin)
WHERE a.anio = 2027;

-- Grados 1° a 5°
INSERT INTO academico.grado (id, anio_escolar_id, numero_nivel)
SELECT uuidv7()::text, a.id, n.nivel
FROM academico.anio_escolar a CROSS JOIN (VALUES (1), (2), (3), (4), (5)) AS n (nivel)
WHERE a.anio = 2027;

-- Docentes: cuentas sin acceso (salvo Rosa Quispe, que usa docente@) y su perfil de docente
INSERT INTO seguridad.usuario (id, nombres, apellidos, documento_identidad, activo, rol_acceso_id)
SELECT uuidv7()::text, d.nombres, d.apellidos, d.dni, FALSE, r.id
FROM (VALUES
    ('Fernando', 'Medina Soto',    '10254789'),
    ('Carlos',   'Ríos Vega',      '10698745'),
    ('Jorge',    'Mendoza Alva',   '09745632'),
    ('Ana',      'Huamán Torres',  '41256398'),
    ('Luis',     'Castro Núñez',   '08965412'),
    ('Carmen',   'Villanueva Paz', '42158796'),
    ('Óscar',    'Cárdenas Lazo',  '10478523')
) AS d (nombres, apellidos, dni)
CROSS JOIN seguridad.rol_acceso r WHERE r.codigo = 'DOCENTE';

INSERT INTO seguridad.docente (id, usuario_id, codigo_docente, especialidad)
SELECT uuidv7()::text, u.id, d.codigo, d.especialidad
FROM (VALUES
    ('10547896', 'DOC-001', 'Matemática'),
    ('10254789', 'DOC-002', 'Matemática'),
    ('10698745', 'DOC-003', 'Matemática'),
    ('09745632', 'DOC-004', 'Comunicación'),
    ('41256398', 'DOC-005', 'Ciencia y Tecnología'),
    ('08965412', 'DOC-006', 'Ciencias Sociales'),
    ('42158796', 'DOC-007', 'Inglés'),
    ('10478523', 'DOC-008', 'Educación Física')
) AS d (dni, codigo, especialidad)
JOIN seguridad.usuario u ON u.documento_identidad = d.dni;

-- Secciones A y B por grado, capacidad 30. 3° A tiene 4 vacantes y 2° B una (demo de la última vacante, RNF-09).
-- Rosa Quispe es la tutora de 3° A.
INSERT INTO academico.seccion (id, grado_id, anio_escolar_id, letra, capacidad, vacantes_disponibles, tutor_docente_id)
SELECT uuidv7()::text, g.id, g.anio_escolar_id, l.letra, 30,
       CASE WHEN g.numero_nivel = 3 AND l.letra = 'A' THEN 4
            WHEN g.numero_nivel = 2 AND l.letra = 'B' THEN 1
            ELSE 30 END,
       CASE WHEN g.numero_nivel = 3 AND l.letra = 'A'
            THEN (SELECT d.id FROM seguridad.docente d WHERE d.codigo_docente = 'DOC-001') END
FROM academico.grado g CROSS JOIN (VALUES ('A'), ('B')) AS l (letra);

-- Cursos 2027, iguales para todos los grados (RF-23): 22 horas semanales
INSERT INTO academico.curso (id, anio_escolar_id, nombre, slug, horas_lectivas)
SELECT uuidv7()::text, a.id, c.nombre, c.slug, c.horas
FROM academico.anio_escolar a
CROSS JOIN (VALUES
    ('Matemática',           'matematica',           5),
    ('Comunicación',         'comunicacion',         5),
    ('Ciencia y Tecnología', 'ciencia-y-tecnologia', 4),
    ('Ciencias Sociales',    'ciencias-sociales',    3),
    ('Inglés',               'ingles',               3),
    ('Educación Física',     'educacion-fisica',     2)
) AS c (nombre, slug, horas)
WHERE a.anio = 2027;

-- Competencias con su nivel esperado (RF-34)
INSERT INTO academico.competencia (id, curso_id, codigo, nombre, nivel_esperado, orden)
SELECT uuidv7()::text, c.id, k.codigo, k.nombre, k.nivel, k.orden
FROM academico.curso c
JOIN (VALUES
    ('matematica',           'C1', 'Resuelve problemas de cantidad',                              'Traduce cantidades a expresiones numéricas y usa estrategias de cálculo.', 1),
    ('matematica',           'C2', 'Resuelve problemas de regularidad, equivalencia y cambio',    'Establece relaciones entre datos y las expresa con ecuaciones y funciones.', 2),
    ('matematica',           'C3', 'Resuelve problemas de forma, movimiento y localización',      'Modela objetos con formas geométricas y describe su ubicación y transformación.', 3),
    ('matematica',           'C4', 'Resuelve problemas de gestión de datos e incertidumbre',      'Representa datos con gráficos y medidas, y decide con base en la probabilidad.', 4),
    ('comunicacion',         'C1', 'Se comunica oralmente en su lengua materna',                  'Expresa ideas con coherencia y adecúa su discurso al interlocutor.', 1),
    ('comunicacion',         'C2', 'Lee diversos tipos de textos escritos',                       'Interpreta textos complejos e infiere su sentido global.', 2),
    ('comunicacion',         'C3', 'Escribe diversos tipos de textos',                            'Organiza sus ideas y las desarrolla con cohesión.', 3),
    ('ciencia-y-tecnologia', 'C1', 'Indaga mediante métodos científicos',                         'Plantea hipótesis y diseña procedimientos para ponerlas a prueba.', 1),
    ('ciencia-y-tecnologia', 'C2', 'Explica el mundo físico basándose en conocimientos',          'Relaciona conceptos científicos con fenómenos de su entorno.', 2),
    ('ciencia-y-tecnologia', 'C3', 'Diseña y construye soluciones tecnológicas',                  'Propone prototipos que resuelven problemas del entorno.', 3),
    ('ciencias-sociales',    'C1', 'Construye interpretaciones históricas',                       'Explica procesos históricos con fuentes diversas.', 1),
    ('ciencias-sociales',    'C2', 'Gestiona responsablemente el espacio y el ambiente',          'Analiza problemas ambientales y propone acciones.', 2),
    ('ciencias-sociales',    'C3', 'Gestiona responsablemente los recursos económicos',           'Toma decisiones económicas informadas.', 3),
    ('ingles',               'C1', 'Se comunica oralmente en inglés',                             'Mantiene conversaciones sencillas sobre temas cotidianos.', 1),
    ('ingles',               'C2', 'Lee diversos tipos de textos en inglés',                      'Comprende textos breves e identifica ideas principales.', 2),
    ('ingles',               'C3', 'Escribe diversos tipos de textos en inglés',                  'Redacta textos simples con vocabulario básico.', 3),
    ('educacion-fisica',     'C1', 'Se desenvuelve de manera autónoma a través de su motricidad', 'Controla su cuerpo en actividades físicas variadas.', 1),
    ('educacion-fisica',     'C2', 'Asume una vida saludable',                                    'Adopta hábitos de actividad física, alimentación e higiene.', 2)
) AS k (slug, codigo, nombre, nivel, orden) ON k.slug = c.slug;

-- Carga horaria semanal de cada curso por grado (C-10): inicia con las horas lectivas del curso
INSERT INTO horarios.carga_horaria_curso (id, grado_id, curso_id, bloques_semanales)
SELECT uuidv7()::text, g.id, c.id, c.horas_lectivas
FROM academico.grado g JOIN academico.curso c ON c.anio_escolar_id = g.anio_escolar_id;

-- Vinculaciones 2027 y cursos que dicta cada docente (RF-47)
INSERT INTO horarios.vinculacion_docente (id, docente_id, anio_escolar_id, habilitado, carga_semanal_referencia)
SELECT uuidv7()::text, d.id, a.id, TRUE, 22
FROM seguridad.docente d CROSS JOIN academico.anio_escolar a WHERE a.anio = 2027;

INSERT INTO horarios.asociacion_docente_curso (id, vinculacion_id, curso_id)
SELECT uuidv7()::text, v.id, c.id
FROM (VALUES
    ('DOC-001', 'matematica'), ('DOC-002', 'matematica'), ('DOC-003', 'matematica'),
    ('DOC-004', 'comunicacion'), ('DOC-005', 'ciencia-y-tecnologia'), ('DOC-006', 'ciencias-sociales'),
    ('DOC-007', 'ingles'), ('DOC-008', 'educacion-fisica')
) AS x (codigo, slug)
JOIN seguridad.docente d ON d.codigo_docente = x.codigo
JOIN horarios.vinculacion_docente v ON v.docente_id = d.id
JOIN academico.curso c ON c.slug = x.slug;

-- Disponibilidad confirmada: todos los bloques, salvo Carlos Ríos el martes de 9:15 a 10:00
INSERT INTO horarios.disponibilidad_docente (id, vinculacion_id, fecha_hora_confirmacion)
SELECT uuidv7()::text, v.id, TIMESTAMPTZ '2027-01-20 10:00:00-05' FROM horarios.vinculacion_docente v;

INSERT INTO horarios.bloque_disponible (id, disponibilidad_id, bloque_id)
SELECT uuidv7()::text, dd.id, b.id
FROM horarios.disponibilidad_docente dd
JOIN horarios.vinculacion_docente v ON v.id = dd.vinculacion_id
JOIN seguridad.docente d ON d.id = v.docente_id
CROSS JOIN horarios.bloque_horario b
WHERE NOT (d.codigo_docente = 'DOC-003' AND b.dia_semana = 'MARTES' AND b.hora_inicio = TIME '09:15');

-- Borrador 2027 parcialmente armado
INSERT INTO horarios.horario_borrador (id, anio_escolar_id, fecha_creacion, fecha_actualizacion)
SELECT uuidv7()::text, a.id, TIMESTAMPTZ '2027-01-25 09:00:00-05', TIMESTAMPTZ '2027-01-25 09:00:00-05'
FROM academico.anio_escolar a WHERE a.anio = 2027;

-- Rosa Quispe ya tiene 4° B el martes de 9:15 a 10:00 (provoca el cruce de la demo)
INSERT INTO horarios.asignacion_horario (id, horario_borrador_id, bloque_id, seccion_id, curso_id, docente_id,
                                          asociacion_docente_curso_id)
SELECT uuidv7()::text, hb.id, b.id, s.id, c.id, d.id, ad.id
FROM horarios.horario_borrador hb
JOIN horarios.bloque_horario b ON b.anio_escolar_id = hb.anio_escolar_id
     AND b.dia_semana = 'MARTES' AND b.hora_inicio = TIME '09:15'
JOIN academico.grado g ON g.anio_escolar_id = hb.anio_escolar_id AND g.numero_nivel = 4
JOIN academico.seccion s ON s.grado_id = g.id AND s.letra = 'B'
JOIN academico.curso c ON c.anio_escolar_id = hb.anio_escolar_id AND c.slug = 'matematica'
JOIN seguridad.docente d ON d.codigo_docente = 'DOC-001'
JOIN horarios.vinculacion_docente v ON v.docente_id = d.id
JOIN horarios.asociacion_docente_curso ad ON ad.vinculacion_id = v.id AND ad.curso_id = c.id;

-- 3° A con 19 de 22 horas: Matemática 5 (Fernando), Comunicación 5 (Jorge), Ciencia y Tecnología 4 (Ana),
-- Ciencias Sociales 3 (Luis) e Inglés 2 de 3 (Carmen). Se reparten en orden por los bloques de la semana, sin usar
-- el martes de 9:15, que queda libre para la demo. Faltan 1 h de Inglés y 2 h de Educación Física.
INSERT INTO horarios.asignacion_horario (id, horario_borrador_id, bloque_id, seccion_id, curso_id, docente_id,
                                          asociacion_docente_curso_id)
SELECT uuidv7()::text, hb.id, ord.bloque_id, s.id, c.id, d.id, ad.id
FROM horarios.horario_borrador hb
JOIN academico.grado g ON g.anio_escolar_id = hb.anio_escolar_id AND g.numero_nivel = 3
JOIN academico.seccion s ON s.grado_id = g.id AND s.letra = 'A'
JOIN (
    SELECT b.id AS bloque_id, b.anio_escolar_id,
           row_number() OVER (ORDER BY CASE b.dia_semana WHEN 'LUNES' THEN 1 WHEN 'MARTES' THEN 2
                                                          WHEN 'MIERCOLES' THEN 3 WHEN 'JUEVES' THEN 4 ELSE 5 END,
                                       b.hora_inicio) AS n
    FROM horarios.bloque_horario b
    WHERE NOT (b.dia_semana = 'MARTES' AND b.hora_inicio = TIME '09:15')
) ord ON ord.anio_escolar_id = hb.anio_escolar_id AND ord.n <= 19
JOIN (VALUES
    ( 1,  5, 'matematica',           'DOC-002'),
    ( 6, 10, 'comunicacion',         'DOC-004'),
    (11, 14, 'ciencia-y-tecnologia', 'DOC-005'),
    (15, 17, 'ciencias-sociales',    'DOC-006'),
    (18, 19, 'ingles',               'DOC-007')
) AS plan (desde, hasta, slug, docente) ON ord.n BETWEEN plan.desde AND plan.hasta
JOIN academico.curso c ON c.anio_escolar_id = hb.anio_escolar_id AND c.slug = plan.slug
JOIN seguridad.docente d ON d.codigo_docente = plan.docente
JOIN horarios.vinculacion_docente v ON v.docente_id = d.id
JOIN horarios.asociacion_docente_curso ad ON ad.vinculacion_id = v.id AND ad.curso_id = c.id;

-- Apoderada y alumna: María Rojas Salazar y su hija Lucía García Rojas (3° A, matrícula 2027 ya confirmada)
INSERT INTO seguridad.apoderado (id, usuario_id, numero_telefono)
SELECT uuidv7()::text, u.id, '987654321' FROM seguridad.usuario u WHERE u.correo = 'apoderado@demo.gesedu.pe';

INSERT INTO seguridad.alumno (id, usuario_id, codigo_alumno, fecha_nacimiento, apoderado_id, parentesco_apoderado)
SELECT uuidv7()::text, u.id, '2026-0315', DATE '2012-05-20', ap.id, 'MADRE'
FROM seguridad.usuario u CROSS JOIN seguridad.apoderado ap WHERE u.correo = 'alumno@demo.gesedu.pe';

-- Las solicitudes pendientes de la demo llevan los códigos MAT-2027-00479 en adelante
SELECT setval('matricula.seq_solicitud', 500);

INSERT INTO matricula.solicitud_matricula (id, codigo_seguimiento, anio_escolar_id, alumno_id, apoderado_id, grado_id,
                                            fecha_solicitud, tipo_estudiante, estado_documentacion, estado_admision,
                                            estado, costos_aceptados)
SELECT uuidv7()::text, 'MAT-2027-00478', a.id, al.id, al.apoderado_id, g.id, DATE '2027-01-10', 'RECURRENTE',
       'COMPLETA', 'NO_APLICA', 'APROBADA', TRUE
FROM academico.anio_escolar a
JOIN academico.grado g ON g.anio_escolar_id = a.id AND g.numero_nivel = 3
CROSS JOIN seguridad.alumno al
WHERE a.anio = 2027 AND al.codigo_alumno = '2026-0315';

INSERT INTO matricula.matricula (id, solicitud_id, alumno_id, anio_escolar_id, seccion_id, fecha_matricula, estado)
SELECT uuidv7()::text, sm.id, sm.alumno_id, sm.anio_escolar_id, s.id, DATE '2027-01-12', 'CONFIRMADA'
FROM matricula.solicitud_matricula sm
JOIN academico.seccion s ON s.grado_id = sm.grado_id AND s.letra = 'A'
WHERE sm.codigo_seguimiento = 'MAT-2027-00478';
