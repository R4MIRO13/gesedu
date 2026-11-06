import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../compartido/api/cliente'
import type { SesionIniciada } from '../../compartido/auth/sesion'

export function useIniciarSesion() {
  return useMutation({
    mutationFn: (datos: { correo: string; contrasena: string }) =>
      api<SesionIniciada>('/auth/inicio-sesion', { method: 'POST', cuerpo: datos }),
  })
}

/** Pide el enlace de recuperación. La respuesta es la misma exista o no la cuenta (RF-52). */
export function useSolicitarRecuperacion() {
  return useMutation({
    mutationFn: (datos: { correo: string }) =>
      api<{ mensaje: string }>('/auth/recuperacion', { method: 'POST', cuerpo: datos }),
  })
}

/** Define la contraseña nueva con el token del enlace del correo (un solo uso, 30 minutos). */
export function useRestablecerContrasena() {
  return useMutation({
    mutationFn: (datos: { token: string; nuevaContrasena: string }) =>
      api<void>('/auth/recuperacion/confirmacion', { method: 'POST', cuerpo: datos }),
  })
}

export type EstadoCuenta = 'ACTIVO' | 'INACTIVO' | 'BLOQUEADO' | 'PENDIENTE'

/** Fila de la lista de usuarios (UC-24). */
export interface UsuarioFila {
  id: string
  nombre: string
  correo: string | null
  rol: string
  activo: boolean
  estado: EstadoCuenta
  bloqueadoHasta: string | null
  ultimoAcceso: string | null
}

export interface PaginaUsuarios {
  contenido: UsuarioFila[]
  pagina: number
  tamano: number
  totalElementos: number
  totalPaginas: number
}

export interface FiltrosUsuarios {
  q: string
  rol: string
  estado: EstadoCuenta | ''
  pagina: number
}

export const FILTROS_USUARIOS: FiltrosUsuarios = { q: '', rol: '', estado: '', pagina: 0 }

export function useUsuarios(filtros: FiltrosUsuarios) {
  const parametros = new URLSearchParams({ page: String(filtros.pagina), size: '15' })
  if (filtros.q.trim()) parametros.set('q', filtros.q.trim())
  if (filtros.rol) parametros.set('rol', filtros.rol)
  if (filtros.estado) parametros.set('estado', filtros.estado)
  return useQuery({
    queryKey: ['usuarios', filtros],
    queryFn: () => api<PaginaUsuarios>(`/usuarios?${parametros}`),
    placeholderData: keepPreviousData,
  })
}

/** El administrador levanta el bloqueo de una cuenta (queda auditado). */
export function useDesbloquearUsuario() {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api<UsuarioFila>(`/usuarios/${id}/desbloqueo`, { method: 'PATCH' }),
    onSuccess: () => cliente.invalidateQueries({ queryKey: ['usuarios'] }),
  })
}

// ---- UC-24: alta, edición, activación y desactivación de cuentas ----

export type RolCreable = 'DOCENTE' | 'APODERADO' | 'ADMINISTRADOR'

/** Ficha de la cuenta con su perfil; `version` hace de ETag en la edición. */
export interface DetalleUsuario {
  id: string
  nombres: string
  apellidos: string
  documento: string
  correo: string | null
  rol: string
  estado: EstadoCuenta
  cargo: string | null
  codigoDocente: string | null
  especialidad: string | null
  telefono: string | null
  codigoAlumno: string | null
  invitacionVenceEn: string | null
  version: number
}

export interface CuerpoUsuario {
  rol: RolCreable
  nombres: string
  apellidos: string
  documento: string
  correo: string
  cargo: string
  especialidad: string
  telefono: string
}

/** Los campos vacíos viajan como nulos; los que no corresponden al rol no se mandan. */
function cuerpoDe(d: CuerpoUsuario) {
  const o = (v: string) => (v.trim() === '' ? null : v.trim())
  return {
    rol: d.rol,
    nombres: d.nombres,
    apellidos: d.apellidos,
    documento: d.documento,
    correo: d.correo,
    cargo: d.rol === 'ADMINISTRADOR' ? o(d.cargo) : null,
    especialidad: d.rol === 'DOCENTE' ? o(d.especialidad) : null,
    telefono: d.rol === 'APODERADO' ? o(d.telefono) : null,
  }
}

export function useDetalleUsuario(id: string | null) {
  return useQuery({
    queryKey: ['usuario', id],
    queryFn: () => api<DetalleUsuario>(`/usuarios/${id}`),
    enabled: !!id,
    staleTime: 0,
  })
}

