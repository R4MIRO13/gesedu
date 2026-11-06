import { describe, expectTypeOf, it } from 'vitest'
import type { DatosUsuario, SesionIniciada } from '../auth/sesion'
import type {
  Asignacion,
  Borrador,
  CargaCurso,
  CargaSeccion,
  ConflictoHorario,
  OpcionDocente,
  Opciones,
  SolicitudAsignacion,
  Validacion,
} from '../../modulos/horarios/api'
import type {
  CambioAuditoria,
  CatalogosAuditoria,
  DetalleRegistroAuditoria,
  Pagina,
  RegistroAuditoria,
} from '../../modulos/auditoria/api'
import type {
  AlumnoSolicitud,
  ApoderadoSolicitud,
  DetalleSolicitud,
  DocumentoSolicitud,
  LineaCosto,
  MotivoRechazo,
  ReglaMatricula,
  ResultadoConfirmacion,
  ResultadoRechazo,
  ResumenCostos,
  SeccionConVacantes,
  SolicitudResumen,
  ValidacionMatricula,
} from '../../modulos/matricula/api'
import type {
  AlumnoBusqueda,
  Boleta,
  ComprobanteBandeja,
  ComprobanteResumen,
  ConfiguracionCobros,
  DetalleComprobante,
  DeudaVencida,
  HistorialFinanciero,
  LineaCobro as LineaCobroPagos,
  Obligacion,
  ObligacionesAlumno,
  PagoRegistrado,
  ResultadoRevision,
  ResultadoValidacion,
  ResumenObligaciones,
} from '../../modulos/pagos/api'
import type {
  DatosApoderado,
  HijoMatricula,
  MatriculaGestionable,
  MatriculaVigente,
  OpcionesSolicitud,
  ResultadoCambioSeccion,
  SolicitudPropia,
  SolicitudRegistrada,
} from '../../modulos/matricula/api'
import type { DetalleUsuario, EstadoCuenta, FichaAlumno, FichaApoderado, PaginaUsuarios, UsuarioFila } from '../../modulos/seguridad/api'
import type {
  AnioDetalle,
  AnioResumen,
  Bimestre,
  Competencia,
  CopiaAnterior,
  Curso,
  CursoCopiable,
  CursosAnio,
  DocenteTutor,
  GradoConSecciones,
  GradosAnio,
  ProblemaAnio,
  ResultadoCopia,
  Seccion,
  ValidacionAnio,
} from '../../modulos/academico/api'
import type {
  Advertencia,
  CargasHorarias,
  CeldaCarga,
  Completitud,
  CursoOpcion,
  Disponibilidad,
  DiferenciaCurso,
  FilaCarga,
  HorarioVigente,
  PeriodoDisponibilidad,
  Publicacion,
  SeccionCompletitud,
  ValidacionDisponibilidad,
  Vinculacion,
  VinculacionesAnio,
} from '../../modulos/horarios/api'
import type {
  AlumnoAsistencia,
  AsistenciaDocentesDia,
  AsistenciaHoy,
  AsistenciaSeccion,
  FilaAsistenciaDocente,
  FilaReporte,
  MiSeccion,
  RegistroAlumno,
  RegistroAsistenciaDocente,
  ReporteAsistencia,
  ResumenAsistenciaAlumno,
  TotalesAsistencia,
  TotalesDocentes,
} from '../../modulos/asistencia/api'
import type {
  Asignacion as AsignacionDocente,
  AlumnoIncompleto,
  AlumnoNotas,
  AvanceBimestre,
  BimestreInfo,
  CeldaNota,
  CompetenciaCurso,
  ConfiguracionCurso,
  CursoAvance,
  CursoNotas,
  CursosAlumno,
  FilaAlumno,
  FilaNotas,
  Libreta,
  NotaCompetencia,
  NotasFinales,
  PesoBimestre,
  ReglaConsolidacion,
  Rejilla,
  ResultadoCalculo,
  ResultadoConsolidacion,
} from '../../modulos/evaluacion/api'
import type { components } from './tipos.gen'

type Esquemas = components['schemas']

/**
 * Comprueba con `tsc` (npm run typecheck) que los tipos escritos a mano coinciden con el contrato OpenAPI del
 * backend (`scripts/generar-tipos-api.*`). Si el backend agrega, quita o renombra una propiedad, esto deja de
 * compilar y hay que actualizar el tipo y regenerar `tipos.gen.ts`.
 */
