import { zodResolver } from '@hookform/resolvers/zod'
import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { ApiError } from '../../../../compartido/api/errores'
import { exigirPermiso } from '../../../../compartido/auth/guardias'
import {
  useCargasHorarias,
  useGuardarCargas,
  useGuardarPeriodo,
  useGuardarVinculacion,
  usePeriodoDisponibilidad,
  useVinculaciones,
  type CargasHorarias,
  type PeriodoDisponibilidad,
  type Vinculacion,
  type VinculacionesAnio,
} from '../../../../modulos/horarios/api'
import { esquemaPeriodo, esquemaVinculacion, type DatosPeriodo, type DatosVinculacion } from '../../../../modulos/horarios/esquemas'

const SECCIONES = [
  { clave: 'periodo', texto: 'Periodo de disponibilidad' },
  { clave: 'docentes', texto: 'Docentes y cursos' },
  { clave: 'cargas', texto: 'Cargas horarias' },
] as const

const esquemaBusqueda = z.object({ seccion: z.enum(['periodo', 'docentes', 'cargas']).catch('periodo') })
import { resolverAnioActivo } from '../../../../modulos/academico/anioActivo'

export const Route = createFileRoute('/intranet/horarios/$anio/configuracion')({
  beforeLoad: async ({ params, location }: { params: { anio: string }; location: { href: string } }) => {
    exigirPermiso('HORARIO_CONFIGURAR')
    await resolverAnioActivo(params, location.href)
  },
  validateSearch: (busqueda) => esquemaBusqueda.parse(busqueda),
  component: ConfiguracionHorarios,
})

const formatoFecha = new Intl.DateTimeFormat('es-PE', { dateStyle: 'short', timeStyle: 'short', timeZone: 'America/Lima' })

function mensajeDe(error: unknown): string | null {
  if (!error) return null
  return error instanceof ApiError ? error.message : 'No se pudo guardar. Inténtalo de nuevo.'
}

/** RF-46, RF-47 y RF-43: periodo de disponibilidad, docentes con sus cursos y bloques semanales por grado. */
function ConfiguracionHorarios() {
  const { anio: anioTexto } = Route.useParams()
  const { seccion } = Route.useSearch()
  const navegar = useNavigate({ from: Route.fullPath })
  const anio = Number(anioTexto)
  return (
    <>
      <div className="encabezado">
        <div>
          <h1>Configuración de horarios {anio}</h1>
          <p className="muted">
            Lo que necesita el horario antes de armarlo: cuándo registran los docentes su disponibilidad, quién dicta qué y
            cuántos bloques semanales tiene cada curso.{' '}
            <Link to="/intranet/horarios/bloques">Bloques horarios</Link>
          </p>
        </div>
      </div>
      <div className="pestanas" role="tablist" aria-label="Secciones de la configuración">
        {SECCIONES.map((s) => (
          <button
            key={s.clave}
            type="button"
            role="tab"
            aria-selected={seccion === s.clave}
            className={seccion === s.clave ? 'pestana pestana-activa' : 'pestana'}
            onClick={() => void navegar({ search: { seccion: s.clave } })}
          >
            {s.texto}
          </button>
        ))}
      </div>
      {seccion === 'periodo' && <SeccionPeriodo anio={anio} />}
      {seccion === 'docentes' && <SeccionDocentes anio={anio} />}
      {seccion === 'cargas' && <SeccionCargas anio={anio} />}
    </>
  )
}

// ---- Periodo de registro de disponibilidad -------------------------------------------------------------------

function aLocal(iso: string | null): string {
  // El backend manda la hora de Lima con su desplazamiento: sus primeros 16 caracteres son «AAAA-MM-DDTHH:mm»
  return iso ? iso.slice(0, 16) : ''
}

function SeccionPeriodo({ anio }: { anio: number }) {
  const periodo = usePeriodoDisponibilidad(anio)
  if (periodo.isPending) return <p className="muted">Cargando…</p>
  if (periodo.isError) {
    return (
      <p className="alerta alerta-error" role="alert">
        {periodo.error.message}
      </p>
    )
  }
  return <FormularioPeriodo anio={anio} periodo={periodo.data} />
}

