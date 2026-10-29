import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, solicitar } from '../../compartido/api/cliente'
import { ApiError, SIN_CONEXION } from '../../compartido/api/errores'

export type ConceptoPago = 'MATRICULA' | 'PENSION' | 'CUOTA_INGRESO'

export const CONCEPTOS: Record<ConceptoPago, string> = {
  MATRICULA: 'Matrícula',
  PENSION: 'Pensión',
  CUOTA_INGRESO: 'Cuota de ingreso',
}

/** Una línea del cronograma de cobros; los montos viajan como texto decimal. */
export interface LineaCobro {
  concepto: ConceptoPago
  periodo: string
  monto: string
  fechaVencimiento: string
}

export interface ConfiguracionCobros {
  anio: number
  porcentajePenalidad: number | null
  lineas: LineaCobro[]
  configurado: boolean
}

export function useConfiguracionCobros(anio: number) {
  return useQuery({
    queryKey: ['cobros', anio],
    queryFn: () => api<ConfiguracionCobros>(`/anios-escolares/${anio}/cobros`),
    staleTime: 0,
  })
}

export function useGuardarCobros(anio: number) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (cuerpo: { porcentajePenalidad: number; lineas: LineaCobro[] }) =>
      api<ConfiguracionCobros>(`/anios-escolares/${anio}/cobros`, { method: 'PUT', cuerpo }),
    onSuccess: (datos) => cliente.setQueryData(['cobros', anio], datos),
  })
}

// ---- UC-03: obligaciones del apoderado ----

export type EstadoObligacion = 'PENDIENTE' | 'PAGADA' | 'VENCIDA'
export type EstadoRevision = 'PENDIENTE' | 'APROBADO' | 'OBSERVADO' | 'RECHAZADO'

export interface Boleta {
  numero: string
  fechaRegistro: string
  monto: string
}

export interface ComprobanteResumen {
  id: string
  estado: EstadoRevision
  observacion: string | null
  numeroOperacion: string
  fechaPago: string
  montoPresentado: string
  medioPago: string
}

export interface Obligacion {
  id: string
  concepto: ConceptoPago
  periodo: string
  montoBase: string
  montoPenalidad: string
  total: string
  fechaVencimiento: string
  estado: EstadoObligacion
  penalidadAplicadaEn: string | null
  boleta: Boleta | null
  comprobante: ComprobanteResumen | null
  puedePresentarComprobante: boolean
}

export interface ResumenObligaciones {
  totalPagado: string
  totalPendiente: string
  deudaVencida: string
  obligaciones: number
  pagadas: number
  pendientes: number
  vencidas: number
  proximoVencimiento: { concepto: ConceptoPago; periodo: string; fechaVencimiento: string; total: string } | null
}

export interface ObligacionesAlumno {
  alumnoId: string
  codigoAlumno: string
  alumno: string
  obligaciones: Obligacion[]
  resumen: ResumenObligaciones
}

const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre']

/** «Pensión marzo» para «2027-03»; la matrícula y la cuota de ingreso no llevan mes. */
export function nombreObligacion(concepto: ConceptoPago, periodo: string): string {
  if (concepto !== 'PENSION') return CONCEPTOS[concepto]
  const mes = Number(periodo.slice(5, 7))
  return `${CONCEPTOS.PENSION} ${MESES[mes - 1] ?? periodo}`
}

export function useObligaciones(alumnoId: string | undefined) {
  return useQuery({
    queryKey: ['obligaciones', alumnoId],
    queryFn: () => api<ObligacionesAlumno>(`/alumnos/${alumnoId}/obligaciones`),
    enabled: !!alumnoId,
    staleTime: 0,
  })
}

// ---- UC-04: presentar comprobante ----

export const TIPOS_VOUCHER_PERMITIDOS = ['application/pdf', 'image/jpeg', 'image/png']
export const TAMANO_MAXIMO_VOUCHER = 5 * 1024 * 1024

/** Voucher ya subido al Gestor de Archivos, listo para declararse en el comprobante. */
export interface VoucherSubido {
  claveObjeto: string
  nombreArchivo: string
  contentType: string
  tamanoBytes: number
}

export interface ComprobanteNuevo {
  numeroOperacion: string
  fechaPago: string
  montoPagado: string
  medioPago: string
  voucher: VoucherSubido
}

/**
 * Sube el voucher como lo hace el navegador (DA-07): pide la URL prefirmada y envía el archivo directo al Gestor de
 * Archivos con la cabecera que esa URL exige.
 */
export async function subirVoucher(obligacionId: string, archivo: File): Promise<VoucherSubido> {
  const { datos } = await solicitar<{
    claveObjeto: string
    subida: { url: string; metodo: string; cabeceras: Record<string, string> }
  }>(`/obligaciones/${obligacionId}/comprobantes/archivo`, {
    method: 'POST',
    cuerpo: { nombreArchivo: archivo.name, contentType: archivo.type, tamanoBytes: archivo.size },
  })
  let respuesta: Response
  try {
    respuesta = await fetch(datos.subida.url, { method: datos.subida.metodo, headers: datos.subida.cabeceras, body: archivo })
  } catch {
    throw new ApiError(0, 'No se pudo subir el archivo. Revisa tu conexión.', SIN_CONEXION)
  }
  if (!respuesta.ok) throw new ApiError(respuesta.status, 'No se pudo subir el archivo. Inténtalo de nuevo.', 'ARCHIVO_INVALIDO')
  return { claveObjeto: datos.claveObjeto, nombreArchivo: archivo.name, contentType: archivo.type, tamanoBytes: archivo.size }
}

export function useRegistrarComprobante(obligacionId: string) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (c: ComprobanteNuevo) =>
      api<ComprobanteResumen>(`/obligaciones/${obligacionId}/comprobantes`, {
        method: 'POST',
        cuerpo: {
          numeroOperacion: c.numeroOperacion,
          fechaPago: c.fechaPago,
          montoPagado: c.montoPagado,
          medioPago: c.medioPago,
          claveObjeto: c.voucher.claveObjeto,
          contentType: c.voucher.contentType,
          tamanoBytes: c.voucher.tamanoBytes,
        },
      }),
    onSuccess: () => cliente.invalidateQueries({ queryKey: ['obligaciones'] }),
  })
}