/** Crea la cuenta pendiente y le manda la invitación de 72 horas (S-77). */
export function useCrearUsuario() {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (datos: CuerpoUsuario) => api<DetalleUsuario>('/usuarios', { method: 'POST', cuerpo: cuerpoDe(datos) }),
    onSuccess: () => cliente.invalidateQueries({ queryKey: ['usuarios'] }),
  })
}

export function useActualizarUsuario(id: string) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: ({ version, ...datos }: CuerpoUsuario & { version: number }) =>
      api<DetalleUsuario>(`/usuarios/${id}`, {
        method: 'PUT',
        cuerpo: (({ rol: _rol, ...resto }) => resto)(cuerpoDe(datos)),
        cabeceras: { 'If-Match': `"${version}"` },
      }),
    onSuccess: () => {
      void cliente.invalidateQueries({ queryKey: ['usuarios'] })
      void cliente.invalidateQueries({ queryKey: ['usuario', id] })
    },
  })
}

/** Habilita la cuenta (FA-2: el alumno exige matrícula activa). Una cuenta sin contraseña recibe la invitación. */
export function useActivarUsuario() {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: ({ id, correo }: { id: string; correo?: string }) =>
      api<DetalleUsuario>(`/usuarios/${id}/activacion`, { method: 'PATCH', cuerpo: correo ? { correo } : {} }),
    onSuccess: () => cliente.invalidateQueries({ queryKey: ['usuarios'] }),
  })
}

/** Desactiva la cuenta y corta sus sesiones de inmediato. */
export function useDesactivarUsuario() {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api<DetalleUsuario>(`/usuarios/${id}/desactivacion`, { method: 'PATCH' }),
    onSuccess: () => cliente.invalidateQueries({ queryKey: ['usuarios'] }),
  })
}

/** «Reenviar invitación»: la anterior queda anulada. */
export function useReenviarInvitacion() {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api<DetalleUsuario>(`/usuarios/${id}/invitacion`, { method: 'POST' }),
    onSuccess: () => cliente.invalidateQueries({ queryKey: ['usuarios'] }),
  })
}

// ---- RF-02: datos personales de estudiantes y apoderados ----

export interface FichaAlumno {
  id: string
  codigo: string
  nombres: string
  apellidos: string
  documento: string
  correo: string | null
  fechaNacimiento: string
  parentesco: string
  apoderadoId: string
  apoderado: string | null
  cuentaActiva: boolean
  version: number
}

export interface FichaApoderado {
  id: string
  nombres: string
  apellidos: string
  documento: string
  correo: string | null
  telefono: string | null
  cuentaActiva: boolean
  version: number
}

export interface CambiosAlumno {
  nombres: string
  apellidos: string
  documento: string
  correo: string | null
  fechaNacimiento: string
  parentesco: string
}

export interface CambiosApoderado {
  nombres: string
  apellidos: string
  documento: string
  correo: string | null
  telefono: string | null
}

const etag = (version: number) => `"${version}"`

export function useFichaAlumno(id: string) {
  return useQuery({ queryKey: ['ficha-alumno', id], queryFn: () => api<FichaAlumno>(`/alumnos/${id}`), staleTime: 0 })
}

export function useFichaApoderado(id: string | undefined) {
  return useQuery({
    queryKey: ['ficha-apoderado', id],
    queryFn: () => api<FichaApoderado>(`/apoderados/${id}`),
    enabled: !!id,
    staleTime: 0,
  })
}

export function useActualizarAlumno(id: string) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: ({ cambios, version }: { cambios: CambiosAlumno; version: number }) =>
      api<FichaAlumno>(`/alumnos/${id}`, { method: 'PUT', cuerpo: cambios, cabeceras: { 'If-Match': etag(version) } }),
    onSuccess: (ficha) => cliente.setQueryData(['ficha-alumno', id], ficha),
  })
}

export function useActualizarApoderado(id: string) {
  const cliente = useQueryClient()
  return useMutation({
    mutationFn: ({ cambios, version }: { cambios: CambiosApoderado; version: number }) =>
      api<FichaApoderado>(`/apoderados/${id}`, { method: 'PUT', cuerpo: cambios, cabeceras: { 'If-Match': etag(version) } }),
    onSuccess: (ficha) => cliente.setQueryData(['ficha-apoderado', id], ficha),
  })
}
