import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useState } from 'react'
import { z } from 'zod'
import { exigirPermiso } from '../../../../compartido/auth/guardias'
import { MatrizHorario } from '../../../../compartido/componentes/MatrizHorario'
import {
  useBorrador,
  type Asignacion,
  type Borrador,
  type BloqueHorario,
  type Perspectiva,
} from '../../../../modulos/horarios/api'
import { PanelAsignacion } from '../../../../modulos/horarios/componentes/PanelAsignacion'

const esquemaBusqueda = z.object({
  perspectiva: z.enum(['SECCION', 'DOCENTE']).catch('SECCION'),
  seccionId: z.string().optional().catch(undefined),
  docenteId: z.string().optional().catch(undefined),
})

export const Route = createFileRoute('/intranet/horarios/$anio/elaborar')({
  beforeLoad: () => exigirPermiso('HORARIO_GESTIONAR'),
  validateSearch: (busqueda) => esquemaBusqueda.parse(busqueda),
  component: ElaborarHorario,
})

function ElaborarHorario() {
  const { anio: anioTexto } = Route.useParams()
  const { perspectiva, seccionId, docenteId } = Route.useSearch()
  const navegar = useNavigate({ from: Route.fullPath })
  const anio = Number(anioTexto)
  const borrador = useBorrador(anio, perspectiva, seccionId, docenteId)
  const [bloqueId, setBloqueId] = useState<string | null>(null)

  function cambiarPerspectiva(nueva: Perspectiva) {
    setBloqueId(null)
    void navegar({ search: (previa) => ({ ...previa, perspectiva: nueva }) })
  }

  return (
    <>
      <div className="encabezado">
        <h1>Elaborar horario {anio}</h1>
        {borrador.data && <span className="etiqueta">Borrador · versión {borrador.data.version}</span>}
      </div>

      {borrador.isPending && <p className="muted">Cargando…</p>}
      {borrador.isError && (
        <p className="alerta alerta-error" role="alert">
          {borrador.error.message}
        </p>
      )}

      {borrador.data && (
        <>
          <div className="filtros">
            <div className="pestanas" role="radiogroup" aria-label="Perspectiva">
              {(['SECCION', 'DOCENTE'] as const).map((p) => (
                <button
                  key={p}
                  type="button"
                  role="radio"
                  aria-checked={perspectiva === p}
                  className={perspectiva === p ? 'pestana pestana-activa' : 'pestana'}
                  onClick={() => cambiarPerspectiva(p)}
                >
                  {p === 'SECCION' ? 'Sección' : 'Docente'}
                </button>
              ))}
            </div>
            {perspectiva === 'SECCION' ? (
              <label className="campo campo-corto">
                Sección
                <select
                  value={borrador.data.seccionId ?? ''}
                  onChange={(e) => {
                    setBloqueId(null)
                    void navegar({ search: (previa) => ({ ...previa, seccionId: e.target.value }) })
                  }}
                >
                  {borrador.data.secciones.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.nombre}
                    </option>
                  ))}
                </select>
              </label>
            ) : (
              <label className="campo">
                Docente
                <select
                  value={borrador.data.docenteId ?? ''}
                  onChange={(e) => {
                    setBloqueId(null)
                    void navegar({ search: (previa) => ({ ...previa, docenteId: e.target.value }) })
                  }}
                >
                  {borrador.data.docentes.map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.nombre}
                    </option>
                  ))}
                </select>
              </label>
            )}
          </div>

          <Matriz
            anio={anio}
            borrador={borrador.data}
            bloqueId={bloqueId}
            onSeleccionar={setBloqueId}
            onRecargar={() => void borrador.refetch()}
          />
        </>
      )}
    </>
  )
}

function Matriz({
  anio,
  borrador,
  bloqueId,
  onSeleccionar,
  onRecargar,
}: {
  anio: number
  borrador: Borrador
  bloqueId: string | null
  onSeleccionar: (id: string | null) => void
  onRecargar: () => void
}) {
  const porBloque = new Map<string, Asignacion>(borrador.asignaciones.map((a) => [a.bloqueId, a]))
  const seleccionado = borrador.bloques.find((b) => b.id === bloqueId) ?? null
  const seccion = borrador.secciones.find((s) => s.id === borrador.seccionId)
  const esSeccion = borrador.perspectiva === 'SECCION'
  // En la perspectiva Docente solo se pueden corregir las clases que ya existen (la sección la fija la clase)
  const asignacionSeleccionada = seleccionado ? porBloque.get(seleccionado.id) : undefined
  const seccionDelPanel = esSeccion ? seccion : borrador.secciones.find((s) => s.id === asignacionSeleccionada?.seccionId)

  function contenido(bloque: BloqueHorario) {
    const a = porBloque.get(bloque.id)
    if (!a) return <span className="muted">Libre</span>
    return esSeccion ? (
      <>
        <strong>{a.curso}</strong>
        <span>{a.docente}</span>
      </>
    ) : (
      <>
        <strong>{a.seccion}</strong>
        <span>{a.curso}</span>
      </>
    )
  }

  return (
    <div className="elaborar">
      <MatrizHorario
        bloques={borrador.bloques}
        contenido={contenido}
        seleccionadoId={bloqueId}
        onSeleccionar={(b) => onSeleccionar(b.id)}
      />
      <aside className="lateral-elaborar">
        {esSeccion && borrador.carga && (
          <section className="tarjeta" aria-label="Carga horaria de la sección">
            <h2>Carga horaria · {seccion?.nombre}</h2>
            <p>
              <strong>
                {borrador.carga.totalAsignadas} de {borrador.carga.totalRequeridas}
              </strong>{' '}
              horas asignadas
            </p>
            <ul className="carga">
              {borrador.carga.cursos.map((c) => (
                <li key={c.cursoId} className={c.horasAsignadas >= c.horasRequeridas ? 'carga-completa' : undefined}>
                  <span>{c.curso}</span>
                  <span className="muted">
                    {c.horasAsignadas} / {c.horasRequeridas} h
                  </span>
                </li>
              ))}
            </ul>
          </section>
        )}

        {seleccionado && seccionDelPanel && (
          <PanelAsignacion
            // Al cambiar de bloque o de clase el panel empieza de cero
            key={`${seleccionado.id}-${asignacionSeleccionada?.id ?? 'nueva'}-${borrador.version}`}
            anio={anio}
            seccionId={seccionDelPanel.id}
            seccionNombre={seccionDelPanel.nombre}
            bloque={seleccionado}
            asignacion={asignacionSeleccionada}
            version={borrador.version}
            onTerminar={() => onSeleccionar(null)}
            onRecargar={onRecargar}
          />
        )}
        {seleccionado && !seccionDelPanel && (
          <p className="alerta">
            Para agregar una clase elige la perspectiva Sección: la clase se arma sobre una sección.
          </p>
        )}
        {!seleccionado && <p className="muted">Elige un bloque de la matriz para agregar o modificar una clase.</p>}
      </aside>
    </div>
  )
}
