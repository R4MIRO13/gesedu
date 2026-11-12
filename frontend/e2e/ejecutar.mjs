// Corre los smoke tests de Playwright navegador por navegador, en serie: cada uno con su proyecto preparar-<navegador>
// (preparar-datos.sql) justo antes, de modo que los tres parten de los mismos datos y un fallo en uno no impide probar
// los demás. Al final imprime el resumen para docs/pruebas/compatibilidad.md.
//
//   npm run e2e                 # chromium, firefox y msedge
//   npm run e2e -- firefox      # solo los navegadores indicados
//
// Requisitos: Docker con gesedu-postgres, el backend con los perfiles local,demo en :8080 y los navegadores instalados
// (npx playwright install chromium firefox; Edge usa el instalado en el sistema). Ver e2e/README.md.
import { spawnSync } from 'node:child_process'
import { existsSync, mkdirSync, readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const AQUI = dirname(fileURLToPath(import.meta.url))
const RESULTADOS = join(AQUI, 'resultados')
const TODOS = ['chromium', 'firefox', 'msedge']
const pedidos = process.argv.slice(2)
const navegadores = pedidos.length ? TODOS.filter((n) => pedidos.includes(n)) : TODOS

if (!process.env.PLAYWRIGHT_BROWSERS_PATH) {
    console.warn(
        'Aviso: PLAYWRIGHT_BROWSERS_PATH no está definida; Playwright buscará los navegadores en su carpeta por omisión.',
    )
}
mkdirSync(RESULTADOS, { recursive: true })

const resumen = []
for (const navegador of navegadores) {
    console.log(`\n=== ${navegador} ===`)
    const json = join(RESULTADOS, `${navegador}.json`)
    const r = spawnSync('npx', ['playwright', 'test', `--project=${navegador}`], {
        cwd: join(AQUI, '..'),
        stdio: 'inherit',
        shell: process.platform === 'win32',
        env: { ...process.env, E2E_JSON: json },
    })
    resumen.push({ navegador, ...leer(json, navegador), codigo: r.status })
}

console.log('\n| Navegador | Versión | Resultado | Fallos |')
console.log('|---|---|---|---|')
for (const f of resumen) {
    console.log(`| ${f.navegador} | ${f.version} | ${f.pasan}/${f.total} | ${f.fallos.join('; ') || '—'} |`)
}
process.exit(resumen.every((f) => f.codigo === 0) ? 0 : 1)

function leer(json, navegador) {
    const archivoVersion = join(RESULTADOS, `${navegador}-version.txt`)
    const version = existsSync(archivoVersion) ? readFileSync(archivoVersion, 'utf8').trim() : '¿?'
    if (!existsSync(json)) return { version, pasan: 0, total: 0, fallos: ['sin resultados'] }
    const pruebas = []
    const recorrer = (suite) => {
        for (const spec of suite.specs ?? []) {
            for (const t of spec.tests ?? []) {
                if (t.projectName.startsWith('preparar-')) {
                    if (t.status !== 'expected') pruebas.push({ titulo: 'preparación de datos', ok: false })
                    continue
                }
                pruebas.push({ titulo: `${spec.file}: ${spec.title}`, ok: t.status === 'expected' })
            }
        }
        for (const hija of suite.suites ?? []) recorrer(hija)
    }
    for (const suite of JSON.parse(readFileSync(json, 'utf8')).suites ?? []) recorrer(suite)
    return {
        version,
        pasan: pruebas.filter((p) => p.ok).length,
        total: pruebas.length,
        fallos: pruebas.filter((p) => !p.ok).map((p) => p.titulo),
    }
}
