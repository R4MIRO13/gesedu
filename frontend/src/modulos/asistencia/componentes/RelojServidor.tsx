import { useEffect, useState } from 'react'

const formatoHora = new Intl.DateTimeFormat('es-PE', {
  hour: 'numeric',
  minute: '2-digit',
  second: '2-digit',
  hour12: true,
  timeZone: 'America/Lima',
})

/**
 * La hora del servidor, corriendo segundo a segundo. Se muestra como la hora que mandó el servidor más el tiempo que
 * pasó desde que llegó (`performance.now()` es monotónico y no depende de que el reloj del dispositivo esté bien).
 * Es solo para mostrar; el registro de la asistencia siempre usa la hora del servidor. Quien lo use debe pasarle
 * `key={horaServidor}` para que cada hora nueva del servidor reinicie la cuenta.
 */
export function RelojServidor({ horaServidor }: { horaServidor: string }) {
  const base = Date.parse(horaServidor)
  const [transcurrido, setTranscurrido] = useState(0)

  useEffect(() => {
    const llegada = performance.now()
    const temporizador = setInterval(() => setTranscurrido(performance.now() - llegada), 1000)
    return () => clearInterval(temporizador)
  }, [])

  return (
    <p className="reloj-servidor">
      <strong aria-label="Hora del servidor">{formatoHora.format(base + transcurrido)}</strong>
      <span className="muted"> · hora del servidor</span>
    </p>
  )
}
