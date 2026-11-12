# Compatibilidad entre navegadores (RNF-04)

Resultado de los smoke tests de Playwright (`frontend/e2e`) sobre la SPA y el backend reales con la semilla de demo. Atiende
la observación 3 de la evaluación del primer incremento y la ficha 117 de `verificacion_casos.md`.

## Resultado (11/11/2026)

| Navegador | Versión | Resultado | Observaciones |
|---|---|---|---|
| Chromium (Playwright) | 156.0.8078.4 | ✅ 14/14 | Equivale a Google Chrome 156. Corrida de 1,5 min |
| Firefox (Playwright) | 157.0 | ✅ 14/14 | Más lento (4,4 min), sin diferencias de comportamiento. La descarga del PDF y el menú plegable funcionan igual |
| Microsoft Edge (instalado, `channel: 'msedge'`) | 157.0.4341.46 | ✅ 14/14 | Corrida de 1,7 min |
| Safari / WebKit | — | **Fuera de alcance** | No se probó: no hay equipo Apple para validarlo. No se afirma compatibilidad con Safari |

Entorno: Windows 11 Home 10.0.26200, Node 24.12, `@playwright/test` 1.64.1, backend con los perfiles `local,demo` (reloj de la
demo en el lunes 15/03/2027) y PostgreSQL 18 en Docker. Cada navegador se ejecutó en serie, con un solo worker y con
`preparar-datos.sql` justo antes. La corrida de la tabla se hizo justo después de otra completa (y de dos solo con
Chromium) que ya habían registrado la asistencia y las notas: la preparación deja los datos listos para repetir.

**Hallazgos durante la puesta a punto** (ninguno es un defecto de la aplicación):
- En la primera corrida completa, Edge falló la prueba de credenciales incorrectas con «Tu cuenta se bloqueó temporalmente
  por 5 intentos fallidos». El backend también «bloquea» los correos inexistentes al quinto intento, para no revelar cuáles
  existen (`BloqueoFicticioTest`), y la prueba repetía el mismo correo en cada corrida. Ahora usa un correo distinto en cada
  una.
- Recargar la página en cada paso pide el refresh token, y la autenticación está limitada a 30 peticiones por minuto por IP.
  Por eso las pruebas navegan dentro de la SPA.

## Qué cubre cada navegador (14 pruebas)

| Prueba | Requisitos |
|---|---|
| Inicio de sesión correcto y «Salir» · credenciales incorrectas con el mensaje genérico | RF-52, UC-24 |
| Navegación por rol: administrador, docente y apoderado (saludo, opciones visibles y ocultas según permisos, ir y volver) | RF-50 |
| Marcación de la docente con la hora del servidor | UC-07 |
| Pase de lista de 3° A con Puntual, Tardanza y Falta («4 puntuales, 1 tardanza y 1 falta») | UC-08 |
| Registro de notas por competencia (las dos C3 pendientes → «24/24 celdas completas») y notas finales | UC-15, UC-17 |
| Descarga de la libreta en PDF por el apoderado (`libreta-2026-0404-2026.pdf`, firma `%PDF-`) | UC-19 |
| Sin desbordamiento horizontal en 375, 768 y 1280 px: 21 rutas del administrador, 11 de la docente y 4 del apoderado | RNF-02 |
| Menú plegable bajo 768 px (☰ de 44 px, foco, cierre al navegar y con Escape) y menú fijo en 1280 px | RNF-02 |

## Cómo repetirlo

Requisitos: Docker con `gesedu-postgres`, el backend con `local,demo` en :8080 y los navegadores de Playwright instalados en
la carpeta de `PLAYWRIGHT_BROWSERS_PATH` (aquí, `D:\ms-playwright`, para no descargar nada en `C:`). Edge es el del sistema.

```powershell
docker compose up -d postgres mailpit
cd backend; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local,demo"      # en otra terminal
cd frontend
$env:PLAYWRIGHT_BROWSERS_PATH = "D:\ms-playwright"
npx playwright install chromium firefox     # solo la primera vez
npm run e2e                                 # imprime la tabla de arriba
```

`preparar-datos.sql` publica el horario 2027 si falta y borra la marcación de Rosa, el pase de lista de 3° A del 15/03/2027 y
las dos celdas C3 de Matemática 3° A. Solo corre en la base local `gesedu` con la semilla de demo (si no, aborta) y no toca
el esquema `auditoria`. **No entra en CI**: necesita Docker, el backend con datos de demo y Edge instalado. Detalle en
`frontend/e2e/README.md`.
