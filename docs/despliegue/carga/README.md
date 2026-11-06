# Prueba de carga de UC-01 con k6 (RNF-12, RNF-03)

**Resultado (medición local del 06/11/2026, no de QA): NO cumple el umbral p95 < 2 s.** 0 % de errores y todas las solicitudes quedaron registradas (201), pero la latencia se dispara por una serialización en el alta de alumnos (ver «Causa»). Queda abierto como hallazgo de la fase 6.

## Qué mide
`uc01.js` simula apoderados que recorren el asistente de «Solicitar matrícula» con un alumno nuevo: `GET /solicitudes-matricula/opciones` → `POST /solicitudes-matricula/archivos` → `PUT` de la URL prefirmada (disco local) → `POST /solicitudes-matricula`. Cada apoderado usa su propia cuenta (`carga001@carga.gesedu.pe` … `carga200@…`, contraseña de la demo). Umbrales: p95 < 2 s en total y por paso, errores < 1 %.

Dos escenarios (`ESCENARIO`):
- **pico** (RNF-12: «picos de al menos 200 apoderados concurrentes»): los 200 envían la solicitud a la vez, una vez.
- **sostenida** (por omisión): los 200 repiten el asistente sin parar durante 2 minutos (estrés).

## Condiciones de la medición
Máquina de desarrollo (Windows 11, 8 hilos lógicos; Docker Desktop con 4 GB) con todo en el mismo equipo: backend `local,demo` con `-Xmx1g`, PostgreSQL 18 y Mailpit en Docker y k6 (`grafana/k6`) en Docker. **IntelliJ estaba abierto.** Pool de conexiones por omisión (Hikari: 10). Límite de inicios de sesión por IP subido con `GESEDU_LIMITE_AUTH_POR_MINUTO=100000` (los 200 apoderados salen de la misma IP). Base reiniciada y con las 200 cuentas de `preparar-apoderados.sql`. Los números de un equipo de desarrollo no son los de QA o AWS.

| Escenario | Iteraciones | Errores | p95 total | p95 opciones | p95 url de subida | p95 subida | p95 solicitud |
|---|---|---|---|---|---|---|---|
| pico (200 × 1) | 200 | 0 % | **6,83 s** | 5,94 s | 6,62 s | 42 ms | 7,26 s |
| sostenida (2 min) | 1 845 | 0 % | **6,29 s** | 6,31 s | 5,82 s | 19 ms | 6,86 s |

Salidas completas: `resultado-k6-pico.txt` y `resultado-k6-sostenida.txt`.

## Causa (verificada)
- Un volcado de hilos durante la carga (`jcmd <pid> Thread.dump_to_file -format=json`) mostró ~180 de ~200 hilos esperando una conexión de Hikari; con el pool en 30 siguieron 152 esperando y la latencia no mejoró (p95 6,2 s), así que **el tamaño del pool no es la causa**.
- `pg_stat_activity` durante la carga: **28 de las 30 conexiones activas esperaban `pg_advisory_xact_lock(7311001)`**. Es el candado de `AlumnoRepository.tomarCandadoDeCodigos()`, que `RegistroFamilias.obtenerORegistrar` toma al crear el alumno (para que dos solicitudes del mismo documento no creen dos cuentas y para calcular el código `AAAA-NNNN` con `MAX()+1`). Como el método corre dentro de la transacción de `SolicitudApoderadoService.registrar`, **el candado se mantiene hasta el commit de toda la solicitud** (documentos, auditoría): las altas de alumnos nuevos se atienden de una en una (~12 por segundo) y cada una retiene su conexión mientras espera, de modo que los pasos que solo leen (opciones, URL de subida) hacen cola detrás del pool.
- Los pasos sin base de datos (la subida del archivo) responden en menos de 50 ms.

## Opciones para cerrarlo (no aplicadas: tocan la concurrencia del alta de alumnos, RNF-09)
1. Generar el número del código con una **secuencia de PostgreSQL** (migración nueva) en lugar de `MAX()+1` bajo candado, y apoyarse en el índice único de `documento_identidad` para el duplicado (capturar la violación y devolver `DOCUMENTO_DUPLICADO`). Quita la serialización; exige repetir las pruebas de concurrencia de matrícula.
2. Mantener el candado, pero tomarlo en una transacción corta aparte (`REQUIRES_NEW`). **Riesgo:** necesita una segunda conexión mientras la externa retiene la suya, y con el pool agotado puede bloquearse; habría que subir el pool a más que la concurrencia esperada.
3. Aceptar el límite (una solicitud de alumno nuevo cada ~80 ms) y reformular RNF-12 con el cliente.

Después de aplicar cualquiera, repetir esta prueba y reemplazar la tabla de arriba.

## Cómo repetirla (PowerShell, desde la raíz; Docker Desktop encendido)
```powershell
.\scripts\reiniciar-bd.ps1
$env:GESEDU_LIMITE_AUTH_POR_MINUTO = '100000'
cd backend; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local,demo" "-Dspring-boot.run.jvmArguments=-Xmx1g"   # en otra terminal, esperar a que /actuator/health responda UP
Get-Content docs\despliegue\carga\preparar-apoderados.sql -Raw | docker exec -i -e PGPASSWORD=gesedu_app gesedu-postgres psql -h 127.0.0.1 -U gesedu_app -d gesedu
docker run --rm -v "${PWD}\docs\despliegue\carga:/carga" -e ESCENARIO=pico grafana/k6:latest run /carga/uc01.js
docker run --rm -v "${PWD}\docs\despliegue\carga:/carga" grafana/k6:latest run /carga/uc01.js
```
Variables: `BASE_URL` (por omisión `http://host.docker.internal:8080`), `USUARIOS` (200), `DURACION` (2m), `ANIO` (2027), `SEMILLA` (para no repetir DNI de alumnos entre corridas). Al terminar, reinicia la base: la prueba deja 200 cuentas y miles de solicitudes en la base de demo. Con Windows, si Git Bash reescribe las rutas, anteponer `MSYS_NO_PATHCONV=1`.
