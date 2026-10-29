import { createFileRoute, Link } from '@tanstack/react-router'
import { useState } from 'react'
import { exigirPermiso } from '../../../compartido/auth/guardias'
import { FILTROS_BANDEJA, useBandeja, type EstadoSolicitud, type FiltrosBandeja } from '../../../modulos/matricula/api'

export const Route = createFileRoute('/intranet/matriculas/')({
  beforeLoad: () => exigirPermiso('MATRICULA_EVALUAR'),
  component: Bandeja,
})

const ESTADOS: { valor: EstadoSolicitud | ''; texto: string }[] = [
  { valor: 'PENDIENTE_REVISION', texto: 'Pendientes de revisión' },
  { valor: 'APROBADA', texto: 'Aprobadas' },
  { valor: 'RECHAZADA', texto: 'Rechazadas' },
  { valor: '', texto: 'Todas' },
]

const TIPOS = { NUEVO_INGRESO: 'Nuevo ingreso', RECURRENTE: 'Recurrente' } as const

function Bandeja() {
  const [borrador, setBorrador] = useState<FiltrosBandeja>(FILTROS_BANDEJA)
  const [filtros, setFiltros] = useState<FiltrosBandeja>(FILTROS_BANDEJA)
  const solicitudes = useBandeja(filtros)
  const datos = solicitudes.data

  return (
    <>
      <div className="encabezado">
        <div>
          <h1>Solicitudes de matrícula</h1>
          <p className="muted">Revisa cada solicitud, valida los requisitos y confirma o rechaza la matrícula.</p>
        </div>
      </div>

      <form
        className="tarjeta filtros"
        onSubmit={(e) => {
          e.preventDefault()
          setFiltros({ ...borrador, pagina: 0 })
        }}
      >
        <label className="campo">
          Estado
          <select value={borrador.estado} onChange={(e) => setBorrador({ ...borrador, estado: e.target.value as FiltrosBandeja['estado'] })}>
            {ESTADOS.map((e) => (
              <option key={e.valor} value={e.valor}>
                {e.texto}
              </option>
            ))}
          </select>
        </label>
        <label className="campo">
          Buscar
          <input
            type="search"
            placeholder="Código, alumno o documento"
            value={borrador.q}
            onChange={(e) => setBorrador({ ...borrador, q: e.target.value })}
          />
        </label>
        <div className="acciones">
          <button className="btn btn-primario" type="submit">
            Filtrar
          </button>
        </div>
      </form>

      {solicitudes.isPending && <p className="muted">Cargando…</p>}
      {solicitudes.isError && (
        <p className="alerta alerta-error" role="alert">
          {solicitudes.error.message}
        </p>
      )}

      {datos && (
        <>
          <div className="tabla-scroll">
            <table aria-label="Solicitudes de matrícula">
              <thead>
                <tr>
                  <th>Código</th>
                  <th>Alumno</th>
                  <th>Grado</th>
                  <th>Tipo</th>
                  <th>Fecha</th>
                  <th>Estado</th>
                  <th>
                    <span className="solo-lectores">Acción</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {datos.contenido.map((s) => (
                  <tr key={s.codigo}>
                    <td className="nowrap">
                      <code>{s.codigo}</code>
                    </td>
                    <td>{s.alumno}</td>
                    <td>{s.grado}</td>
                    <td>{TIPOS[s.tipoEstudiante]}</td>
                    <td className="nowrap">{s.fechaSolicitud}</td>
                    <td>
                      <span className="etiqueta">{s.estadoVisible}</span>
                      {s.estadoDocumentacion === 'INCOMPLETA' && s.estado === 'PENDIENTE_REVISION' && (
                        <span className="etiqueta etiqueta-no"> Documentos incompletos</span>
                      )}
                    </td>
                    <td>
                      <Link className="btn" to="/intranet/matriculas/$codigo" params={{ codigo: s.codigo }}>
                        {s.estado === 'PENDIENTE_REVISION' ? 'Evaluar' : 'Ver'}
                      </Link>
                    </td>
                  </tr>
                ))}
                {datos.contenido.length === 0 && (
                  <tr>
                    <td colSpan={7} className="muted vacio">
                      No hay solicitudes para los filtros indicados.
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
              Página {datos.pagina + 1} de {Math.max(datos.totalPaginas, 1)} · {datos.totalElementos} solicitudes
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