function FormularioPeriodo({ anio, periodo }: { anio: number; periodo: PeriodoDisponibilidad }) {
  const guardar = useGuardarPeriodo(anio)
  const [guardado, setGuardado] = useState(false)
  const valores = (p: PeriodoDisponibilidad): DatosPeriodo => ({
    inicio: aLocal(p.fechaHoraInicio),
    fin: aLocal(p.fechaHoraFin),
    habilitado: !p.configurado || p.estado === 'HABILITADO',
  })
  const form = useForm<DatosPeriodo>({ resolver: zodResolver(esquemaPeriodo), defaultValues: valores(periodo) })
  const errores = form.formState.errors

  useEffect(() => {
    form.reset(valores(periodo))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [periodo, form])

  function enviar(datos: DatosPeriodo) {
    setGuardado(false)
    guardar.mutate(
      { fechaHoraInicio: `${datos.inicio}:00-05:00`, fechaHoraFin: `${datos.fin}:00-05:00`, habilitado: datos.habilitado },
      { onSuccess: () => setGuardado(true) },
    )
  }

  return (
    <form className="tarjeta formulario" onSubmit={form.handleSubmit(enviar)} noValidate aria-label="Periodo de disponibilidad">
      <h2>Periodo de registro de disponibilidad</h2>
      <p>
        Estado:{' '}
        {periodo.abierto ? (
          <span className="etiqueta etiqueta-si">Abierto hasta {formatoFecha.format(new Date(periodo.fechaHoraFin!))}</span>
        ) : periodo.configurado ? (
          <span className="etiqueta etiqueta-no">Cerrado</span>
        ) : (
          <span className="etiqueta etiqueta-no">Sin configurar</span>
        )}
      </p>
      {guardado && (
        <p className="alerta alerta-ok" role="status">
          Se guardó el periodo de registro de disponibilidad.
        </p>
      )}
      {mensajeDe(guardar.error) && (
        <p className="alerta alerta-error" role="alert">
          {mensajeDe(guardar.error)}
        </p>
      )}
      <div className="filtros">
        <label className="campo">
          Desde
          <input type="datetime-local" {...form.register('inicio')} aria-invalid={!!errores.inicio} />
          {errores.inicio && <span className="error-campo">{errores.inicio.message}</span>}
        </label>
        <label className="campo">
          Hasta
          <input type="datetime-local" {...form.register('fin')} aria-invalid={!!errores.fin} />
          {errores.fin && <span className="error-campo">{errores.fin.message}</span>}
        </label>
      </div>
      <label className="opcion-copia">
        <input type="checkbox" {...form.register('habilitado')} />
        <span>
          Periodo habilitado <span className="muted">(desmárcalo para cerrarlo aunque no haya vencido)</span>
        </span>
      </label>
      <button type="submit" className="btn btn-primario" disabled={guardar.isPending}>
        Guardar periodo
      </button>
    </form>
  )
}

// ---- Docentes y cursos ---------------------------------------------------------------------------------------

function SeccionDocentes({ anio }: { anio: number }) {
  const vinculaciones = useVinculaciones(anio)
  const [editando, setEditando] = useState<string | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)
  if (vinculaciones.isPending) return <p className="muted">Cargando…</p>
  if (vinculaciones.isError) {
    return (
      <p className="alerta alerta-error" role="alert">
        {vinculaciones.error.message}
      </p>
    )
  }
  const datos = vinculaciones.data
  const nombreCurso = new Map(datos.cursos.map((c) => [c.id, c.nombre]))
  const cerrado = datos.estado === 'CERRADO'
  return (
    <section className="tarjeta" aria-label="Docentes y cursos">
      <h2>Docentes del año {anio}</h2>
      <p className="muted">
        Solo los docentes vinculados y habilitados entran al horario, y solo se ofrecen para los cursos con los que están asociados.
      </p>
      {aviso && (
        <p className="alerta alerta-ok" role="status">
          {aviso}
        </p>
      )}
      <div className="tabla-scroll">
        <table aria-label="Docentes del año">
          <thead>
            <tr>
              <th>Docente</th>
              <th>Vinculación</th>
              <th>Cursos</th>
              <th>Carga</th>
              <th>Disponibilidad</th>
              <th>
                <span className="solo-lectores">Acciones</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {datos.docentes.map((d) =>
              editando === d.docenteId ? (
                <tr key={d.docenteId} className="fila-activa">
                  <td colSpan={6}>
                    <FormularioVinculacion
                      anio={anio}
                      docente={d}
                      cursos={datos.cursos}
                      alTerminar={(mensaje) => {
                        setEditando(null)
                        setAviso(mensaje)
                      }}
                    />
                  </td>
                </tr>
              ) : (
                <tr key={d.docenteId}>
                  <td>
                    <strong>{d.nombre}</strong>
                  </td>
                  <td>
                    {!d.vinculado ? (
                      <span className="muted">No vinculado</span>
                    ) : d.habilitado ? (
                      <span className="etiqueta etiqueta-si">Habilitado</span>
                    ) : (
                      <span className="etiqueta etiqueta-no">Deshabilitado</span>
                    )}
                  </td>
                  <td>{d.cursoIds.map((id) => nombreCurso.get(id) ?? id).join(', ') || <span className="muted">—</span>}</td>
                  <td>{d.vinculado ? `${d.cargaSemanalReferencia} bloques` : '—'}</td>
                  <td>
                    {!d.vinculado ? (
                      '—'
                    ) : d.disponibilidadConfirmada ? (
                      <>
                        {d.bloquesDisponibles} bloques
                        {d.ultimaConfirmacion && <span className="muted"> · {formatoFecha.format(new Date(d.ultimaConfirmacion))}</span>}
                      </>
                    ) : (
                      <span className="etiqueta etiqueta-alerta">Sin confirmar</span>
                    )}
                  </td>
                  <td>
                    {!cerrado && (
                      <button
                        type="button"
                        className="btn"
                        aria-label={`${d.vinculado ? 'Editar' : 'Vincular'} a ${d.nombre}`}
                        onClick={() => (setAviso(null), setEditando(d.docenteId))}
                      >
                        {d.vinculado ? 'Editar' : 'Vincular'}
                      </button>
                    )}
                  </td>
                </tr>
              ),
            )}
          </tbody>
        </table>
      </div>
    </section>
  )
}

