# Prueba de carga de UC-01 con k6 (RNF-12, RNF-03)

**Resultado aceptado como medición local (06/11/2026), no como medición válida de RNF-12.** La **solicitud de matrícula cumple** el umbral en el pico de 200 apoderados (p95 de 1,59 s). El **p95 sostenido es de 2,53 s**, medido en una máquina que comparte CPU entre k6, backend, base de datos y Docker, así que no es representativo. Tras quitar el candado global del alta de alumnos (S-86, migración V13) el rendimiento mejoró de 2,5 a 2,7 veces. 0 % de errores y todas las solicitudes quedaron registradas (201) en todas las corridas.

**Pendiente:** la medición válida de RNF-12 se repite en QA, con la infraestructura de AWS y k6 fuera del backend. No se harán más optimizaciones antes de esa medición.

## Qué mide
`uc01.js` simula apoderados que recorren el asistente de «Solicitar matrícula» con un alumno nuevo: `GET /solicitudes-matricula/opciones` → `POST /solicitudes-matricula/archivos` → `PUT` de la URL prefirmada (disco local) → `POST /solicitudes-matricula`. Cada apoderado usa su propia cuenta (`carga001@carga.gesedu.pe` … `carga200@…`, contraseña de la demo). Umbrales: p95 < 2 s en total y por paso, errores < 1 %.

Dos escenarios (`ESCENARIO`):
- **pico** (RNF-12: «picos de al menos 200 apoderados concurrentes»): los 200 envían la solicitud a la vez, una vez.
- **sostenida** (por omisión): los 200 repiten el asistente sin parar durante 2 minutos (estrés, mucho más duro que un pico real).

## Condiciones de la medición
Máquina de desarrollo (Windows 11, 8 hilos lógicos; Docker Desktop con 4 GB) con todo en el mismo equipo: backend `local,demo` con `-Xmx1g`, PostgreSQL 18 y Mailpit en Docker y k6 (`grafana/k6`) en Docker. **IntelliJ estaba abierto.** Límite de inicios de sesión por IP subido con `GESEDU_LIMITE_AUTH_POR_MINUTO=100000` (los 200 apoderados salen de la misma IP). Base reiniciada y con las 200 cuentas de `preparar-apoderados.sql`. Los números de un equipo de desarrollo no son los de QA o AWS.

## Resultados (p95)
| Corrida | Iteraciones | Errores | total | opciones | url de subida | subida | solicitud |
|---|---|---|---|---|---|---|---|
| **Antes** (candado global), pico 200 × 1 | 200 | 0 % | 6,83 s | 5,94 s | 6,62 s | 42 ms | 7,26 s |
| **Antes**, sostenida 2 min | 1 845 | 0 % | 6,29 s | 6,31 s | 5,82 s | 19 ms | 6,86 s |
| **Después** (secuencia V13, candado por documento), pico 200 × 1 | 200 | 0 % | **3,40 s** | 4,17 s | 2,31 s | 59 ms | 1,59 s |
| **Después**, sostenida 2 min | 4 795 | 0 % | **2,53 s** | 2,79 s | 2,59 s | 52 ms | 2,76 s |
| Después, sostenida 90 s con pool de 30 conexiones (solo para diagnóstico) | 4 072 | 0 % | 2,43 s | 2,76 s | 2,33 s | 106 ms | 2,71 s |

Salidas completas: `resultado-k6-pico.txt`, `resultado-k6-sostenida.txt` (después) y `resultado-k6-sostenida-pool30.txt`. Las del «antes» están en el historial de git (commit `8b6b452`).

## Qué se cambió y qué queda
- **Causa original (verificada):** `pg_advisory_xact_lock(7311001)` en el alta de alumnos, mantenido hasta el commit de toda la solicitud, atendía las altas de una en una (28 de 30 conexiones esperando el candado según `pg_stat_activity`). Se reemplazó por una secuencia de PostgreSQL y un candado solo por documento (S-86). Con eso la solicitud pasa de 7,3 s a 1,6 s en el pico (ya bajo el umbral) y el rendimiento sostenido se multiplica por 2,6.
- **Lo que sigue por encima de 2 s:** los pasos de lectura (`opciones`, URL de subida) y el pico inicial. El volcado de hilos durante la carga muestra ~190 de 200 hilos esperando una conexión del pool (10 por omisión) y Postgres casi inactivo (esperando al cliente), es decir, la base ya no es el límite. Subir el pool a 30 **no** lo mejora (2,43 s frente a 2,53 s), por eso no se cambió el valor por omisión. Lo más probable es que ahora limite la CPU de esta máquina, que comparte backend, base de datos, k6 y el IDE; **no lo he demostrado** (habría que medir CPU por proceso o repetir la prueba con k6 y backend en máquinas distintas, por ejemplo en QA).
- **Conclusión para RNF-12:** en esta máquina el pico de 200 solicitudes simultáneas completa sin errores con p95 de 3,4 s; el umbral de 2 s queda **sin demostrar** hasta repetirla en QA o AWS. Decisión del 06/11/2026: se acepta este resultado como medición local y se detienen las optimizaciones.

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
