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
