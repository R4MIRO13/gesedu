import { linkOptions } from '@tanstack/react-router'

/**
 * Año con el que trabaja la intranet hasta que exista la configuración del año escolar (UC-13): es el año de la
 * demo (docs/PLAN.md § 7.2). Cuando UC-13 esté lista saldrá del año ACTIVO.
 */
export const ANIO_TRABAJO = 2027

/** Opciones de la intranet; la SPA solo oculta, la autorización real la hace el backend. */
export const OPCIONES_MENU = [
  {
    enlace: linkOptions({ to: '/intranet/academico/anios' }),
    titulo: 'Año escolar',
    descripcion: 'Crea el año escolar con sus cuatro bimestres y revisa los años registrados.',
    permiso: 'ACADEMICO_CONFIGURAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/academico/$anio/cursos', params: { anio: String(ANIO_TRABAJO) } }),
    titulo: 'Cursos y competencias',
    descripcion: 'Cursos del año con sus horas y competencias; copia la configuración del año anterior.',
    permiso: 'ACADEMICO_CONFIGURAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/academico/$anio/secciones', params: { anio: String(ANIO_TRABAJO) } }),
    titulo: 'Secciones y vacantes',
    descripcion: 'Secciones de cada grado: capacidad, vacantes y tutor.',
    permiso: 'ACADEMICO_CONFIGURAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/horario' }),
    titulo: 'Horario',
    descripcion: 'Consulta el horario publicado: por sección o por docente, según tu alcance.',
    permiso: 'HORARIO_CONSULTAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/horarios/bloques' }),
    titulo: 'Bloques horarios',
    descripcion: 'Franjas de clase del año escolar.',
    permiso: 'HORARIO_CONSULTAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/horarios/$anio/configuracion', params: { anio: String(ANIO_TRABAJO) }, search: { seccion: 'periodo' } }),
    titulo: 'Configuración de horarios',
    descripcion: 'Periodo de disponibilidad, docentes con sus cursos y bloques semanales de cada curso.',
    permiso: 'HORARIO_CONFIGURAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/disponibilidad/$anio', params: { anio: String(ANIO_TRABAJO) } }),
    titulo: 'Mi disponibilidad',
    descripcion: 'Marca los bloques en los que puedes dictar clases durante el periodo de registro.',
    permiso: 'DISPONIBILIDAD_REGISTRAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/horarios/$anio/elaborar', params: { anio: String(ANIO_TRABAJO) }, search: { perspectiva: 'SECCION' } }),
    titulo: 'Elaborar horario',
    descripcion: 'Arma el horario del año: secciones, cursos y docentes, sin cruces.',
    permiso: 'HORARIO_GESTIONAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/horarios/$anio/publicar', params: { anio: String(ANIO_TRABAJO) } }),
    titulo: 'Publicar horario',
    descripcion: 'Valida la carga de cada sección y publica la versión vigente del horario.',
    permiso: 'HORARIO_GESTIONAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/matriculas' }),
    titulo: 'Matrículas',
    descripcion: 'Evalúa las solicitudes: valida, confirma la sección y genera los compromisos de pago.',
    permiso: 'MATRICULA_EVALUAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/matricula/solicitud' }),
    titulo: 'Solicitar matrícula',
    descripcion: 'Presenta la solicitud de matrícula de un estudiante con sus documentos.',
    permiso: 'MATRICULA_SOLICITAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/matricula/solicitudes' }),
    titulo: 'Mis solicitudes',
    descripcion: 'Sigue el estado de las solicitudes de matrícula que presentaste.',
    permiso: 'MATRICULA_SOLICITAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/pagos' }),
    titulo: 'Matrícula y pagos',
    descripcion: 'Consulta la matrícula de tus hijos, sus obligaciones de pago y presenta comprobantes.',
    permiso: 'OBLIGACION_CONSULTAR_PROPIA',
  },
  {
    enlace: linkOptions({ to: '/intranet/pagos/validar' }),
    titulo: 'Validar comprobantes',
    descripcion: 'Revisa los comprobantes de pago, valida el importe y registra la boleta.',
    permiso: 'COMPROBANTE_VALIDAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/pagos/historial' }),
    titulo: 'Historial financiero',
    descripcion: 'Consulta las obligaciones, boletas, deudas y penalidades de un estudiante.',
    permiso: 'PAGO_HISTORIAL_CONSULTAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/pagos/configuracion' }),
    titulo: 'Configuración de cobros',
    descripcion: 'Costos, vencimientos y penalidad del año escolar.',
    permiso: 'COBRO_CONFIGURAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/estudiantes' }),
    titulo: 'Datos de estudiantes',
    descripcion: 'Corrige los datos personales de los estudiantes y de sus apoderados.',
    permiso: 'USUARIO_GESTIONAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/usuarios' }),
    titulo: 'Usuarios',
    descripcion: 'Cuentas del sistema: estado, bloqueos y desbloqueo.',
    permiso: 'USUARIO_CONSULTAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/auditoria' }),
    titulo: 'Auditoría',
    descripcion: 'Registro inmutable de las operaciones críticas.',
    permiso: 'AUDITORIA_CONSULTAR',
  },
] as const

export type OpcionMenu = (typeof OPCIONES_MENU)[number]