describe('contrato de la API', () => {
  it('la sesión y el usuario tienen las mismas propiedades que el backend', () => {
    expectTypeOf<keyof SesionIniciada>().toEqualTypeOf<keyof Esquemas['SesionIniciada']>()
    expectTypeOf<keyof DatosUsuario>().toEqualTypeOf<keyof Esquemas['DatosUsuario']>()
  })

  it('los tipos de las propiedades son compatibles', () => {
    expectTypeOf<SesionIniciada['tokenAcceso']>().toEqualTypeOf<NonNullable<Esquemas['SesionIniciada']['tokenAcceso']>>()
    expectTypeOf<DatosUsuario['permisos']>().toEqualTypeOf<NonNullable<Esquemas['DatosUsuario']['permisos']>>()
  })
})

describe('contrato del horario (UC-21)', () => {
  it('los tipos del borrador tienen las mismas propiedades que el backend', () => {
    expectTypeOf<keyof Borrador>().toEqualTypeOf<keyof Esquemas['BorradorDto']>()
    expectTypeOf<keyof Asignacion>().toEqualTypeOf<keyof Esquemas['AsignacionDto']>()
    expectTypeOf<keyof CargaCurso>().toEqualTypeOf<keyof Esquemas['CargaCursoDto']>()
    expectTypeOf<keyof CargaSeccion>().toEqualTypeOf<keyof Esquemas['CargaSeccionDto']>()
    expectTypeOf<keyof Opciones>().toEqualTypeOf<keyof Esquemas['OpcionesDto']>()
    expectTypeOf<keyof OpcionDocente>().toEqualTypeOf<keyof Esquemas['OpcionDocenteDto']>()
    expectTypeOf<keyof ConflictoHorario>().toEqualTypeOf<keyof Esquemas['ConflictoDto']>()
    expectTypeOf<keyof Validacion>().toEqualTypeOf<keyof Esquemas['ValidacionDto']>()
    expectTypeOf<keyof SolicitudAsignacion>().toEqualTypeOf<keyof Esquemas['AsignacionRequest']>()
  })

  it('el código de cada conflicto es uno de los tipos del validador', () => {
    expectTypeOf<ConflictoHorario['tipo']>().toEqualTypeOf<
      'DOCENTE_NO_ASOCIADO' | 'DOCENTE_NO_DISPONIBLE' | 'CRUCE_DOCENTE' | 'CRUCE_SECCION'
    >()
  })
})

describe('contrato de la auditoría (UC-25)', () => {
  it('los tipos de la consulta tienen las mismas propiedades que el backend', () => {
    expectTypeOf<keyof RegistroAuditoria>().toEqualTypeOf<keyof Esquemas['RegistroAuditoriaDto']>()
    expectTypeOf<keyof CambioAuditoria>().toEqualTypeOf<keyof Esquemas['CambioAuditoriaDto']>()
    expectTypeOf<keyof DetalleRegistroAuditoria>().toEqualTypeOf<keyof Esquemas['DetalleRegistroAuditoriaDto']>()
    expectTypeOf<keyof CatalogosAuditoria>().toEqualTypeOf<keyof Esquemas['CatalogosAuditoriaDto']>()
    expectTypeOf<keyof Pagina<RegistroAuditoria>>().toEqualTypeOf<keyof Esquemas['PaginaRegistroAuditoriaDto']>()
  })
})

