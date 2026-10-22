-- Datos de referencia presentes en todos los ambientes: los 4 roles (el Directivo es un Administrador con cargo
-- de dirección, no un rol aparte) y el catálogo de permisos (docs/PLAN.md § 5.3, RF-48).
-- uuidv7() es nativa desde PostgreSQL 18.

INSERT INTO seguridad.rol_acceso (id, codigo, nombre, descripcion) VALUES
    (uuidv7()::text, 'ADMINISTRADOR', 'Administrador', 'Personal administrativo y directivo del colegio'),
    (uuidv7()::text, 'DOCENTE',       'Docente',       'Docente; si es tutor, también gestiona la asistencia de su sección'),
    (uuidv7()::text, 'ALUMNO',        'Alumno',        'Estudiante matriculado'),
    (uuidv7()::text, 'APODERADO',     'Apoderado',     'Padre, madre o tutor legal de uno o más alumnos');

INSERT INTO seguridad.permiso (id, codigo, modulo, accion, descripcion)
SELECT uuidv7()::text, codigo, modulo, accion, descripcion
FROM (VALUES
    ('USUARIO_CONSULTAR',          'SEGURIDAD',  'CONSULTAR',  'Consultar cuentas de usuario'),
    ('USUARIO_GESTIONAR',          'SEGURIDAD',  'GESTIONAR',  'Crear, modificar, activar y desactivar cuentas'),
    ('ROL_GESTIONAR',              'SEGURIDAD',  'GESTIONAR',  'Gestionar roles y permisos'),
    ('MATRICULA_SOLICITAR',        'MATRICULA',  'SOLICITAR',  'Solicitar la matrícula de un estudiante'),
    ('MATRICULA_CONSULTAR_PROPIA', 'MATRICULA',  'CONSULTAR',  'Consultar la matrícula propia o de los hijos'),
    ('MATRICULA_EVALUAR',          'MATRICULA',  'EVALUAR',    'Consultar y evaluar solicitudes de matrícula'),
    ('MATRICULA_REGISTRAR',        'MATRICULA',  'REGISTRAR',  'Confirmar o rechazar matrículas'),
    ('COBRO_CONFIGURAR',           'PAGOS',      'CONFIGURAR', 'Configurar costos y políticas de cobranza'),
    ('OBLIGACION_CONSULTAR_PROPIA','PAGOS',      'CONSULTAR',  'Consultar las obligaciones de pago propias o de los hijos'),
    ('COMPROBANTE_PRESENTAR',      'PAGOS',      'PRESENTAR',  'Presentar comprobantes de pago'),
    ('COMPROBANTE_VALIDAR',        'PAGOS',      'VALIDAR',    'Validar y registrar pagos'),
    ('PAGO_HISTORIAL_CONSULTAR',   'PAGOS',      'CONSULTAR',  'Consultar el historial financiero de un estudiante'),
    ('ASISTENCIA_DOCENTE_MARCAR',  'ASISTENCIA', 'MARCAR',     'Marcar la asistencia propia como docente'),
    ('ASISTENCIA_ALUMNO_REGISTRAR','ASISTENCIA', 'REGISTRAR',  'Registrar la asistencia de los alumnos de la sección a cargo'),
    ('ASISTENCIA_REPORTE_SECCION', 'ASISTENCIA', 'REPORTAR',   'Exportar el reporte de asistencia de la sección a cargo'),
    ('ASISTENCIA_REPORTE_GENERAL', 'ASISTENCIA', 'REPORTAR',   'Exportar reportes de asistencia de alumnos y docentes'),
    ('ASISTENCIA_DOCENTE_CONSULTAR','ASISTENCIA','CONSULTAR',  'Consultar la asistencia diaria de docentes'),
    ('ASISTENCIA_CONSULTAR_PROPIA','ASISTENCIA', 'CONSULTAR',  'Consultar la asistencia propia'),
    ('ACADEMICO_CONFIGURAR',       'ACADEMICO',  'CONFIGURAR', 'Configurar año escolar, bimestres, cursos y competencias'),
    ('ACADEMICO_CONSULTAR',        'ACADEMICO',  'CONSULTAR',  'Consultar la configuración académica'),
    ('NOTA_REGISTRAR',             'EVALUACION', 'REGISTRAR',  'Registrar logros por competencia'),
    ('REGLA_DEFINIR',              'EVALUACION', 'DEFINIR',    'Definir la regla de consolidación de un curso'),
    ('NOTA_CALCULAR_FINAL',        'EVALUACION', 'CALCULAR',   'Calcular las notas finales'),
    ('NOTA_CONSOLIDAR',            'EVALUACION', 'CONSOLIDAR', 'Consolidar las notas bimestrales'),
    ('NOTA_CONSULTAR_PROPIA',      'EVALUACION', 'CONSULTAR',  'Consultar notas y libreta propias o de los hijos'),
    ('HORARIO_CONFIGURAR',         'HORARIOS',   'CONFIGURAR', 'Configurar días lectivos, bloques y carga horaria'),
    ('HORARIO_GESTIONAR',          'HORARIOS',   'GESTIONAR',  'Elaborar y publicar el horario'),
    ('DISPONIBILIDAD_REGISTRAR',   'HORARIOS',   'REGISTRAR',  'Registrar la disponibilidad horaria propia'),
    ('HORARIO_CONSULTAR',          'HORARIOS',   'CONSULTAR',  'Consultar el horario publicado'),
    ('AUDITORIA_CONSULTAR',        'AUDITORIA',  'CONSULTAR',  'Consultar los registros de auditoría'),
    ('AUDITORIA_EXPORTAR',         'AUDITORIA',  'EXPORTAR',   'Exportar los registros de auditoría')
) AS p (codigo, modulo, accion, descripcion);

