import { createFileRoute } from '@tanstack/react-router'
import { useState } from 'react'
import { exigirPermiso } from '../../compartido/auth/guardias'
import { usePermiso } from '../../compartido/auth/usePermiso'
import { ApiError } from '../../compartido/api/errores'
import { DialogoUsuario } from '../../modulos/seguridad/componentes/DialogoUsuario'
import {
  FILTROS_USUARIOS,
  useActivarUsuario,
  useDesactivarUsuario,
  useDesbloquearUsuario,
  useReenviarInvitacion,
  useUsuarios,
  type EstadoCuenta,
  type FiltrosUsuarios,
  type UsuarioFila,
} from '../../modulos/seguridad/api'

export const Route = createFileRoute('/intranet/usuarios')({
  beforeLoad: () => exigirPermiso('USUARIO_CONSULTAR'),
  component: Usuarios,
})

const ROLES = [
  { valor: 'ADMINISTRADOR', texto: 'Administrador' },
  { valor: 'DOCENTE', texto: 'Docente' },
  { valor: 'ALUMNO', texto: 'Alumno' },
  { valor: 'APODERADO', texto: 'Apoderado' },
]
const NOMBRE_ROL: Record<string, string> = Object.fromEntries(ROLES.map((r) => [r.valor, r.texto]))

const ESTADOS: { valor: EstadoCuenta | ''; texto: string }[] = [
  { valor: '', texto: 'Todos' },
  { valor: 'ACTIVO', texto: 'Activos' },
  { valor: 'BLOQUEADO', texto: 'Bloqueados' },
  { valor: 'INACTIVO', texto: 'Inactivos' },
  { valor: 'PENDIENTE', texto: 'Pendientes de invitación' },
]

/** Intentos fallidos que bloquean una cuenta (RNF-06); el texto es el del wireframe. */
const INTENTOS_PARA_BLOQUEO = 5

const formatoHora = new Intl.DateTimeFormat('es-PE', { dateStyle: 'short', timeStyle: 'short', timeZone: 'America/Lima' })