describe('contrato de la matrícula (UC-02)', () => {
  it('los tipos de la bandeja y del detalle tienen las mismas propiedades que el backend', () => {
    expectTypeOf<keyof SolicitudResumen>().toEqualTypeOf<keyof Esquemas['SolicitudResumen']>()
    expectTypeOf<keyof DetalleSolicitud>().toEqualTypeOf<keyof Esquemas['DetalleSolicitud']>()
    expectTypeOf<keyof AlumnoSolicitud>().toEqualTypeOf<keyof Esquemas['AlumnoSolicitudDto']>()
    expectTypeOf<keyof ApoderadoSolicitud>().toEqualTypeOf<keyof Esquemas['ApoderadoSolicitudDto']>()
    expectTypeOf<keyof DocumentoSolicitud>().toEqualTypeOf<keyof Esquemas['DocumentoSolicitudDto']>()
    expectTypeOf<keyof ValidacionMatricula>().toEqualTypeOf<keyof Esquemas['ValidacionMatriculaDto']>()
    expectTypeOf<keyof SeccionConVacantes>().toEqualTypeOf<keyof Esquemas['SeccionConVacantes']>()
    expectTypeOf<keyof ResumenCostos>().toEqualTypeOf<keyof Esquemas['ResumenCostos']>()
    expectTypeOf<keyof LineaCosto>().toEqualTypeOf<keyof Esquemas['LineaCosto']>()
  })

  it('los resultados de confirmar y rechazar coinciden con el backend', () => {
    expectTypeOf<keyof ResultadoConfirmacion>().toEqualTypeOf<keyof Esquemas['ResultadoConfirmacion']>()
    expectTypeOf<keyof ResultadoRechazo>().toEqualTypeOf<keyof Esquemas['ResultadoRechazo']>()
  })

  it('los motivos y reglas son los del backend', () => {
    expectTypeOf<MotivoRechazo>().toEqualTypeOf<NonNullable<Esquemas['RechazoRequest']['motivo']>>()
    expectTypeOf<ReglaMatricula>().toEqualTypeOf<NonNullable<Esquemas['ValidacionMatriculaDto']['regla']>>()
  })
})

describe('contrato de usuarios (UC-24)', () => {
  it('la fila de usuario tiene las mismas propiedades que el backend', () => {
    expectTypeOf<keyof UsuarioFila>().toEqualTypeOf<keyof Esquemas['UsuarioFila']>()
    expectTypeOf<keyof PaginaUsuarios>().toEqualTypeOf<keyof Esquemas['PaginaUsuarioFila']>()
  })

  it('fase 6: la ficha de la cuenta (UC-24) tiene las propiedades del backend', () => {
    expectTypeOf<keyof DetalleUsuario>().toEqualTypeOf<keyof Esquemas['DetalleUsuario']>()
  })

  it('los estados de la cuenta son los del backend', () => {
    expectTypeOf<EstadoCuenta>().toEqualTypeOf<NonNullable<Esquemas['UsuarioFila']['estado']>>()
  })

  it('fase 2: UC-01 y UC-03 (matrícula del apoderado y de sus hijos)', () => {
    expectTypeOf<keyof OpcionesSolicitud>().toEqualTypeOf<keyof Esquemas['OpcionesSolicitud']>()
    expectTypeOf<keyof DatosApoderado>().toEqualTypeOf<keyof Esquemas['DatosApoderado']>()
    expectTypeOf<keyof SolicitudRegistrada>().toEqualTypeOf<keyof Esquemas['SolicitudRegistrada']>()
    expectTypeOf<keyof SolicitudPropia>().toEqualTypeOf<keyof Esquemas['SolicitudPropia']>()
    expectTypeOf<keyof HijoMatricula>().toEqualTypeOf<keyof Esquemas['HijoMatricula']>()
    expectTypeOf<keyof MatriculaVigente>().toEqualTypeOf<keyof Esquemas['MatriculaVigente']>()
  })

  it('fase 2: cobros, obligaciones y comprobantes (RF-06, UC-03 a UC-06)', () => {
    expectTypeOf<keyof ConfiguracionCobros>().toEqualTypeOf<keyof Esquemas['ConfiguracionCobros']>()
    expectTypeOf<keyof LineaCobroPagos>().toEqualTypeOf<keyof Esquemas['LineaCobro']>()
    expectTypeOf<keyof Obligacion>().toEqualTypeOf<keyof Esquemas['ObligacionDto']>()
    expectTypeOf<keyof ObligacionesAlumno>().toEqualTypeOf<keyof Esquemas['ObligacionesAlumno']>()
    expectTypeOf<keyof ResumenObligaciones>().toEqualTypeOf<keyof Esquemas['ResumenObligaciones']>()
    expectTypeOf<keyof Boleta>().toEqualTypeOf<keyof Esquemas['BoletaDto']>()
    expectTypeOf<keyof ComprobanteResumen>().toEqualTypeOf<keyof Esquemas['ComprobanteResumen']>()
    expectTypeOf<keyof ComprobanteBandeja>().toEqualTypeOf<keyof Esquemas['ComprobanteBandeja']>()
    expectTypeOf<keyof DetalleComprobante>().toEqualTypeOf<keyof Esquemas['DetalleComprobante']>()
    expectTypeOf<keyof ResultadoValidacion>().toEqualTypeOf<keyof Esquemas['ResultadoValidacion']>()
    expectTypeOf<keyof ResultadoRevision>().toEqualTypeOf<keyof Esquemas['ResultadoRevision']>()
    expectTypeOf<keyof HistorialFinanciero>().toEqualTypeOf<keyof Esquemas['HistorialFinanciero']>()
    expectTypeOf<keyof PagoRegistrado>().toEqualTypeOf<keyof Esquemas['PagoRegistrado']>()
    expectTypeOf<keyof DeudaVencida>().toEqualTypeOf<keyof Esquemas['DeudaVencida']>()
    expectTypeOf<keyof AlumnoBusqueda>().toEqualTypeOf<keyof Esquemas['AlumnoBusqueda']>()
  })

  it('fase 2: RF-02 y RF-05 (datos personales y cambio de sección)', () => {
    expectTypeOf<keyof FichaAlumno>().toEqualTypeOf<keyof Esquemas['FichaAlumno']>()
    expectTypeOf<keyof FichaApoderado>().toEqualTypeOf<keyof Esquemas['FichaApoderado']>()
    expectTypeOf<keyof MatriculaGestionable>().toEqualTypeOf<keyof Esquemas['MatriculaGestionable']>()
    expectTypeOf<keyof ResultadoCambioSeccion>().toEqualTypeOf<keyof Esquemas['ResultadoCambioSeccion']>()
  })
})

