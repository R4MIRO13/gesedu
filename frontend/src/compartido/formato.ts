/** «07:45:00» → «07:45» (el backend manda las horas con segundos). */
export function hhmm(hora: string): string {
  return hora.slice(0, 5)
}
