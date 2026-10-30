import { zodResolver } from '@hookform/resolvers/zod'
import { createFileRoute, Link } from '@tanstack/react-router'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { ApiError } from '../../../../compartido/api/errores'
import { exigirPermiso } from '../../../../compartido/auth/guardias'
import { fechaCorta } from '../../../../compartido/formato'
import { useCrearAnio, useValidacionAnio, type AnioDetalle, type Bimestre } from '../../../../modulos/academico/api'
import { LineaTiempoAnio } from '../../../../modulos/academico/componentes/LineaTiempoAnio'
import { esquemaAnioNuevo, type DatosAnioNuevoForm } from '../../../../modulos/academico/esquemas'

export const Route = createFileRoute('/intranet/academico/anio-escolar/nuevo')({
  beforeLoad: () => exigirPermiso('ACADEMICO_CONFIGURAR'),
  component: NuevoAnioEscolar,
})

const ROMANOS = ['I', 'II', 'III', 'IV']

function bimestresVacios(): Bimestre[] {
  return [1, 2, 3, 4].map((numero) => ({ numero, fechaInicio: '', fechaFin: '' }))
}

/** UC-13, Nuevo año escolar: paso 1 (fechas del año) y paso 2 (cuatro bimestres sin cruces). */
function NuevoAnioEscolar() {
  const [paso, setPaso] = useState<1 | 2>(1)
  const [creado, setCreado] = useState<AnioDetalle | null>(null)
  const crear = useCrearAnio()
  const form = useForm<DatosAnioNuevoForm>({
    resolver: zodResolver(esquemaAnioNuevo),
    defaultValues: { fechaInicio: '', fechaFin: '', bimestres: bimestresVacios() },
  })
  const valores = useWatch({ control: form.control }) as DatosAnioNuevoForm
  const errores = form.formState.errors
  const bimestres = (valores.bimestres ?? []).filter((b): b is Bimestre => !!b)
  const validacion = useValidacionAnio(valores.fechaInicio ?? '', valores.fechaFin ?? '', paso === 2 ? bimestres : null)
  const problemas = validacion.data?.problemas ?? []
  const problemasAnio = problemas.filter((p) => p.tipo === 'ANIO_FUERA_DE_DURACION')
  const problemasBimestres = problemas.filter((p) => p.tipo !== 'ANIO_FUERA_DE_DURACION')
  const duracion = validacion.data?.duracion

  function siguiente() {
    void form.trigger(['fechaInicio', 'fechaFin']).then((ok) => {
      if (!ok || problemasAnio.length > 0 || !validacion.data) return
      // Ayuda: el primer bimestre empieza con el año y el cuarto termina con él
      if (!form.getValues('bimestres.0.fechaInicio')) form.setValue('bimestres.0.fechaInicio', form.getValues('fechaInicio'))
      if (!form.getValues('bimestres.3.fechaFin')) form.setValue('bimestres.3.fechaFin', form.getValues('fechaFin'))
      setPaso(2)
    })
  }

  function enviar(datos: DatosAnioNuevoForm) {
    crear.mutate(datos, { onSuccess: setCreado })
  }

  if (creado) {
    return (
      <>
        <h1>Año escolar {creado.anio}</h1>
        <p className="alerta alerta-ok" role="status">
          El año escolar {creado.anio} quedó registrado ({fechaCorta(creado.fechaInicio)} – {fechaCorta(creado.fechaFin)}) con sus
          cuatro bimestres y los grados de 1° a 5°.
        </p>
        <p>
          <Link className="btn btn-primario" to="/intranet/academico/anios">
            Ver años escolares
          </Link>
        </p>
      </>
    )
  }

  const errorEnvio = crear.error instanceof ApiError ? crear.error : null
  const erroresEnvio = (errorEnvio?.propiedades.problemas as { mensaje: string }[] | undefined) ?? []

  return (
    <>
      <div className="encabezado">
        <div>
          <h1>Nuevo año escolar</h1>
          <p className="muted">Inicio › Configuración académica › Nuevo año escolar</p>
        </div>
      </div>
      <ol className="pasos" aria-label="Pasos">
        <li className={paso === 1 ? 'paso paso-activo' : 'paso'} aria-current={paso === 1 ? 'step' : undefined}>
          Paso 1 · Fechas del año escolar
        </li>
        <li className={paso === 2 ? 'paso paso-activo' : 'paso'} aria-current={paso === 2 ? 'step' : undefined}>
          Paso 2 · Bimestres
        </li>
        {problemas.length > 0 && (
          <li className="etiqueta etiqueta-alerta">
            {problemas.length} {problemas.length === 1 ? 'error' : 'errores'}
          </li>
        )}
      </ol>

      <form className="tarjeta formulario" onSubmit={form.handleSubmit(enviar)} noValidate aria-label="Nuevo año escolar">
        <h2>Paso 1 · Fechas del año escolar</h2>
        <div className="filtros">
          <label className="campo">
            Fecha de inicio
            <input type="date" {...form.register('fechaInicio')} disabled={paso === 2} aria-invalid={!!errores.fechaInicio} />
            {errores.fechaInicio && <span className="error-campo">{errores.fechaInicio.message}</span>}
          </label>
          <label className="campo">
            Fecha de fin
            <input type="date" {...form.register('fechaFin')} disabled={paso === 2} aria-invalid={!!errores.fechaFin} />
            {errores.fechaFin && <span className="error-campo">{errores.fechaFin.message}</span>}
          </label>
        </div>
        {duracion && duracion.meses + duracion.dias > 0 && (
          <p className="muted" role="status">
            Duración: {duracion.meses} {duracion.meses === 1 ? 'mes' : 'meses'} y {duracion.dias} {duracion.dias === 1 ? 'día' : 'días'}
          </p>
        )}
        {problemasAnio.map((p) => (
          <p key={p.mensaje} className="alerta alerta-error" role="alert">
            {p.mensaje}
          </p>
        ))}

        {paso === 1 ? (
          <div className="acciones">
            <button type="button" className="btn btn-primario" onClick={siguiente}>
              Siguiente
            </button>
          </div>
        ) : (
          <>
            <h2>Paso 2 · Bimestres</h2>
            {bimestres.map((b, i) => (
              <div key={b.numero} className="fila-bimestre">
                <strong>Bimestre {ROMANOS[i]}</strong>
                <label className="campo">
                  Inicio
                  <input
                    type="date"
                    aria-label={`Inicio del Bimestre ${ROMANOS[i]}`}
                    {...form.register(`bimestres.${i}.fechaInicio`)}
                    aria-invalid={!!errores.bimestres?.[i]?.fechaInicio}
                  />
                  {errores.bimestres?.[i]?.fechaInicio && <span className="error-campo">{errores.bimestres[i].fechaInicio.message}</span>}
                </label>
                <label className="campo">
                  Fin
                  <input
                    type="date"
                    aria-label={`Fin del Bimestre ${ROMANOS[i]}`}
                    {...form.register(`bimestres.${i}.fechaFin`)}
                    aria-invalid={!!errores.bimestres?.[i]?.fechaFin}
                  />
                  {errores.bimestres?.[i]?.fechaFin && <span className="error-campo">{errores.bimestres[i].fechaFin.message}</span>}
                </label>
                {problemasBimestres.some((p) => p.bimestre === b.numero) && <span className="etiqueta etiqueta-alerta">Se superpone</span>}
              </div>
            ))}
            {problemasBimestres.map((p) => (
              <p key={p.mensaje} className="alerta alerta-error" role="alert">
                {p.mensaje}
              </p>
            ))}
            {errorEnvio && problemasBimestres.length === 0 && (
              <p className="alerta alerta-error" role="alert">
                {errorEnvio.message}
                {erroresEnvio.length > 1 && ` (${erroresEnvio.length} problemas)`}
              </p>
            )}
            <LineaTiempoAnio
              fechaInicio={valores.fechaInicio ?? ''}
              fechaFin={valores.fechaFin ?? ''}
              bimestres={bimestres}
              problemas={problemasBimestres}
            />
            <div className="acciones">
              <button type="button" className="btn" onClick={() => setPaso(1)}>
                Anterior
              </button>
              <button type="submit" className="btn btn-primario" disabled={crear.isPending || problemas.length > 0}>
                Crear año escolar
              </button>
            </div>
          </>
        )}
      </form>
    </>
  )
}
