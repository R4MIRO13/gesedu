import { useState, type ReactNode } from 'react'
import { hhmm } from '../formato'

const DIAS = ['LUNES', 'MARTES', 'MIERCOLES', 'JUEVES', 'VIERNES', 'SABADO', 'DOMINGO'] as const
type Dia = (typeof DIAS)[number]

const NOMBRE_DIA: Record<Dia, string> = {
  LUNES: 'Lunes',
  MARTES: 'Martes',
  MIERCOLES: 'Miércoles',
  JUEVES: 'Jueves',
  VIERNES: 'Viernes',
  SABADO: 'Sábado',
  DOMINGO: 'Domingo',
}

/** Lo mínimo que la matriz necesita saber de un bloque horario. */
export interface BloqueMatriz {
  id: string
  diaSemana: Dia
  horaInicio: string
  horaFin: string
}

interface Props<B extends BloqueMatriz> {
  bloques: readonly B[]
  /** Contenido de la celda de un bloque (por ejemplo, curso y docente). */
  contenido: (bloque: B) => ReactNode
  seleccionadoId?: string | null
  /** Si se pasa, las celdas son botones y devuelven el bloque elegido. */
  onSeleccionar?: (bloque: B) => void
}

/**
 * Matriz de horario: días en columnas y franjas en filas (escritorio) o una lista del día elegido con pestañas
 * (móvil, RNF-02). El CSS decide cuál se ve; las dos comparten el mismo contenido. Sirve para el borrador (UC-21)
 * y para el horario publicado en solo lectura (UC-23).
 */
export function MatrizHorario<B extends BloqueMatriz>({ bloques, contenido, seleccionadoId, onSeleccionar }: Props<B>) {
  const dias = DIAS.filter((d) => bloques.some((b) => b.diaSemana === d))
  const [diaElegido, setDiaElegido] = useState<Dia | null>(null)
  const diaActivo = diaElegido && dias.includes(diaElegido) ? diaElegido : (dias[0] ?? null)

  if (dias.length === 0) {
    return <p className="alerta">No hay bloques horarios configurados para este año.</p>
  }

  // Franjas distintas, por hora de inicio. Un día puede no tener alguna franja (por ejemplo, un día más corto).
  const franjas = [...new Set(bloques.map((b) => `${hhmm(b.horaInicio)}-${hhmm(b.horaFin)}`))].sort()
  const bloquePorCelda = new Map(bloques.map((b) => [`${b.diaSemana}|${hhmm(b.horaInicio)}-${hhmm(b.horaFin)}`, b]))

  function celda(bloque: B) {
    const etiqueta = `${NOMBRE_DIA[bloque.diaSemana]} ${hhmm(bloque.horaInicio)} a ${hhmm(bloque.horaFin)}`
    const clases = `celda${seleccionadoId === bloque.id ? ' celda-activa' : ''}`
    return onSeleccionar ? (
      <button
        type="button"
        className={clases}
        aria-pressed={seleccionadoId === bloque.id}
        onClick={() => onSeleccionar(bloque)}
      >
        {/* WCAG 2.5.3: el nombre accesible empieza con el día y la hora y conserva el texto visible de la celda */}
        <span className="solo-lectores">{etiqueta}. </span>
        {contenido(bloque)}
      </button>
    ) : (
      <div className={clases} role="group" aria-label={etiqueta}>
        {contenido(bloque)}
      </div>
    )
  }

  return (
    <div className="matriz">
      <div className="matriz-escritorio tabla-scroll">
        <table aria-label="Horario semanal">
          <thead>
            <tr>
              <th scope="col">Hora</th>
              {dias.map((d) => (
                <th key={d} scope="col">
                  {NOMBRE_DIA[d]}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {franjas.map((franja) => (
              <tr key={franja}>
                <th scope="row" className="hora-franja">
                  {franja.replace('-', ' – ')}
                </th>
                {dias.map((d) => {
                  const bloque = bloquePorCelda.get(`${d}|${franja}`)
                  return <td key={d}>{bloque ? celda(bloque) : <span className="muted">—</span>}</td>
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="matriz-movil">
        <div className="pestanas" role="tablist" aria-label="Día de la semana">
          {dias.map((d) => (
            <button
              key={d}
              type="button"
              role="tab"
              aria-selected={d === diaActivo}
              className={d === diaActivo ? 'pestana pestana-activa' : 'pestana'}
              onClick={() => setDiaElegido(d)}
            >
              {NOMBRE_DIA[d].slice(0, 3)}
            </button>
          ))}
        </div>
        <ul className="lista-dia" aria-label={diaActivo ? `Clases del ${NOMBRE_DIA[diaActivo]}` : undefined}>
          {bloques
            .filter((b) => b.diaSemana === diaActivo)
            .sort((a, b) => a.horaInicio.localeCompare(b.horaInicio))
            .map((b) => (
              <li key={b.id}>
                <span className="hora">
                  {hhmm(b.horaInicio)} – {hhmm(b.horaFin)}
                </span>
                {celda(b)}
              </li>
            ))}
        </ul>
      </div>
    </div>
  )
}
