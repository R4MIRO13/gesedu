import { linkOptions } from '@tanstack/react-router'
import { ANIO_ACTIVO } from '../modulos/academico/anioActivo'

/** Opciones de la intranet; la SPA solo oculta, la autorización real la hace el backend. */
export const OPCIONES_MENU = [
  {
    enlace: linkOptions({ to: '/intranet/academico/anios' }),
    titulo: 'Año escolar',
    descripcion: 'Crea el año escolar con sus cuatro bimestres y revisa los años registrados.',
    permiso: 'ACADEMICO_CONFIGURAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/academico/$anio/cursos', params: { anio: ANIO_ACTIVO } }),
    titulo: 'Cursos y competencias',
    descripcion: 'Cursos del año con sus horas y competencias; copia la configuración del año anterior.',
    permiso: 'ACADEMICO_CONFIGURAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/academico/$anio/secciones', params: { anio: ANIO_ACTIVO } }),
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
    enlace: linkOptions({ to: '/intranet/horarios/$anio/configuracion', params: { anio: ANIO_ACTIVO }, search: { seccion: 'periodo' } }),
    titulo: 'Configuración de horarios',
    descripcion: 'Periodo de disponibilidad, docentes con sus cursos y bloques semanales de cada curso.',
    permiso: 'HORARIO_CONFIGURAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/disponibilidad/$anio', params: { anio: ANIO_ACTIVO } }),
    titulo: 'Mi disponibilidad',
    descripcion: 'Marca los bloques en los que puedes dictar clases durante el periodo de registro.',
    permiso: 'DISPONIBILIDAD_REGISTRAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/mi-asistencia' }),
    titulo: 'Mi asistencia',
    descripcion: 'Marca tu llegada: el servidor pone la hora y la compara con tu primera clase del día.',
    permiso: 'ASISTENCIA_DOCENTE_MARCAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/asistencia' }),
    titulo: 'Asistencia de mi sección',
    descripcion: 'Pasa lista a los alumnos de tu sección: puntual, tardanza o falta.',
    permiso: 'ASISTENCIA_ALUMNO_REGISTRAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/asistencia/docentes/$fecha', params: { fecha: 'hoy' } }),
    titulo: 'Asistencia docente',
    descripcion: 'Quién tenía clases hoy, cómo marcó y las faltas que generó el cierre de la jornada.',
    permiso: 'ASISTENCIA_DOCENTE_CONSULTAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/asistencia/reportes' }),
    titulo: 'Reporte de asistencia de mi sección',
    descripcion: 'Resumen mensual de asistencia de tus alumnos, en pantalla, Excel o PDF.',
    permiso: 'ASISTENCIA_REPORTE_SECCION',
  },
  {
    enlace: linkOptions({ to: '/intranet/asistencia/reportes' }),
    titulo: 'Reportes de asistencia',
    descripcion: 'Resumen mensual de asistencia de alumnos y docentes, en pantalla, Excel o PDF.',
    permiso: 'ASISTENCIA_REPORTE_GENERAL',
  },
  {
    enlace: linkOptions({ to: '/intranet/notas' }),
    titulo: 'Registro de notas',
    descripcion: 'Califica con AD, A, B o C a tus alumnos en cada competencia del bimestre vigente.',
    permiso: 'NOTA_REGISTRAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/notas' }),
    titulo: 'Notas y libreta',
    descripcion: 'Consulta tus cursos, las notas por competencia y descarga el informe de progreso o la libreta.',
    permiso: 'NOTA_CONSULTAR_PROPIA',
  },
  {
    enlace: linkOptions({ to: '/intranet/academico/consolidar-notas', search: { bimestre: 1 } }),
    titulo: 'Consolidar notas',
    descripcion: 'Revisa el avance de las notas por curso y sección y consolida el bimestre en la libreta de cada alumno.',
    permiso: 'NOTA_CONSOLIDAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/horarios/$anio/elaborar', params: { anio: ANIO_ACTIVO }, search: { perspectiva: 'SECCION' } }),
    titulo: 'Elaborar horario',
    descripcion: 'Arma el horario del año: secciones, cursos y docentes, sin cruces.',
    permiso: 'HORARIO_GESTIONAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/horarios/$anio/publicar', params: { anio: ANIO_ACTIVO } }),
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
    descripcion: 'Cuentas del sistema: alta con invitación, estado, bloqueos y desbloqueo.',
    permiso: 'USUARIO_CONSULTAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/roles' }),
    titulo: 'Roles y permisos',
    descripcion: 'Qué puede hacer cada rol; el cambio se aplica en el siguiente request de sus usuarios.',
    permiso: 'ROL_GESTIONAR',
  },
  {
    enlace: linkOptions({ to: '/intranet/auditoria' }),
    titulo: 'Auditoría',
    descripcion: 'Registro inmutable de las operaciones críticas.',
    permiso: 'AUDITORIA_CONSULTAR',
  },
] as const

export type OpcionMenu = (typeof OPCIONES_MENU)[number]
