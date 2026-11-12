# Smoke tests de compatibilidad (Playwright)

Pruebas de extremo a extremo en **Chromium, Firefox y Microsoft Edge** (RNF-04) sobre la SPA y el backend reales con la
semilla de demo. El resultado de la última corrida está en [`docs/pruebas/compatibilidad.md`](../../docs/pruebas/compatibilidad.md).
**No entran en CI**: necesitan Docker, el backend con datos de demo y Edge instalado, así que se corren a mano.

| Archivo | Qué prueba |
|---|---|
| `login.spec.ts` | Inicio de sesión, «Salir» y credenciales incorrectas (RF-52, UC-24) |
| `navegacion.spec.ts` | Inicio y menú por rol: administrador, docente y apoderado (RF-50) |
| `asistencia.spec.ts` | Marcación de la docente (UC-07) y pase de lista de 3° A con Puntual, Tardanza y Falta (UC-08) |
| `notas.spec.ts` | Registro de notas por competencia de Matemática 3° A y notas finales (UC-15, UC-17) |
| `libreta.spec.ts` | Descarga de la libreta en PDF por el apoderado (UC-19) |
| `responsive.spec.ts` | Sin desbordamiento horizontal en 375, 768 y 1280 px por rol, y el menú plegable ☰ (RNF-02) |
| `preparar.setup.ts` + `preparar-datos.sql` | Preparación de datos antes de cada navegador (ver abajo) |

## Requisitos

1. Docker Desktop con la base y el correo: `docker compose up -d postgres mailpit` (contenedor `gesedu-postgres`).
2. El backend con los perfiles **`local,demo`** en http://localhost:8080 (la semilla `db/demo` y el reloj de la demo, lunes 15/03/2027):
   `cd backend; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local,demo"`.
3. La SPA en http://localhost:5173 (`npm run dev`). Si no está arriba, Playwright la arranca.
4. Los navegadores de Playwright. **Defina `PLAYWRIGHT_BROWSERS_PATH`** para elegir dónde se descargan (por omisión van al
   perfil del usuario en `C:`); en el equipo de referencia se usó `D:\ms-playwright`:
   ```powershell
   $env:PLAYWRIGHT_BROWSERS_PATH = "D:\ms-playwright"
   npx playwright install chromium firefox
   ```
   Edge no se descarga: el proyecto `msedge` usa el Microsoft Edge instalado en el sistema (`channel: 'msedge'`).

## Cómo correr

```powershell
cd frontend
$env:PLAYWRIGHT_BROWSERS_PATH = "D:\ms-playwright"   # la misma carpeta de la instalación
npm run e2e                  # chromium, firefox y msedge, uno después de otro
npm run e2e -- firefox       # solo los navegadores indicados
```

`npm run e2e` (`e2e/ejecutar.mjs`) corre un navegador a la vez con un solo worker y, **antes de cada uno**, su proyecto
`preparar-<navegador>`. Al final imprime la tabla navegador · versión · resultado para `docs/pruebas/compatibilidad.md`.
Los fallos dejan captura, traza y contexto en `e2e/resultados/artefactos/` (ignorada por git):
`npx playwright show-trace e2e/resultados/artefactos/<prueba>/trace.zip`.

## Preparación de datos

Registrar asistencia y notas solo se puede una vez por día, y el reloj de la demo es siempre el lunes 15/03/2027. Por eso
`preparar-datos.sql` deja la base como la semilla antes de cada navegador:

- publica el horario 2027 si aún no hay uno vigente (como el paso 12 del guion o `docs/demo/atajos/publicar-horario.sql`);
- borra la marcación de Rosa y el pase de lista de 3° A del 15/03/2027;
- borra las dos celdas C3 de Matemática 3° A del bimestre I (Mendoza y Paredes), que la semilla deja sin calificar.

**Solo toca la base local de desarrollo**: el script verifica que el contenedor sea `gesedu-postgres`, que la base se llame
`gesedu` y que tenga la semilla de demo; si no, aborta. **No modifica el esquema `auditoria`** (es de solo inserción): las
entradas que dejan las corridas se acumulan. Publicar el horario es definitivo; para volver a la semilla original hay que
reiniciar la base (`.\scripts\reiniciar-bd.ps1`, que borra los datos locales).

## Notas

- Las pruebas navegan dentro de la SPA (`irA`) en lugar de recargar: cada recarga pide el refresh token y el backend
  limita la autenticación a 30 peticiones por minuto por IP.
- El backend «bloquea» también los correos inexistentes al quinto intento fallido (para no revelar cuáles existen); por eso
  la prueba de credenciales incorrectas usa un correo distinto en cada corrida.
- Safari (WebKit) queda fuera de alcance: no hay equipo Apple para validarlo.