function FormularioVinculacion({
  anio,
  docente,
  cursos,
  alTerminar,
}: {
  anio: number
  docente: Vinculacion
  cursos: VinculacionesAnio['cursos']
  alTerminar: (mensaje: string) => void
}) {
  const guardar = useGuardarVinculacion(anio)
  const form = useForm<DatosVinculacion>({
    resolver: zodResolver(esquemaVinculacion),
    defaultValues: {
      habilitado: docente.vinculado ? docente.habilitado : true,
      cargaSemanalReferencia: String(docente.cargaSemanalReferencia),
      cursoIds: docente.cursoIds,
    },
  })
  const errores = form.formState.errors

  function enviar(datos: DatosVinculacion) {
    guardar.mutate(
      { docenteId: docente.docenteId, habilitado: datos.habilitado, cargaSemanalReferencia: Number(datos.cargaSemanalReferencia), cursoIds: datos.cursoIds },
      { onSuccess: () => alTerminar(`Se guardó la vinculación de ${docente.nombre}.`) },
    )
  }

  return (
    <form onSubmit={form.handleSubmit(enviar)} noValidate aria-label={`Vinculación de ${docente.nombre}`}>
      <h3>{docente.vinculado ? 'Editar' : 'Vincular'} a {docente.nombre}</h3>
      {mensajeDe(guardar.error) && (
        <p className="alerta alerta-error" role="alert">
          {mensajeDe(guardar.error)}
        </p>
      )}
      <div className="filtros">
        <label className="campo campo-corto">
          Carga semanal (bloques)
          <input inputMode="numeric" {...form.register('cargaSemanalReferencia')} aria-invalid={!!errores.cargaSemanalReferencia} />
          {errores.cargaSemanalReferencia && <span className="error-campo">{errores.cargaSemanalReferencia.message}</span>}
        </label>
        <label className="opcion-copia">
          <input type="checkbox" {...form.register('habilitado')} />
          <span>Habilitado para el año</span>
        </label>
      </div>
      <fieldset className="grupo-opciones">
        <legend>Cursos que puede dictar</legend>
        {cursos.map((c) => (
          <label key={c.id} className="opcion-copia">
            <input type="checkbox" value={c.id} {...form.register('cursoIds')} />
            <span>{c.nombre}</span>
          </label>
        ))}
      </fieldset>
      <div className="acciones">
        <button type="submit" className="btn btn-primario" disabled={guardar.isPending}>
          Guardar vinculación
        </button>
        <button type="button" className="btn" onClick={() => alTerminar('')}>
          Cancelar
        </button>
      </div>
    </form>
  )
}

// ---- Cargas horarias -----------------------------------------------------------------------------------------

