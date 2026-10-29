import { zodResolver } from '@hookform/resolvers/zod'
import { createFileRoute } from '@tanstack/react-router'
import { useEffect, useState } from 'react'
import { useFieldArray, useForm, useWatch } from 'react-hook-form'
import { ApiError } from '../../../compartido/api/errores'
import { exigirPermiso } from '../../../compartido/auth/guardias'
import { ANIO_TRABAJO } from '../../../compartido/menu'
import {
  CONCEPTOS,
  useConfiguracionCobros,
  useGuardarCobros,
  type ConfiguracionCobros,
  type LineaCobro,
} from '../../../modulos/pagos/api'
import { esquemaCobros, type DatosCobros } from '../../../modulos/pagos/esquemas'

export const Route = createFileRoute('/intranet/pagos/configuracion')({
  beforeLoad: () => exigirPermiso('COBRO_CONFIGURAR'),
  component: ConfiguracionCobrosPagina,
})

/** Cronograma de partida cuando el año aún no tiene uno: matrícula en febrero y pensiones de marzo a diciembre. */
function cronogramaInicial(anio: number): LineaCobro[] {
  const pensiones = Array.from({ length: 10 }, (_, i) => {
    const mes = String(i + 3).padStart(2, '0')
    const ultimoDia = new Date(anio, i + 3, 0).getDate()
    return { concepto: 'PENSION' as const, periodo: `${anio}-${mes}`, monto: '', fechaVencimiento: `${anio}-${mes}-${ultimoDia}` }
  })
  return [{ concepto: 'MATRICULA', periodo: String(anio), monto: '', fechaVencimiento: `${anio}-02-28` }, ...pensiones]
}

function valoresDe(c: ConfiguracionCobros): DatosCobros {
  return {
    porcentajePenalidad: c.porcentajePenalidad === null ? '' : String(c.porcentajePenalidad),
    lineas: c.lineas.length > 0 ? c.lineas : cronogramaInicial(c.anio),
  }
}

function ConfiguracionCobrosPagina() {
  const cobros = useConfiguracionCobros(ANIO_TRABAJO)
  if (cobros.isPending) return <p className="muted">Cargando…</p>
  if (cobros.isError) {
    return (
      <p className="alerta alerta-error" role="alert">
        {cobros.error.message}
      </p>
    )
  }
  return <Formulario configuracion={cobros.data} />
}

function Formulario({ configuracion }: { configuracion: ConfiguracionCobros }) {
  const guardar = useGuardarCobros(configuracion.anio)
  const [guardado, setGuardado] = useState<number | null>(null)
  const form = useForm<DatosCobros>({ resolver: zodResolver(esquemaCobros), defaultValues: valoresDe(configuracion) })
  const lineas = useFieldArray({ control: form.control, name: 'lineas' })
  const errores = form.formState.errors
  const tieneCuotaIngreso = useWatch({ control: form.control, name: 'lineas' }).some((l) => l.concepto === 'CUOTA_INGRESO')

  // Tras guardar, el formulario muestra lo que quedó registrado
  useEffect(() => {
    form.reset(valoresDe(configuracion))
  }, [configuracion, form])

  function enviar(datos: DatosCobros) {
    setGuardado(null)
    guardar.mutate(
      { porcentajePenalidad: Number(datos.porcentajePenalidad), lineas: datos.lineas.map((l) => ({ ...l, monto: l.monto.trim() })) },
      { onSuccess: (guardada) => setGuardado(guardada.obligacionesActualizadas) },
    )
  }

  return (
    <>
      <div className="encabezado">
        <div>
          <h1>Configuración de cobros {configuracion.anio}</h1>
          <p className="muted">
            Costos y vencimientos del año. Las matrículas que se confirmen desde ahora generan sus compromisos con este
            cronograma.
          </p>
        </div>
        <span className={configuracion.configurado ? 'etiqueta etiqueta-si' : 'etiqueta etiqueta-no'}>
          {configuracion.configurado ? 'Configurado' : 'Sin configurar'}
        </span>
      </div>

      {guardado !== null && (
        <p className="alerta alerta-ok" role="status">
          Se guardó la configuración de cobros.{' '}
          {guardado === 0
            ? 'Ningún compromiso pendiente necesitó actualizarse.'
            : `Se actualizaron ${guardado} ${guardado === 1 ? 'compromiso pendiente' : 'compromisos pendientes'} con los montos y vencimientos nuevos; los pagados y los vencidos no cambian.`}
        </p>
      )}
      {guardar.error && (
        <p className="alerta alerta-error" role="alert">
          {guardar.error instanceof ApiError ? guardar.error.message : 'No se pudo guardar. Inténtalo de nuevo.'}
        </p>
      )}

      <form className="tarjeta formulario" onSubmit={form.handleSubmit(enviar)} noValidate aria-label="Configuración de cobros">
        <label className="campo">
          Penalidad por pago vencido (%)
          <input inputMode="decimal" {...form.register('porcentajePenalidad')} aria-invalid={!!errores.porcentajePenalidad} />
          {errores.porcentajePenalidad && <span className="error-campo">{errores.porcentajePenalidad.message}</span>}
        </label>

        <div className="tabla-scroll">
          <table aria-label="Cronograma de cobros">
            <thead>
              <tr>
                <th>Concepto</th>
                <th>Periodo</th>
                <th>Monto (S/)</th>
                <th>Vence</th>
                <th>
                  <span className="solo-lectores">Quitar</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {lineas.fields.map((campo, i) => (
                <tr key={campo.id}>
                  <td>{CONCEPTOS[campo.concepto]}</td>
                  <td className="nowrap">{campo.periodo}</td>
                  <td>
                    <input
                      inputMode="decimal"
                      aria-label={`Monto de ${CONCEPTOS[campo.concepto]} ${campo.periodo}`}
                      {...form.register(`lineas.${i}.monto`)}
                      aria-invalid={!!errores.lineas?.[i]?.monto}
                    />
                    {errores.lineas?.[i]?.monto && <span className="error-campo">{errores.lineas[i].monto.message}</span>}
                  </td>
                  <td>
                    <input
                      type="date"
                      aria-label={`Vencimiento de ${CONCEPTOS[campo.concepto]} ${campo.periodo}`}
                      {...form.register(`lineas.${i}.fechaVencimiento`)}
                    />
                    {errores.lineas?.[i]?.fechaVencimiento && (
                      <span className="error-campo">{errores.lineas[i].fechaVencimiento.message}</span>
                    )}
                  </td>
                  <td>
                    {campo.concepto === 'CUOTA_INGRESO' && (
                      <button type="button" className="btn" onClick={() => lineas.remove(i)}>
                        Quitar
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="acciones">
          {!tieneCuotaIngreso && (
            <button
              type="button"
              className="btn"
              onClick={() =>
                lineas.append({ concepto: 'CUOTA_INGRESO', periodo: String(configuracion.anio), monto: '', fechaVencimiento: `${configuracion.anio}-02-28` })
              }
            >
              Agregar cuota de ingreso
            </button>
          )}
          <button className="btn btn-primario" type="submit" disabled={guardar.isPending}>
            {guardar.isPending ? 'Guardando…' : 'Guardar configuración'}
          </button>
        </div>
      </form>
    </>
  )
}