describe('contrato académico (UC-13, UC-14, RF-03, RF-04)', () => {
  it('el año escolar y su validación tienen las mismas propiedades que el backend', () => {
    expectTypeOf<keyof AnioResumen>().toEqualTypeOf<keyof Esquemas['AnioResumenDto']>()
    expectTypeOf<keyof AnioDetalle>().toEqualTypeOf<keyof Esquemas['AnioDetalleDto']>()
    expectTypeOf<keyof Bimestre>().toEqualTypeOf<keyof Esquemas['BimestreDto']>()
    expectTypeOf<keyof ValidacionAnio>().toEqualTypeOf<keyof Esquemas['ValidacionAnioDto']>()
    expectTypeOf<keyof ProblemaAnio>().toEqualTypeOf<keyof Esquemas['ProblemaDto']>()
  })

  it('los cursos y la copia del año anterior coinciden con el backend', () => {
    expectTypeOf<keyof Curso>().toEqualTypeOf<keyof Esquemas['CursoDto']>()
    expectTypeOf<keyof Competencia>().toEqualTypeOf<keyof Esquemas['CompetenciaDto']>()
    expectTypeOf<keyof CursosAnio>().toEqualTypeOf<keyof Esquemas['CursosAnioDto']>()
    expectTypeOf<keyof CursoCopiable>().toEqualTypeOf<keyof Esquemas['CursoCopiable']>()
    expectTypeOf<keyof CopiaAnterior>().toEqualTypeOf<keyof Esquemas['CopiaAnteriorDto']>()
    expectTypeOf<keyof ResultadoCopia>().toEqualTypeOf<keyof Esquemas['ResultadoCopiaDto']>()
  })

  it('los grados y las secciones coinciden con el backend', () => {
    expectTypeOf<keyof Seccion>().toEqualTypeOf<keyof Esquemas['SeccionConfiguracionDto']>()
    expectTypeOf<keyof GradoConSecciones>().toEqualTypeOf<keyof Esquemas['GradoSeccionesDto']>()
    expectTypeOf<keyof GradosAnio>().toEqualTypeOf<keyof Esquemas['GradosAnioDto']>()
    expectTypeOf<keyof DocenteTutor>().toEqualTypeOf<keyof Esquemas['DocenteTutorDto']>()
  })
})

