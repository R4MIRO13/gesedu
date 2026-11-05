/** «07:45:00» → «07:45» (el backend manda las horas con segundos). */
export function hhmm(hora: string): string {
  return hora.slice(0, 5)
}

/** «2027-03-31» → «31/03/2027» (fechas sin hora, como las manda la API). */
export function fechaCorta(iso: string): string {
  const [anio, mes, dia] = iso.slice(0, 10).split('-')
  return `${dia}/${mes}/${anio}`
}

/** «1400.00» → «S/ 1,400.00». Solo da formato al texto decimal de la API: la SPA no calcula montos. */
export function soles(monto: string): string {
  const [entero = '0', decimales] = monto.split('.')
  return `S/ ${entero.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}${decimales === undefined ? '' : `.${decimales}`}`
}

/** Fecha de hoy en la zona institucional (America/Lima) como «AAAA-MM-DD». */
export function hoyLima(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(new Date())
}

const formatoHora12 = new Intl.DateTimeFormat('es-PE', { hour: 'numeric', minute: '2-digit', hour12: true, timeZone: 'America/Lima' })

/** «07:45» o «07:45:00» → «7:45 a. m.» (la hora de un horario, que no lleva zona). */
export function hora12(hora: string): string {
  const [h = '0', m = '0'] = hora.split(':')
  return formatoHora12.format(new Date(Date.UTC(2000, 0, 1, Number(h) + 5, Number(m))))
}

/** «2027-03-15T07:41:12-05:00» → «7:41 a. m.» (el instante de una marcación, en la zona institucional). */
export function horaDeInstante(iso: string): string {
  return formatoHora12.format(new Date(iso))
}

const formatoDiaSemana = new Intl.DateTimeFormat('es-PE', { weekday: 'long', timeZone: 'UTC' })

/** «2026-09-29» → «Martes 29/09/2026». */
export function diaLargo(fecha: string): string {
  const nombre = formatoDiaSemana.format(new Date(`${fecha}T12:00:00Z`))
  return `${nombre.charAt(0).toUpperCase()}${nombre.slice(1)} ${fechaCorta(fecha)}`
}

/** «2026-09-29» → «Mar 29/09». */
export function diaCorto(fecha: string): string {
  const nombre = formatoDiaSemana.format(new Date(`${fecha}T12:00:00Z`)).slice(0, 3)
  const [, mes, dia] = fecha.split('-')
  return `${nombre.charAt(0).toUpperCase()}${nombre.slice(1)} ${dia}/${mes}`
}
