import { fechaCorta } from '../../../compartido/formato'
import type { ProblemaAnio } from '../api'

interface Tramo {
  numero: number
  fechaInicio: string
  fechaFin: string
}

const MESES = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic']
const ROMANOS = ['I', 'II', 'III', 'IV']
const FECHA = /^\d{4}-\d{2}-\d{2}$/

function dia(iso: string): number {
  const [a = 0, m = 1, d = 1] = iso.split('-').map(Number)
  return Date.UTC(a, m - 1, d) / 86_400_000
}

/**
 * Línea de tiempo del año escolar (wireframe p. 20): una franja por bimestre sobre la duración del año y, en rojo,
 * los tramos que el backend dice que se cruzan. Solo dibuja; no decide si las fechas son válidas.
 */
export function LineaTiempoAnio({
  fechaInicio,
  fechaFin,
  bimestres,
  problemas,
}: {
  fechaInicio: string
  fechaFin: string
  bimestres: Tramo[]
  problemas: ProblemaAnio[]
}) {
  if (!FECHA.test(fechaInicio) || !FECHA.test(fechaFin) || dia(fechaFin) <= dia(fechaInicio)) return null
  const desde = dia(fechaInicio)
  const total = dia(fechaFin) - desde
  const posicion = (iso: string) => Math.min(100, Math.max(0, ((dia(iso) - desde) / total) * 100))

  // Un mes por columna, con el ancho que ocupa dentro del año
  const [anioInicio = 0, mesInicio = 1] = fechaInicio.split('-').map(Number)
  const [anioFin = 0, mesFin = 1] = fechaFin.split('-').map(Number)
  const meses: { clave: string; texto: string; izquierda: number }[] = []
  for (let a = anioInicio, m = mesInicio; a < anioFin || (a === anioFin && m <= mesFin); m === 12 ? ((m = 1), a++) : m++) {
    const primero = `${a}-${String(m).padStart(2, '0')}-01`
    meses.push({ clave: primero, texto: MESES[m - 1] ?? '', izquierda: posicion(primero) })
  }

  const cruces = problemas.filter((p) => p.tipo === 'BIMESTRES_SUPERPUESTOS' && p.desde && p.hasta)
  return (
    <section className="linea-tiempo" aria-label={`Línea de tiempo del año escolar ${anioInicio}`}>
      <h3>Línea de tiempo del año escolar {anioInicio}</h3>
      <div className="linea-tiempo-pista">
        {bimestres
          .filter((b) => FECHA.test(b.fechaInicio) && FECHA.test(b.fechaFin) && dia(b.fechaFin) > dia(b.fechaInicio))
          .map((b) => {
            const izquierda = posicion(b.fechaInicio)
            const ancho = Math.max(0.5, posicion(b.fechaFin) - izquierda)
            const conProblema = problemas.some((p) => p.bimestre === b.numero || p.otroBimestre === b.numero)
            return (
              <div
                key={b.numero}
                className={conProblema ? 'tramo tramo-error' : 'tramo'}
                style={{ left: `${izquierda}%`, width: `${ancho}%`, top: `${(b.numero - 1) * 1.6}rem` }}
                title={`Bimestre ${ROMANOS[b.numero - 1]}: ${fechaCorta(b.fechaInicio)} – ${fechaCorta(b.fechaFin)}`}
              >
                Bimestre {ROMANOS[b.numero - 1]}
              </div>
            )
          })}
        {cruces.map((c) => (
          <div
            key={`${c.bimestre}-${c.otroBimestre}`}
            className="tramo-cruce"
            style={{ left: `${posicion(c.desde ?? fechaInicio)}%`, width: `${Math.max(0.5, posicion(c.hasta ?? fechaFin) - posicion(c.desde ?? fechaInicio))}%` }}
            title={`Se superponen del ${fechaCorta(c.desde ?? '')} al ${fechaCorta(c.hasta ?? '')}`}
          />
        ))}
      </div>
      <div className="linea-tiempo-meses" aria-hidden="true">
        {meses.map((m) => (
          <span key={m.clave} style={{ left: `${m.izquierda}%` }}>
            {m.texto}
          </span>
        ))}
      </div>
      <p className="muted linea-tiempo-extremos">
        <span>{fechaCorta(fechaInicio)}</span>
        <span>{fechaCorta(fechaFin)}</span>
      </p>
    </section>
  )
}