describe('contrato de la configuración de horarios y UC-20', () => {
  it('el periodo, las vinculaciones y las cargas coinciden con el backend', () => {
    expectTypeOf<keyof PeriodoDisponibilidad>().toEqualTypeOf<keyof Esquemas['PeriodoDto']>()
    expectTypeOf<keyof Vinculacion>().toEqualTypeOf<keyof Esquemas['VinculacionDto']>()
    expectTypeOf<keyof VinculacionesAnio>().toEqualTypeOf<keyof Esquemas['VinculacionesAnioDto']>()
    expectTypeOf<keyof CursoOpcion>().toEqualTypeOf<keyof Esquemas['CursoOpcionDto']>()
    expectTypeOf<keyof CargasHorarias>().toEqualTypeOf<keyof Esquemas['CargasHorariasDto']>()
    expectTypeOf<keyof FilaCarga>().toEqualTypeOf<keyof Esquemas['FilaCargaDto']>()
    expectTypeOf<keyof CeldaCarga>().toEqualTypeOf<keyof Esquemas['CeldaCargaDto']>()
  })

  it('la disponibilidad del docente coincide con el backend', () => {
    expectTypeOf<keyof Disponibilidad>().toEqualTypeOf<keyof Esquemas['DisponibilidadDto']>()
    expectTypeOf<keyof ValidacionDisponibilidad>().toEqualTypeOf<keyof Esquemas['ValidacionDisponibilidadDto']>()
  })
})

describe('contrato de la publicación y la consulta del horario (UC-22, UC-23)', () => {
  it('la completitud y las versiones publicadas coinciden con el backend', () => {
    expectTypeOf<keyof Completitud>().toEqualTypeOf<keyof Esquemas['CompletitudDto']>()
    expectTypeOf<keyof SeccionCompletitud>().toEqualTypeOf<keyof Esquemas['SeccionCompletitudDto']>()
    expectTypeOf<keyof DiferenciaCurso>().toEqualTypeOf<keyof Esquemas['DiferenciaDto']>()
    expectTypeOf<keyof Advertencia>().toEqualTypeOf<keyof Esquemas['AdvertenciaDto']>()
    expectTypeOf<keyof Publicacion>().toEqualTypeOf<keyof Esquemas['PublicacionDto']>()
  })

  it('el horario vigente coincide con el backend', () => {
    expectTypeOf<keyof HorarioVigente>().toEqualTypeOf<keyof Esquemas['HorarioVigenteDto']>()
  })
})

describe('contrato de la asistencia (UC-07 a UC-12, RF-16)', () => {
  it('la marcación docente y su historial tienen las mismas propiedades que el backend', () => {
    expectTypeOf<keyof AsistenciaHoy>().toEqualTypeOf<keyof Esquemas['EstadoHoyDto']>()
    expectTypeOf<keyof RegistroAsistenciaDocente>().toEqualTypeOf<keyof Esquemas['RegistroDto']>()
  })

  it('el pase de lista y el cambio a tardanza tienen las mismas propiedades que el backend', () => {
    expectTypeOf<keyof AsistenciaSeccion>().toEqualTypeOf<keyof Esquemas['AsistenciaSeccionDto']>()
    expectTypeOf<keyof AlumnoAsistencia>().toEqualTypeOf<keyof Esquemas['AlumnoAsistenciaDto']>()
    expectTypeOf<keyof TotalesAsistencia>().toEqualTypeOf<keyof Esquemas['TotalesAsistenciaDto']>()
    expectTypeOf<keyof MiSeccion>().toEqualTypeOf<keyof Esquemas['MiSeccionDto']>()
    expectTypeOf<keyof RegistroAlumno>().toEqualTypeOf<keyof Esquemas['RegistroAlumnoDto']>()
  })

  it('la consulta de docentes por día tiene las mismas propiedades que el backend', () => {
    expectTypeOf<keyof AsistenciaDocentesDia>().toEqualTypeOf<keyof Esquemas['AsistenciaDocenteDiaDto']>()
    expectTypeOf<keyof FilaAsistenciaDocente>().toEqualTypeOf<keyof Esquemas['FilaDocenteDto']>()
    expectTypeOf<keyof TotalesDocentes>().toEqualTypeOf<keyof Esquemas['TotalesDocentesDto']>()
  })

  it('los reportes y el resumen del alumno tienen las mismas propiedades que el backend', () => {
    expectTypeOf<keyof ReporteAsistencia>().toEqualTypeOf<keyof Esquemas['ReporteAsistenciaDto']>()
    expectTypeOf<keyof FilaReporte>().toEqualTypeOf<keyof Esquemas['FilaReporteDto']>()
    expectTypeOf<keyof ResumenAsistenciaAlumno>().toEqualTypeOf<keyof Esquemas['ResumenAsistenciaAlumnoDto']>()
  })
})