INSERT INTO seguridad.rol_permiso (rol_acceso_id, permiso_id)
SELECT r.id, p.id
FROM (VALUES
    ('ADMINISTRADOR', 'USUARIO_CONSULTAR'), ('ADMINISTRADOR', 'USUARIO_GESTIONAR'), ('ADMINISTRADOR', 'ROL_GESTIONAR'),
    ('ADMINISTRADOR', 'MATRICULA_EVALUAR'), ('ADMINISTRADOR', 'MATRICULA_REGISTRAR'),
    ('ADMINISTRADOR', 'COBRO_CONFIGURAR'), ('ADMINISTRADOR', 'COMPROBANTE_VALIDAR'), ('ADMINISTRADOR', 'PAGO_HISTORIAL_CONSULTAR'),
    ('ADMINISTRADOR', 'ASISTENCIA_REPORTE_GENERAL'), ('ADMINISTRADOR', 'ASISTENCIA_DOCENTE_CONSULTAR'),
    ('ADMINISTRADOR', 'ACADEMICO_CONFIGURAR'), ('ADMINISTRADOR', 'ACADEMICO_CONSULTAR'), ('ADMINISTRADOR', 'NOTA_CONSOLIDAR'),
    ('ADMINISTRADOR', 'HORARIO_CONFIGURAR'), ('ADMINISTRADOR', 'HORARIO_GESTIONAR'), ('ADMINISTRADOR', 'HORARIO_CONSULTAR'),
    ('ADMINISTRADOR', 'AUDITORIA_CONSULTAR'), ('ADMINISTRADOR', 'AUDITORIA_EXPORTAR'),

    ('DOCENTE', 'ASISTENCIA_DOCENTE_MARCAR'), ('DOCENTE', 'ASISTENCIA_ALUMNO_REGISTRAR'), ('DOCENTE', 'ASISTENCIA_REPORTE_SECCION'),
    ('DOCENTE', 'NOTA_REGISTRAR'), ('DOCENTE', 'REGLA_DEFINIR'), ('DOCENTE', 'NOTA_CALCULAR_FINAL'),
    ('DOCENTE', 'DISPONIBILIDAD_REGISTRAR'), ('DOCENTE', 'HORARIO_CONSULTAR'), ('DOCENTE', 'ACADEMICO_CONSULTAR'),

    ('ALUMNO', 'MATRICULA_CONSULTAR_PROPIA'), ('ALUMNO', 'ASISTENCIA_CONSULTAR_PROPIA'),
    ('ALUMNO', 'NOTA_CONSULTAR_PROPIA'), ('ALUMNO', 'HORARIO_CONSULTAR'),

    ('APODERADO', 'MATRICULA_SOLICITAR'), ('APODERADO', 'MATRICULA_CONSULTAR_PROPIA'),
    ('APODERADO', 'OBLIGACION_CONSULTAR_PROPIA'), ('APODERADO', 'COMPROBANTE_PRESENTAR'),
    ('APODERADO', 'NOTA_CONSULTAR_PROPIA'), ('APODERADO', 'HORARIO_CONSULTAR')
) AS m (rol, permiso)
JOIN seguridad.rol_acceso r ON r.codigo = m.rol
JOIN seguridad.permiso p ON p.codigo = m.permiso;
