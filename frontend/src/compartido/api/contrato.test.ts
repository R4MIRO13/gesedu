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
import type { EstadoCuenta, FichaAlumno, FichaApoderado, PaginaUsuarios, UsuarioFila } from '../../modulos/seguridad/api'
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