describe('contrato de la evaluación (UC-15 a UC-19)', () => {
  it('mis cursos y la rejilla de logros tienen las mismas propiedades que el backend', () => {
    expectTypeOf<keyof AsignacionDocente>().toEqualTypeOf<keyof Esquemas['AsignacionCursoDto']>()
    expectTypeOf<keyof Rejilla>().toEqualTypeOf<keyof Esquemas['RejillaDto']>()
    expectTypeOf<keyof BimestreInfo>().toEqualTypeOf<keyof Esquemas['BimestreLogrosDto']>()
    expectTypeOf<keyof CompetenciaCurso>().toEqualTypeOf<keyof Esquemas['CompetenciaCursoDto']>()
    expectTypeOf<keyof FilaAlumno>().toEqualTypeOf<keyof Esquemas['FilaAlumnoDto']>()
    expectTypeOf<keyof AlumnoIncompleto>().toEqualTypeOf<keyof Esquemas['IncompletoDto']>()
  })

  it('la regla de consolidación y los pesos tienen las mismas propiedades que el backend', () => {
    expectTypeOf<keyof ConfiguracionCurso>().toEqualTypeOf<keyof Esquemas['ConfiguracionDto']>()
    expectTypeOf<keyof ReglaConsolidacion>().toEqualTypeOf<keyof Esquemas['ReglaDto']>()
    expectTypeOf<keyof ReglaConsolidacion['ejemplo']>().toEqualTypeOf<keyof Esquemas['EjemploDto']>()
    expectTypeOf<keyof PesoBimestre>().toEqualTypeOf<keyof Esquemas['PesoDto']>()
  })

  it('las notas finales tienen las mismas propiedades que el backend', () => {
    expectTypeOf<keyof NotasFinales>().toEqualTypeOf<keyof Esquemas['NotasFinalesDto']>()
    expectTypeOf<keyof FilaNotas>().toEqualTypeOf<keyof Esquemas['FilaNotasDto']>()
    expectTypeOf<keyof CeldaNota>().toEqualTypeOf<keyof Esquemas['CeldaNotaDto']>()
    expectTypeOf<keyof ResultadoCalculo>().toEqualTypeOf<keyof Esquemas['CalculoDto']>()
  })

  it('la consolidación del bimestre tiene las mismas propiedades que el backend', () => {
    expectTypeOf<keyof AvanceBimestre>().toEqualTypeOf<keyof Esquemas['AvanceDto']>()
    expectTypeOf<keyof CursoAvance>().toEqualTypeOf<keyof Esquemas['CursoAvanceDto']>()
    expectTypeOf<keyof AvanceBimestre['bimestre']>().toEqualTypeOf<keyof Esquemas['BimestreAvanceDto']>()
    expectTypeOf<keyof AvanceBimestre['totales']>().toEqualTypeOf<keyof Esquemas['TotalesAvanceDto']>()
    expectTypeOf<keyof AvanceBimestre['bimestres'][number]>().toEqualTypeOf<keyof Esquemas['EstadoBimestreDto']>()
    expectTypeOf<keyof ResultadoConsolidacion>().toEqualTypeOf<keyof Esquemas['ResultadoDto']>()
  })

  it('las notas y la libreta del alumno tienen las mismas propiedades que el backend', () => {
    expectTypeOf<keyof AlumnoNotas>().toEqualTypeOf<keyof Esquemas['AlumnoNotasDto']>()
    expectTypeOf<keyof CursosAlumno>().toEqualTypeOf<keyof Esquemas['CursosAlumnoDto']>()
    expectTypeOf<keyof CursosAlumno['bimestres'][number]>().toEqualTypeOf<keyof Esquemas['BimestreConsolidadoDto']>()
    expectTypeOf<keyof CursoNotas>().toEqualTypeOf<keyof Esquemas['CursoNotasDto']>()
    expectTypeOf<keyof NotaCompetencia>().toEqualTypeOf<keyof Esquemas['NotaCompetenciaDto']>()
    expectTypeOf<keyof Libreta>().toEqualTypeOf<keyof Esquemas['LibretaDto']>()
  })
})