function Usuarios() {
  const [borrador, setBorrador] = useState<FiltrosUsuarios>(FILTROS_USUARIOS)
  const [filtros, setFiltros] = useState<FiltrosUsuarios>(FILTROS_USUARIOS)
  const [aviso, setAviso] = useState<string | null>(null)
  const usuarios = useUsuarios(filtros)
  const desbloquear = useDesbloquearUsuario()
  const activar = useActivarUsuario()
  const desactivar = useDesactivarUsuario()
  const reenviar = useReenviarInvitacion()
  // Cuenta con el diálogo abierto: «nuevo» para el alta o el id de la que se edita
  const [dialogo, setDialogo] = useState<'nuevo' | string | null>(null)
  const [desactivando, setDesactivando] = useState<string | null>(null)
  const [habilitando, setHabilitando] = useState<{ u: UsuarioFila; correo: string } | null>(null)
  const [errorAccion, setErrorAccion] = useState<string | null>(null)
  const puedeGestionar = usePermiso('USUARIO_GESTIONAR')
  const datos = usuarios.data

  function mensajeDe(e: unknown) {
    return e instanceof ApiError ? e.message : 'No se pudo completar la acción'
  }

  function habilitar(u: UsuarioFila, correo?: string) {
    setAviso(null)
    setErrorAccion(null)
    activar.mutate(
      { id: u.id, correo },
      {
        onSuccess: (d) => {
          setHabilitando(null)
          setAviso(
            d.estado === 'PENDIENTE'
              ? `Enviamos la invitación a ${d.correo}. La cuenta se activa cuando defina su contraseña.`
              : `Se habilitó la cuenta de ${u.nombre}.`,
          )
        },
        onError: (e) => setErrorAccion(mensajeDe(e)),
      },
    )
  }

  function deshabilitar(u: UsuarioFila) {
    setAviso(null)
    setErrorAccion(null)
    desactivar.mutate(u.id, {
      onSuccess: () => setAviso(`Se desactivó la cuenta de ${u.nombre}. Sus sesiones se cerraron.`),
      onError: (e) => setErrorAccion(mensajeDe(e)),
      onSettled: () => setDesactivando(null),
    })
  }

  function reenviarInvitacion(u: UsuarioFila) {
    setAviso(null)
    setErrorAccion(null)
    reenviar.mutate(u.id, {
      onSuccess: (d) => setAviso(`Reenviamos la invitación a ${d.correo}. El enlace anterior ya no sirve.`),
      onError: (e) => setErrorAccion(mensajeDe(e)),
    })
  }

  function levantarBloqueo(u: UsuarioFila) {
    setAviso(null)
    desbloquear.mutate(u.id, { onSuccess: () => setAviso(`Se desbloqueó la cuenta de ${u.nombre}.`) })
  }

  return (
    <>
      <div className="encabezado">
        <div>
          <h1>Usuarios</h1>
          <p className="muted">Cuentas del sistema. Las cuentas no se eliminan: se desactivan.</p>
        </div>
        {puedeGestionar && (
          <button type="button" className="btn btn-primario" onClick={() => setDialogo('nuevo')}>
            Nuevo usuario
          </button>
        )}
      </div>

      <form
        className="tarjeta filtros"
        onSubmit={(e) => {
          e.preventDefault()
          setAviso(null)
          setFiltros({ ...borrador, pagina: 0 })
        }}
      >
        <label className="campo">
          Buscar
          <input
            type="search"
            placeholder="Nombre o correo"
            value={borrador.q}
            onChange={(e) => setBorrador({ ...borrador, q: e.target.value })}
          />
        </label>
        <label className="campo">
          Rol
          <select value={borrador.rol} onChange={(e) => setBorrador({ ...borrador, rol: e.target.value })}>
            <option value="">Todos</option>
            {ROLES.map((r) => (
              <option key={r.valor} value={r.valor}>
                {r.texto}
              </option>
            ))}
          </select>
        </label>
        <label className="campo">
          Estado
          <select value={borrador.estado} onChange={(e) => setBorrador({ ...borrador, estado: e.target.value as FiltrosUsuarios['estado'] })}>
            {ESTADOS.map((e) => (
              <option key={e.valor} value={e.valor}>
                {e.texto}
              </option>
            ))}
          </select>
        </label>
        <div className="acciones">
          <button className="btn btn-primario" type="submit">
            Filtrar
          </button>
        </div>
      </form>

      {aviso && (
        <p className="alerta alerta-ok" role="status">
          {aviso}
        </p>
      )}
      {errorAccion && (
        <p className="alerta alerta-error" role="alert">
          {errorAccion}
        </p>
      )}
      {desbloquear.error && (
        <p className="alerta alerta-error" role="alert">
          {desbloquear.error.message}
        </p>
      )}
      {usuarios.isPending && <p className="muted">Cargando…</p>}
      {usuarios.isError && (
        <p className="alerta alerta-error" role="alert">
          {usuarios.error.message}
        </p>
      )}

      {datos && (
        <>
          <div className="tabla-scroll">
            <table aria-label="Usuarios">
              <thead>
                <tr>
                  <th>Nombre</th>
                  <th>Correo</th>
                  <th>Rol</th>
                  <th>Estado</th>
                  <th>Último acceso</th>
                  <th>
                    <span className="solo-lectores">Acciones</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {datos.contenido.map((u) => (
                  <tr key={u.id}>
                    <td>{u.nombre}</td>
                    <td>{u.correo ?? <span className="muted">Sin correo</span>}</td>
                    <td>{NOMBRE_ROL[u.rol] ?? u.rol}</td>
                    <td>
                      <EstadoCuentaEtiqueta u={u} />
                    </td>
                    <td className="nowrap">{u.ultimoAcceso ? formatoHora.format(new Date(u.ultimoAcceso)) : '—'}</td>
                    <td>
                      {puedeGestionar && (
                        <AccionesCuenta
                          u={u}
                          ocupado={activar.isPending || desactivar.isPending || reenviar.isPending}
                          desactivando={desactivando === u.id}
                          onEditar={() => setDialogo(u.id)}
                          onHabilitar={() => (u.correo ? habilitar(u) : setHabilitando({ u, correo: '' }))}
                          onReenviar={() => reenviarInvitacion(u)}
                          onPedirDesactivar={() => setDesactivando(u.id)}
                          onConfirmarDesactivar={() => deshabilitar(u)}
                          onCancelarDesactivar={() => setDesactivando(null)}
                        />
                      )}
                      {puedeGestionar && u.estado === 'BLOQUEADO' && (
                        <button
                          type="button"
                          className="btn"
                          disabled={desbloquear.isPending}
                          onClick={() => levantarBloqueo(u)}
                          aria-label={`Desbloquear a ${u.nombre}`}
                        >
                          Desbloquear
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
                {datos.contenido.length === 0 && (
                  <tr>
                    <td colSpan={6} className="muted vacio">
                      No hay usuarios para los filtros indicados.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="paginacion">
            <button className="btn" disabled={datos.pagina === 0} onClick={() => setFiltros((f) => ({ ...f, pagina: f.pagina - 1 }))}>
              ← Anterior
            </button>
            <span className="muted">
              Página {datos.pagina + 1} de {Math.max(datos.totalPaginas, 1)} · {datos.totalElementos} usuarios
            </span>
            <button
              className="btn"
              disabled={datos.pagina + 1 >= datos.totalPaginas}
              onClick={() => setFiltros((f) => ({ ...f, pagina: f.pagina + 1 }))}
            >
              Siguiente →
            </button>
          </div>
        </>
      )}
      {habilitando && (
        <div className="dialogo-fondo">
          <form
            className="dialogo tarjeta"
            role="dialog"
            aria-modal="true"
            aria-label="Habilitar cuenta"
            onSubmit={(e) => {
              e.preventDefault()
              habilitar(habilitando.u, habilitando.correo.trim())
            }}
          >
            <h2>Habilitar la cuenta de {habilitando.u.nombre}</h2>
            <p className="muted">
              La cuenta no tiene correo. Indica el correo al que enviaremos la invitación para que defina su contraseña.
            </p>
            <label className="campo">
              Correo
              <input type="email" required value={habilitando.correo} onChange={(e) => setHabilitando({ ...habilitando, correo: e.target.value })} />
            </label>
            {errorAccion && (
              <p className="alerta alerta-error" role="alert">
                {errorAccion}
              </p>
            )}
            <div className="acciones">
              <button type="button" className="btn" onClick={() => setHabilitando(null)}>
                Cancelar
              </button>
              <button type="submit" className="btn btn-primario" disabled={activar.isPending}>
                Enviar invitación
              </button>
            </div>
          </form>
        </div>
      )}
      {dialogo && (
        <DialogoUsuario
          id={dialogo === 'nuevo' ? null : dialogo}
          onCerrar={(mensaje) => {
            setDialogo(null)
            if (mensaje) setAviso(mensaje)
          }}
        />
      )}
    </>
  )
}

interface AccionesProps {
  u: UsuarioFila
  ocupado: boolean
  desactivando: boolean
  onEditar: () => void
  onHabilitar: () => void
  onReenviar: () => void
  onPedirDesactivar: () => void
  onConfirmarDesactivar: () => void
  onCancelarDesactivar: () => void
}

/** Acciones de una cuenta según su estado: editar, habilitar, reenviar invitación y desactivar (UC-24). */
function AccionesCuenta({ u, ocupado, desactivando, onEditar, onHabilitar, onReenviar, onPedirDesactivar, onConfirmarDesactivar, onCancelarDesactivar }: AccionesProps) {
  if (desactivando) {
    return (
      <span role="group" aria-label={`Confirmar la desactivación de ${u.nombre}`}>
        Cierra sus sesiones.{' '}
        <button type="button" className="btn btn-primario" disabled={ocupado} onClick={onConfirmarDesactivar}>
          Confirmar
        </button>{' '}
        <button type="button" className="btn" onClick={onCancelarDesactivar}>
          Cancelar
        </button>
      </span>
    )
  }
  return (
    <>
      {u.rol !== 'ALUMNO' && (
        <button type="button" className="btn" onClick={onEditar} aria-label={`Editar a ${u.nombre}`}>
          Editar
        </button>
      )}{' '}
      {u.estado === 'PENDIENTE' && (
        <button type="button" className="btn" disabled={ocupado} onClick={onReenviar} aria-label={`Reenviar invitación a ${u.nombre}`}>
          Reenviar invitación
        </button>
      )}
      {u.estado === 'INACTIVO' && (
        <button type="button" className="btn" disabled={ocupado} onClick={onHabilitar} aria-label={`Activar a ${u.nombre}`}>
          Activar
        </button>
      )}
      {(u.estado === 'ACTIVO' || u.estado === 'BLOQUEADO') && (
        <button type="button" className="btn" disabled={ocupado} onClick={onPedirDesactivar} aria-label={`Desactivar a ${u.nombre}`}>
          Desactivar
        </button>
      )}{' '}
    </>
  )
}

function EstadoCuentaEtiqueta({ u }: { u: UsuarioFila }) {
  if (u.estado === 'BLOQUEADO') {
    const hasta = u.bloqueadoHasta ? formatoHora.format(new Date(u.bloqueadoHasta)) : null
    return (
      <span className="etiqueta etiqueta-no" title={hasta ? `Hasta ${hasta}` : undefined}>
        Bloqueado · {INTENTOS_PARA_BLOQUEO} intentos
      </span>
    )
  }
  const texto = { ACTIVO: 'Activo', INACTIVO: 'Inactivo', PENDIENTE: 'Pendiente de invitación', BLOQUEADO: 'Bloqueado' }[u.estado]
  return <span className={u.estado === 'ACTIVO' ? 'etiqueta etiqueta-si' : 'etiqueta'}>{texto}</span>
}
