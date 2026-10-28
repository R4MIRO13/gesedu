import type { Borrador, Opciones } from '../../modulos/horarios/api'
import { sesionDe } from './handlers'

/** Administrador que gestiona horarios (UC-21). */
export const SESION_GESTOR = sesionDe(
  'ADMINISTRADOR',
  ['HORARIO_CONSULTAR', 'HORARIO_CONFIGURAR', 'HORARIO_GESTIONAR', 'AUDITORIA_CONSULTAR', 'AUDITORIA_EXPORTAR'],
  { cargo: 'Secretaría académica' },
)

export const BLOQUES = [
  { id: 'b-lun', diaSemana: 'LUNES', horaInicio: '07:45:00', horaFin: '08:30:00' },
  { id: 'b-mar1', diaSemana: 'MARTES', horaInicio: '09:15:00', horaFin: '10:00:00' },
  { id: 'b-mar2', diaSemana: 'MARTES', horaInicio: '10:15:00', horaFin: '11:00:00' },
] as const satisfies Borrador['bloques']

export function borradorEjemplo(sobrescribir: Partial<Borrador> = {}): Borrador {
  return {
    id: 'borrador-1',
    anio: 2027,
    version: 3,
    perspectiva: 'SECCION',
    seccionId: 's-3a',
    docenteId: null,
    bloques: [...BLOQUES],
    secciones: [
      { id: 's-3a', nombre: '3° A' },
      { id: 's-4b', nombre: '4° B' },
    ],
    docentes: [
      { id: 'd-rosa', nombre: 'Rosa Quispe Mendoza' },
      { id: 'd-carlos', nombre: 'Carlos Ríos Vega' },
      { id: 'd-fernando', nombre: 'Fernando Medina Soto' },
    ],
    asignaciones: [
      {
        id: 'a-1',
        bloqueId: 'b-lun',
        seccionId: 's-3a',
        seccion: '3° A',
        cursoId: 'c-mat',
        curso: 'Matemática',
        docenteId: 'd-fernando',
        docente: 'Fernando Medina Soto',
      },
    ],
    carga: {
      cursos: [
        { cursoId: 'c-mat', curso: 'Matemática', horasAsignadas: 1, horasRequeridas: 5 },
        { cursoId: 'c-ing', curso: 'Inglés', horasAsignadas: 0, horasRequeridas: 3 },
      ],
      totalAsignadas: 1,
      totalRequeridas: 8,
    },
    ...sobrescribir,
  }
}

export const OPCIONES_SIN_CURSO: Opciones = {
  cursos: [
    { cursoId: 'c-mat', curso: 'Matemática', horasAsignadas: 1, horasRequeridas: 5 },
    { cursoId: 'c-ing', curso: 'Inglés', horasAsignadas: 0, horasRequeridas: 3 },
  ],
  docentes: [],
}

/** Los docentes de Matemática para el martes de 9:15, como los ofrece el backend (RF-47). */
export const OPCIONES_MATEMATICA: Opciones = {
  cursos: OPCIONES_SIN_CURSO.cursos,
  docentes: [
    { docenteId: 'd-fernando', nombre: 'Fernando Medina Soto', disponible: true, ocupadoEn: null, detalleOcupado: null },
    { docenteId: 'd-carlos', nombre: 'Carlos Ríos Vega', disponible: false, ocupadoEn: null, detalleOcupado: null },
    {
      docenteId: 'd-rosa',
      nombre: 'Rosa Quispe Mendoza',
      disponible: true,
      ocupadoEn: '4° B',
      detalleOcupado: 'tiene clase en 4° B el martes de 9:15 a 10:00',
    },
  ],
}
