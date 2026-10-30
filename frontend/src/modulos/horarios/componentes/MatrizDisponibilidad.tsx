import { DIAS, NOMBRE_DIA, type BloqueHorario, type DiaSemana } from '../api'

interface Fila {
  clave: string
  horaInicio: string
  horaFin: string
  porDia: Map<DiaSemana, BloqueHorario>
}

/** Agrupa los bloques en filas por franja horaria; los huecos entre franjas (el recreo) quedan como filas aparte. */
function armarFilas(bloques: BloqueHorario[]): { filas: (Fila | { recreo: string; desde: string; hasta: string })[]; dias: DiaSemana[] } {
  const dias = DIAS.filter((d) => bloques.some((b) => b.diaSemana === d))
  const franjas = new Map<string, Fila>()
  for (const b of bloques) {
    const clave = `${b.horaInicio}-${b.horaFin}`
    const fila = franjas.get(clave) ?? { clave, horaInicio: b.horaInicio, horaFin: b.horaFin, porDia: new Map() }
    fila.porDia.set(b.diaSemana, b)
    franjas.set(clave, fila)
  }
  const ordenadas = [...franjas.values()].sort((a, b) => a.horaInicio.localeCompare(b.horaInicio))
  const filas: (Fila | { recreo: string; desde: string; hasta: string })[] = []
  ordenadas.forEach((f, i) => {
    const anterior = ordenadas[i - 1]
    if (anterior && anterior.horaFin < f.horaInicio) {
      filas.push({ recreo: `${anterior.clave}|${f.clave}`, desde: anterior.horaFin, hasta: f.horaInicio })
    }
    filas.push(f)
  })
  return { filas, dias }
}

/**
 * Matriz de días y bloques del docente (UC-20, wireframe p. 28): cada celda alterna entre «Disponible» y
 * «No disponible». Con `soloLectura` se muestra la última disponibilidad confirmada sin poder cambiarla (FE-1).
 */
export function MatrizDisponibilidad({
  bloques,
  seleccionados,
  soloLectura,
  alAlternar,
}: {
  bloques: BloqueHorario[]
  seleccionados: ReadonlySet<string>
  soloLectura: boolean
  alAlternar: (bloqueId: string) => void
}) {
  const { filas, dias } = armarFilas(bloques)
  if (dias.length === 0) return <p className="muted">El año aún no tiene bloques horarios configurados.</p>
  return (
    <div className="tabla-scroll">
      <table className="matriz-disponibilidad" aria-label="Disponibilidad por día y bloque">
        <thead>
          <tr>
            <th>Bloque</th>
            {dias.map((d) => (
              <th key={d}>{NOMBRE_DIA[d]}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {filas.map((f) =>
            'recreo' in f ? (
              <tr key={f.recreo} className="fila-recreo">
                <td className="nowrap">
                  {f.desde} – {f.hasta}
                </td>
                <td colSpan={dias.length} className="muted">
                  Recreo
                </td>
              </tr>
            ) : (
              <tr key={f.clave}>
                <td className="nowrap">
                  {f.horaInicio} – {f.horaFin}
                </td>
                {dias.map((d) => {
                  const bloque = f.porDia.get(d)
                  if (!bloque) return <td key={d} />
                  const marcado = seleccionados.has(bloque.id)
                  const nombre = `${NOMBRE_DIA[d]} de ${f.horaInicio} a ${f.horaFin}`
                  return (
                    <td key={d}>
                      <button
                        type="button"
                        className={marcado ? 'celda-disp celda-disp-si' : 'celda-disp'}
                        aria-pressed={marcado}
                        aria-label={`${nombre}: ${marcado ? 'disponible' : 'no disponible'}`}
                        disabled={soloLectura}
                        onClick={() => alAlternar(bloque.id)}
                      >
                        {marcado ? 'Disponible' : '—'}
                      </button>
                    </td>
                  )
                })}
              </tr>
            ),
          )}
        </tbody>
      </table>
    </div>
  )
}
