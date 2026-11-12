import { spawnSync } from 'node:child_process'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { expect, test as preparar } from '@playwright/test'

const AQUI = dirname(fileURLToPath(import.meta.url))
const CONTENEDOR = 'gesedu-postgres'

/** Ejecuta un comando y devuelve su salida; falla con su stderr si termina mal. */
function ejecutar(comando: string, argumentos: string[], entrada?: string) {
  const r = spawnSync(comando, argumentos, { input: entrada, encoding: 'utf8' })
  if (r.error) throw r.error
  if (r.status !== 0)
    throw new Error(`${comando} ${argumentos.join(' ')} terminó con ${r.status}:\n${r.stderr}`)
  return r.stdout
}

preparar(
  'el backend local responde y la base de demo queda preparada',
  async ({ browser, request }, testInfo) => {
    // Solo el contenedor de desarrollo de docker-compose: nunca otra base
    const contenedores = ejecutar('docker', [
      'ps',
      '--filter',
      `name=^${CONTENEDOR}$`,
      '--format',
      '{{.Names}}',
    ]).trim()
    expect(
      contenedores,
      `No está corriendo el contenedor ${CONTENEDOR} (docker compose up -d postgres mailpit)`,
    ).toBe(CONTENEDOR)

    const salud = await request.get('http://localhost:8080/actuator/health').catch(() => null)
    expect(salud?.ok(), 'El backend no responde en :8080: arráncalo con los perfiles local,demo').toBe(true)

    const sql = readFileSync(join(AQUI, 'preparar-datos.sql'), 'utf8')
    const salida = ejecutar(
      'docker',
      [
        'exec',
        '-i',
        '-e',
        'PGPASSWORD=gesedu_app',
        CONTENEDOR,
        'psql',
        '-h',
        '127.0.0.1',
        '-U',
        'gesedu_app',
        '-d',
        'gesedu',
        '-v',
        'ON_ERROR_STOP=1',
        '-q',
      ],
      sql,
    )
    expect(salida).toContain('preparado')

    // Navegador y versión para el resumen de docs/pruebas/compatibilidad.md
    const proyecto = testInfo.project.name.replace('preparar-', '')
    const resultados = join(AQUI, 'resultados')
    mkdirSync(resultados, { recursive: true })
    writeFileSync(
      join(resultados, `${proyecto}-version.txt`),
      `${browser.browserType().name()} ${browser.version()}\n`,
    )
  },
)
