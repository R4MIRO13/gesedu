-- Prueba de carga k6 (RNF-12, UC-01): 200 apoderados con la contraseña de la demo (se copia el hash de
-- apoderado@demo.gesedu.pe). Solo para la base de DEMO local; para borrarlos, reiniciar la base (scripts/reiniciar-bd).
INSERT INTO seguridad.usuario (id, nombres, apellidos, documento_identidad, correo, hash_contrasena, activo, rol_acceso_id)
SELECT uuidv7()::text, 'Carga ' || n, 'Apoderado', (60000000 + n)::text,
       'carga' || lpad(n::text, 3, '0') || '@carga.gesedu.pe', base.hash_contrasena, TRUE, base.rol_acceso_id
FROM generate_series(1, 200) n
CROSS JOIN (SELECT hash_contrasena, rol_acceso_id FROM seguridad.usuario WHERE correo = 'apoderado@demo.gesedu.pe') base
ON CONFLICT DO NOTHING;

INSERT INTO seguridad.apoderado (id, usuario_id, numero_telefono)
SELECT uuidv7()::text, u.id, '9' || u.documento_identidad
FROM seguridad.usuario u
WHERE u.correo LIKE 'carga%@carga.gesedu.pe'
  AND NOT EXISTS (SELECT 1 FROM seguridad.apoderado a WHERE a.usuario_id = u.id);
