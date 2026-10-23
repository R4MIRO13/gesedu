import { createFileRoute } from '@tanstack/react-router'
import { useState, type SubmitEvent } from 'react'
import { obtenerSesion, tienePermiso, useSesion } from '../../../compartido/sesion'
import {
  DIAS,
  NOMBRE_DIA,
  useBloques,
  useCrearBloque,
  type BloqueHorario,
  type DiaSemana,
} from '../../../modulos/horarios/api'

export const Route = createFileRoute('/intranet/horarios/bloques')({
  component: Bloques,
})

const ANIO_INICIAL = 2027

function Bloques() {
  const sesion = useSesion()
  const [anio, setAnio] = useState(ANIO_INICIAL)
  const bloques = useBloques(anio)
  const puedeCrear = tienePermiso(sesion ?? obtenerSesion(), 'HORARIO_CONFIGURAR')

  const porDia = new Map<DiaSemana, BloqueHorario[]>()
  for (const b of bloques.data ?? []) porDia.set(b.diaSemana, [...(porDia.get(b.diaSemana) ?? []), b])
  const diasConBloques = DIAS.filter((d) => porDia.has(d))

  return (
    <>
      <div className="encabezado">
        <h1>Bloques horarios</h1>
        <label className="campo campo-corto">
          Año escolar
          <input
            type="number"
            min={2000}
            max={2100}
            value={anio}
            onChange={(e) => setAnio(Number(e.target.value) || ANIO_INICIAL)}
          />
        </label>
      </div>

      {bloques.isPending && <p className="muted">Cargando…</p>}
      {bloques.isError && (
        <p className="alerta alerta-error" role="alert">
          {bloques.error.message}
        </p>
      )}
      {bloques.isSuccess && diasConBloques.length === 0 && (
        <p className="alerta">No hay bloques horarios registrados para {anio}.</p>
      )}

      <div className="dias">
        {diasConBloques.map((dia) => (
          <section key={dia} className="tarjeta">
            <h3>{NOMBRE_DIA[dia]}</h3>
            <ul className="bloques">
              {porDia.get(dia)!.map((b) => (
                <li key={b.id}>
                  <span className="hora">
                    {b.horaInicio} – {b.horaFin}
                  </span>
                </li>
              ))}
            </ul>
          </section>
        ))}
      </div>

      {puedeCrear && <FormularioBloque anio={anio} />}
    </>
  )
}

function FormularioBloque({ anio }: { anio: number }) {
  const crear = useCrearBloque(anio)
  const [dia, setDia] = useState<DiaSemana>('LUNES')
  const [inicio, setInicio] = useState('')
  const [fin, setFin] = useState('')
  const [local, setLocal] = useState<string | null>(null)
  const [ok, setOk] = useState(false)

  function enviar(e: SubmitEvent<HTMLFormElement>) {
    e.preventDefault()
    setOk(false)
    setLocal(null)
    if (!inicio || !fin) {
      setLocal('Indica la hora de inicio y la de fin.')
      return
    }
    if (fin <= inicio) {
      setLocal('La hora de fin debe ser posterior a la hora de inicio.')
      return
    }
    crear.mutate(
      { diaSemana: dia, horaInicio: inicio, horaFin: fin },
      {
        onSuccess: () => {
          setOk(true)
          setInicio('')
          setFin('')
        },
      },
    )
  }

  const mensaje = local ?? crear.error?.message ?? null

  return (
    <form className="tarjeta formulario" onSubmit={enviar} noValidate>
      <h2>Nuevo bloque para {anio}</h2>
      <div className="fila">
        <label className="campo">
          Día
          <select value={dia} onChange={(e) => setDia(e.target.value as DiaSemana)}>
            {DIAS.map((d) => (
              <option key={d} value={d}>
                {NOMBRE_DIA[d]}
              </option>
            ))}
          </select>
        </label>
        <label className="campo">
          Hora de inicio
          <input type="time" value={inicio} onChange={(e) => setInicio(e.target.value)} />
        </label>
        <label className="campo">
          Hora de fin
          <input type="time" value={fin} onChange={(e) => setFin(e.target.value)} />
        </label>
      </div>
      {mensaje && (
        <p className="alerta alerta-error" role="alert">
          {mensaje}
        </p>
      )}
      {ok && !mensaje && <p className="alerta alerta-ok">Bloque creado.</p>}
      <button className="btn btn-primario" type="submit" disabled={crear.isPending}>
        {crear.isPending ? 'Guardando…' : 'Crear bloque'}
      </button>
    </form>
  )
}
