-- Datos semilla de la DEMO (perfil demo; nunca en producción): docs/PLAN.md § 7.0 y § 7.2.
-- Contraseña de todas las cuentas: Gesedu2027!  (se calcula con pgcrypto: BCrypt costo 12, compatible con Spring Security)

INSERT INTO seguridad.usuario (id, nombres, apellidos, documento_identidad, correo, hash_contrasena, activo, rol_acceso_id)
SELECT uuidv7()::text, u.nombres, u.apellidos, u.dni, u.correo, crypt('Gesedu2027!', gen_salt('bf', 12)), TRUE, r.id
FROM (VALUES
    ('Carmen',  'Salas Paredes',    '40125874', 'admin@demo.gesedu.pe',      'ADMINISTRADOR'),
    ('Ricardo', 'Paredes Ortiz',    '09874512', 'directivo@demo.gesedu.pe',  'ADMINISTRADOR'),
    ('Rosa',    'Quispe Mendoza',   '10547896', 'docente@demo.gesedu.pe',    'DOCENTE'),
    ('Lucía',   'García Rojas',     '76543218', 'alumno@demo.gesedu.pe',     'ALUMNO'),
    ('María',   'Rojas Salazar',    '41236587', 'apoderado@demo.gesedu.pe',  'APODERADO'),
    -- Cuenta reservada para mostrar el bloqueo tras 5 intentos fallidos sin afectar a las demás
    ('Hugo',    'Gonzales Paredes', '41785236', 'bloqueo@demo.gesedu.pe',    'DOCENTE')
) AS u (nombres, apellidos, dni, correo, rol)
JOIN seguridad.rol_acceso r ON r.codigo = u.rol;

-- El Directivo es un Administrador con cargo de dirección (no es un rol aparte)
INSERT INTO seguridad.administrador (id, usuario_id, cargo)
SELECT uuidv7()::text, u.id, a.cargo
FROM (VALUES
    ('admin@demo.gesedu.pe',     'Secretaría académica'),
    ('directivo@demo.gesedu.pe', 'Coordinación académica')
) AS a (correo, cargo)
JOIN seguridad.usuario u ON u.correo = a.correo;

-- Año escolar 2027
INSERT INTO academico.anio_escolar (id, anio, fecha_inicio, fecha_fin, estado)
VALUES (uuidv7()::text, 2027, DATE '2027-02-15', DATE '2027-12-20', 'ACTIVO');

-- Bloques horarios 2027, de lunes a viernes; el recreo (10:00 – 10:15) es el hueco entre bloques
INSERT INTO horarios.bloque_horario (id, anio_escolar_id, dia_semana, hora_inicio, hora_fin)
SELECT uuidv7()::text, a.id, d.dia, b.hora_inicio, b.hora_fin
FROM academico.anio_escolar a
CROSS JOIN (VALUES ('LUNES', 1), ('MARTES', 2), ('MIERCOLES', 3), ('JUEVES', 4), ('VIERNES', 5)) AS d (dia, orden)
CROSS JOIN (VALUES
    (TIME '07:45', TIME '08:30'), (TIME '08:30', TIME '09:15'), (TIME '09:15', TIME '10:00'),
    (TIME '10:15', TIME '11:00'), (TIME '11:00', TIME '11:45'), (TIME '11:45', TIME '12:30'),
    (TIME '12:30', TIME '13:15'), (TIME '13:15', TIME '14:00'), (TIME '14:00', TIME '14:45')
) AS b (hora_inicio, hora_fin)
WHERE a.anio = 2027
ORDER BY d.orden, b.hora_inicio;
