import { createFileRoute } from '@tanstack/react-router'
import { Fragment, useState } from 'react'
import { exigirPermiso } from '../../compartido/auth/guardias'
import { usePermiso } from '../../compartido/auth/usePermiso'
import { guardarArchivo } from '../../compartido/guardarArchivo'
import {
  exportarAuditoria,
  FILTROS_VACIOS,
  useCatalogosAuditoria,
  useDetalleRegistro,
  useRegistrosAuditoria,
  type FiltrosAuditoria,
  type FormatoExportacion,
} from '../../modulos/auditoria/api'

export const Route = createFileRoute('/intranet/auditoria')({
  beforeLoad: () => exigirPermiso('AUDITORIA_CONSULTAR'),
  component: Auditoria,
})

const formatoFecha = new Intl.DateTimeFormat('es-PE', {
  dateStyle: 'short',
  timeStyle: 'medium',
  timeZone: 'America/Lima',
})

function Auditoria() {
  const [borrador, setBorrador] = useState<FiltrosAuditoria>(FILTROS_VACIOS)
  const [filtros, setFiltros] = useState<FiltrosAuditoria>(FILTROS_VACIOS)
  const [abierto, setAbierto] = useState<string | null>(null)
  const [exportando, setExportando] = useState(false)
  const [errorExportacion, setErrorExportacion] = useState<string | null>(null)
  const registros = useRegistrosAuditoria(filtros)
  const catalogos = useCatalogosAuditoria()
  const puedeExportar = usePermiso('AUDITORIA_EXPORTAR')
  const datos = registros.data
  const acciones = catalogos.data?.modulos.find((m) => m.modulo === borrador.modulo)?.acciones ?? []

  function cambiar(campo: 'usuarioId' | 'modulo' | 'accion' | 'desde' | 'hasta', valor: string) {
    // Las acciones dependen del módulo: al cambiarlo se limpia la acción elegida
    setBorrador((b) => ({ ...b, [campo]: valor, ...(campo === 'modulo' ? { accion: '' } : {}) }))
  }

  async function exportar(formato: FormatoExportacion) {
    setErrorExportacion(null)
    setExportando(true)
    try {
      const archivo = await exportarAuditoria(filtros, formato)
      guardarArchivo(archivo.blob, archivo.nombre)
    } catch (e) {
      setErrorExportacion(e instanceof Error ? e.message : 'No se pudo exportar.')
    } finally {
      setExportando(false)
    }
  }

  return (
    <>
      <div className="encabezado">
        <div>
          <h1>Auditoría</h1>
          <p className="muted">Más recientes primero. Los registros no se pueden editar ni eliminar.</p>
        </div>
        {puedeExportar && (
          <details className="menu-exportar">
            <summary className="btn" aria-disabled={exportando}>
              {exportando ? 'Exportando…' : 'Exportar ▾'}
            </summary>
            <div className="menu-exportar-opciones" role="menu">
              <button type="button" role="menuitem" disabled={exportando} onClick={() => void exportar('xlsx')}>
                Excel (.xlsx)
              </button>
              <button type="button" role="menuitem" disabled={exportando} onClick={() => void exportar('csv')}>
                CSV
              </button>
            </div>
          </details>
        )}
      </div>

      <form
        className="tarjeta filtros"
        onSubmit={(e) => {
          e.preventDefault()
          setAbierto(null)
          setFiltros({ ...borrador, pagina: 0 })
        }}
      >
        <label className="campo">
          Usuario
          <select value={borrador.usuarioId} onChange={(e) => cambiar('usuarioId', e.target.value)}>
            <option value="">Todos</option>
            {catalogos.data?.usuarios.map((u) => (
              <option key={u.id} value={u.id}>
                {u.nombre}
              </option>
            ))}
          </select>
        </label>
        <label className="campo">
          Módulo
          <select value={borrador.modulo} onChange={(e) => cambiar('modulo', e.target.value)}>
            <option value="">Todos</option>
            {catalogos.data?.modulos.map((m) => (
              <option key={m.modulo} value={m.modulo}>
                {m.modulo}
              </option>
            ))}
          </select>
        </label>
        <label className="campo">
          Acción
          <select value={borrador.accion} onChange={(e) => cambiar('accion', e.target.value)} disabled={!borrador.modulo}>
            <option value="">Todas</option>
            {acciones.map((a) => (
              <option key={a} value={a}>
                {a}
              </option>
            ))}
          </select>
        </label>
        <label className="campo">
          Desde
          <input type="date" value={borrador.desde} onChange={(e) => cambiar('desde', e.target.value)} />
        </label>
        <label className="campo">
          Hasta
          <input type="date" value={borrador.hasta} onChange={(e) => cambiar('hasta', e.target.value)} />
        </label>
        <div className="acciones">
          <button className="btn btn-primario" type="submit">
            Filtrar
          </button>
          <button
            className="btn"
            type="button"
            onClick={() => {
              setBorrador(FILTROS_VACIOS)
              setFiltros(FILTROS_VACIOS)
              setAbierto(null)
            }}
          >
            Limpiar
          </button>
        </div>
      </form>

      {errorExportacion && (
        <p className="alerta alerta-error" role="alert">
          {errorExportacion}
        </p>
      )}
      {registros.isPending && <p className="muted">Cargando…</p>}
      {registros.isError && (
        <p className="alerta alerta-error" role="alert">
          {registros.error.message}
        </p>
      )}

      {datos && (
        <>
          <div className="tabla-scroll">
            <table aria-label="Registros de auditoría">
              <thead>
                <tr>
                  <th>Fecha y hora</th>
                  <th>Usuario</th>
                  <th>Módulo</th>
                  <th>Acción</th>
                  <th>Entidad</th>
                  <th>Descripción</th>
                  <th>
                    <span className="solo-lectores">Detalle</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {datos.contenido.map((r) => (
                  <Fragment key={r.id}>
                    <tr>
                      <td className="nowrap">{formatoFecha.format(new Date(r.fechaHora))}</td>
                      <td>{r.usuario ?? <em>Sistema</em>}</td>
                      <td>
                        <span className="etiqueta">{r.modulo}</span>
                      </td>
                      <td>
                        <code>{r.accion}</code>
                      </td>
                      <td>{r.tipoEntidad ?? '—'}</td>
                      <td>{r.descripcion ?? '—'}</td>
                      <td>
                        <button
                          type="button"
                          className="btn"
                          aria-expanded={abierto === r.id}
                          onClick={() => setAbierto(abierto === r.id ? null : r.id)}
                        >
                          {abierto === r.id ? 'Ocultar' : 'Ver cambios'}
                        </button>
                      </td>
                    </tr>
                    {abierto === r.id && (
                      <tr>
                        <td colSpan={7}>
                          <Cambios id={r.id} />
                        </td>
                      </tr>
                    )}
                  </Fragment>
                ))}
                {datos.contenido.length === 0 && (
                  <tr>
                    <td colSpan={7} className="muted vacio">
                      Sin registros para los filtros indicados.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="paginacion">
            <button
              className="btn"
              disabled={datos.pagina === 0}
              onClick={() => setFiltros((f) => ({ ...f, pagina: f.pagina - 1 }))}
            >
              ← Anterior
            </button>
            <span className="muted">
              Página {datos.pagina + 1} de {Math.max(datos.totalPaginas, 1)} · {datos.totalElementos} registros
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
    </>
  )
}

/** Los campos que cambiaron en una operación, con su valor anterior y el nuevo (RF-49). */
function Cambios({ id }: { id: string }) {
  const detalle = useDetalleRegistro(id)
  if (detalle.isPending) return <p className="muted">Cargando cambios…</p>
  if (detalle.isError) return <p className="alerta alerta-error">{detalle.error.message}</p>
  if (detalle.data.cambios.length === 0) return <p className="muted">Esta operación no registró cambios de campo.</p>
  return (
    <table className="cambios" aria-label="Cambios del registro">
      <thead>
        <tr>
          <th>Campo</th>
          <th>Valor anterior</th>
          <th>Valor nuevo</th>
        </tr>
      </thead>
      <tbody>
        {detalle.data.cambios.map((c) => (
          <tr key={c.campo}>
            <td>{c.campo}</td>
            <td>{c.valorAnterior ?? <span className="muted">—</span>}</td>
            <td>{c.valorNuevo ?? <span className="muted">—</span>}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