function SeccionCargas({ anio }: { anio: number }) {
  const cargas = useCargasHorarias(anio)
  if (cargas.isPending) return <p className="muted">Cargando…</p>
  if (cargas.isError) {
    return (
      <p className="alerta alerta-error" role="alert">
        {cargas.error.message}
      </p>
    )
  }
  return <MatrizCargas anio={anio} cargas={cargas.data} />
}

function MatrizCargas({ anio, cargas }: { anio: number; cargas: CargasHorarias }) {
  const guardar = useGuardarCargas(anio)
  const [editadas, setEditadas] = useState<Record<string, string>>({})
  const [guardado, setGuardado] = useState(false)
  const clave = (gradoId: string, cursoId: string) => `${gradoId}|${cursoId}`

  const cambios = cargas.cursos.flatMap((fila) =>
    fila.grados.flatMap((celda) => {
      const texto = editadas[clave(celda.gradoId, fila.cursoId)]
      if (texto === undefined || texto === String(celda.bloques)) return []
      return [{ gradoId: celda.gradoId, cursoId: fila.cursoId, texto }]
    }),
  )
  const invalidas = cambios.filter((c) => !/^\d{1,2}$/.test(c.texto) || Number(c.texto) < 1 || Number(c.texto) > 20)

  function enviar() {
    setGuardado(false)
    guardar.mutate(
      cambios.map((c) => ({ gradoId: c.gradoId, cursoId: c.cursoId, bloques: Number(c.texto) })),
      {
        onSuccess: () => {
          setEditadas({})
          setGuardado(true)
        },
      },
    )
  }

  const totalDe = (gradoId: string) => cargas.totales.find((t) => t.gradoId === gradoId)

  return (
    <section className="tarjeta" aria-label="Cargas horarias">
      <h2>Bloques semanales por curso y grado</h2>
      <p className="muted">
        La semana tiene {cargas.bloquesPorSemana} bloques. El horario de cada sección solo se puede publicar cuando cada curso
        tiene exactamente los bloques de esta tabla. Si no cambias nada, rigen las horas lectivas del curso.
      </p>
      {guardado && (
        <p className="alerta alerta-ok" role="status">
          Se guardaron las cargas horarias.
        </p>
      )}
      {mensajeDe(guardar.error) && (
        <p className="alerta alerta-error" role="alert">
          {mensajeDe(guardar.error)}
        </p>
      )}
      <div className="tabla-scroll">
        <table aria-label="Cargas horarias">
          <thead>
            <tr>
              <th>Curso</th>
              <th>Horas lectivas</th>
              {cargas.grados.map((g) => (
                <th key={g.id}>{g.numeroNivel}°</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {cargas.cursos.map((fila) => (
              <tr key={fila.cursoId}>
                <td>
                  <strong>{fila.curso}</strong>
                </td>
                <td>{fila.horasLectivas}</td>
                {fila.grados.map((celda) => {
                  const grado = cargas.grados.find((g) => g.id === celda.gradoId)
                  return (
                    <td key={celda.gradoId}>
                      <input
                        className="entrada-numero"
                        inputMode="numeric"
                        aria-label={`${fila.curso} ${grado?.numeroNivel}°`}
                        value={editadas[clave(celda.gradoId, fila.cursoId)] ?? String(celda.bloques)}
                        onChange={(e) => setEditadas((previas) => ({ ...previas, [clave(celda.gradoId, fila.cursoId)]: e.target.value }))}
                      />
                    </td>
                  )
                })}
              </tr>
            ))}
            <tr>
              <td colSpan={2}>
                <strong>Total por semana</strong>
              </td>
              {cargas.grados.map((g) => {
                const total = totalDe(g.id)
                return (
                  <td key={g.id}>
                    <span className={total?.excedeLaSemana ? 'etiqueta etiqueta-alerta' : 'etiqueta'}>{total?.total ?? 0}</span>
                  </td>
                )
              })}
            </tr>
          </tbody>
        </table>
      </div>
      {cargas.totales.some((t) => t.excedeLaSemana) && (
        <p className="alerta alerta-error" role="alert">
          Algún grado pide más bloques de los que tiene la semana ({cargas.bloquesPorSemana}): su horario no se podrá completar.
        </p>
      )}
      {invalidas.length > 0 && (
        <p className="error-campo" role="alert">
          Cada celda debe ser un número entero de 1 a 20.
        </p>
      )}
      <div className="acciones">
        <button type="button" className="btn btn-primario" disabled={cambios.length === 0 || invalidas.length > 0 || guardar.isPending} onClick={enviar}>
          Guardar cargas
        </button>
      </div>
    </section>
  )
}
