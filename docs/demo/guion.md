# Guion de la demo del 23/10/2026

Duración: **8 a 10 minutos**. Hilo conductor: el **diagrama de contenedores** (`docs/arquitectura.pdf`, Figura 3, § 7). Tenerlo abierto en una pestaña y volver a él en cada bloque.

```
Usuario ──HTTPS──▶ SPA (React, :5173) ──JSON/REST + JWT──▶ API Backend (Spring Boot, :8080) ──JDBC──▶ PostgreSQL 18 (Docker, :5432)
                                                                                                     └─ esquemas: seguridad, academico, horarios, auditoria…
```

Fuera de la demo (en el diagrama, pero no se muestran): almacén de documentos (S3) y correo (SES).

Credenciales: todas las cuentas usan la contraseña `Gesedu2027!`.

| Cuenta | Rol |
|---|---|
| `admin@demo.gesedu.pe` | Administrador (Carmen, Secretaría académica) |
| `directivo@demo.gesedu.pe` | Administrador (Ricardo, Coordinación académica) |
| `docente@demo.gesedu.pe`, `alumno@demo.gesedu.pe`, `apoderado@demo.gesedu.pe` | Docente, Alumno, Apoderado |
| `bloqueo@demo.gesedu.pe` | Docente, reservada para mostrar el bloqueo |

---

## Antes de presentar (15 minutos antes)

Todos los comandos son de PowerShell, desde la raíz del repositorio (`gesedu/`).

### 1. Solo Docker debe usar el puerto 5432

Si hay un PostgreSQL de Windows instalado, la API se conectaría a **esa** base y no a la de Docker.

```powershell
Get-NetTCPConnection -LocalPort 5432 -State Listen |
  ForEach-Object { (Get-Process -Id $_.OwningProcess).ProcessName } | Sort-Object -Unique
```

Debe aparecer **solo** `com.docker.backend` (o `wslrelay`, según la instalación de Docker Desktop). Si aparece `postgres`, es un servicio local: detenerlo desde PowerShell **como administrador**.

```powershell
Get-Service *postgres*                      # ver el nombre exacto
Stop-Service -Name <nombre-del-servicio>    # y volver a ejecutar la comprobación
```

Comprobar también que 8080 y 5173 estén libres (deben salir vacíos antes de arrancar, o ser los procesos que se van a usar):

```powershell
Get-NetTCPConnection -LocalPort 8080,5173 -State Listen -ErrorAction SilentlyContinue
```

### 2. Base de datos limpia y servicios

Detener el backend si estuviera corriendo (Ctrl+C o parar en IntelliJ) y luego:

```powershell
.\scripts\reiniciar-bd.ps1     # borra el volumen y recrea postgres:18 (roles gesedu_migrador y gesedu_app incluidos)
```

### 3. Backend con datos de demo (terminal 1)

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local,demo"
```

Esperar «Started GeseduApplication». Alternativa: la configuración `Backend (demo)` de IntelliJ.

### 4. Frontend (terminal 2)

```powershell
cd frontend
npm ci          # solo si es la primera vez
npm run dev
```

### 5. Terminal 3 (comandos de la demo)

Pegar este bloque una vez; define los atajos de `psql` y el de inicio de sesión que se usan abajo.

```powershell
$api = 'http://localhost:8080/api/v1'

# psql dentro del contenedor, como la aplicación (gesedu_app) o como dueño del esquema (gesedu_migrador)
function psql-app  { docker exec -e PGPASSWORD=gesedu_app       gesedu-postgres psql -h 127.0.0.1 -U gesedu_app       -d gesedu @args }
function psql-duenio { docker exec -e PGPASSWORD=gesedu_migrador gesedu-postgres psql -h 127.0.0.1 -U gesedu_migrador -d gesedu @args }

# POST de un objeto como JSON (curl.exe viene con Windows; con "-i" muestra cabeceras y estado)
function Enviar($ruta, $objeto, $token) {
  $cabecera = if ($token) { @('-H', "Authorization: Bearer $token") } else { @() }
  ($objeto | ConvertTo-Json -Compress) |
    curl.exe -s -i -X POST "$api$ruta" -H 'Content-Type: application/json' @cabecera --data-binary '@-'
}
```

### 6. Lista de comprobación final

```powershell
docker ps --format "table {{.Names}}`t{{.Image}}`t{{.Ports}}`t{{.Status}}"   # gesedu-postgres  postgres:18  ...  (healthy)
curl.exe -s http://localhost:8080/actuator/health                            # {"groups":["liveness","readiness"],"status":"UP"}
curl.exe -s -o NUL -w "SPA: %{http_code}`n" http://localhost:5173/login      # SPA: 200
psql-app -c "SELECT count(*) FROM horarios.bloque_horario;"                  # 45 (9 bloques x 5 días)
```

- [ ] Pestañas del navegador listas: (1) `arquitectura.pdf` en el diagrama de contenedores, (2) `http://localhost:5173/login`, (3) `http://localhost:8080/swagger-ui.html`.
- [ ] Nadie ha iniciado sesión en la SPA (cerrar sesión o ventana nueva). Usar una ventana de incógnito evita sesiones viejas.
- [ ] F12 se abre en la pestaña de la SPA solo durante el paso 3.
- [ ] Zoom del navegador y de las terminales subido para que se lea desde atrás.
- [ ] El token dura **15 minutos** y la SPA no lo renueva: si la demo se alarga, volver a iniciar sesión.
- [ ] La cuenta `bloqueo@` no debe estar ya bloqueada (si se ensayó, ver «Reiniciar entre ensayos»).

---

## Recorrido

### 0. El diagrama (30 s)
Mostrar la Figura 3. «Hoy vamos a recorrer estos tres contenedores con una operación real, de la pantalla a la base de datos, y a comprobar que las reglas de seguridad y de arquitectura se cumplen.»

### 1. Cada contenedor está corriendo (1 min)
| Contenedor del diagrama | Dónde corre | Comando | Resultado |
|---|---|---|---|
| Base de datos | Docker, `postgres:18` | `docker ps --format "table {{.Names}}`t{{.Image}}`t{{.Ports}}`t{{.Status}}"` | `gesedu-postgres` … `(healthy)` |
| API Backend | JVM, perfiles `local,demo` | `curl.exe -s http://localhost:8080/actuator/health` | `"status":"UP"` |
| SPA | Vite | abrir `http://localhost:5173` | pantalla de login |

Mostrar los esquemas por módulo en la base:

```powershell
psql-app -c "\dn"
```
Aparecen `seguridad`, `academico`, `horarios`, `auditoria`, etc. (los módulos de la arquitectura, un esquema por cada uno).

### 2. Swagger: el contrato de la API (1 min)
Pestaña `http://localhost:8080/swagger-ui.html`.
1. En **Seguridad** → `POST /api/v1/auth/inicio-sesion` → *Try it out* con `{"correo":"admin@demo.gesedu.pe","contrasena":"Gesedu2027!"}` → *Execute*. Mostrar `tokenAcceso`, `expiraEn` (15 min) y la lista de `permisos`.
2. Copiar el `tokenAcceso`, botón **Authorize** (candado), pegarlo → *Authorize*.
3. `GET /api/v1/anios-escolares/{anio}/bloques` con `anio = 2027` → 200 con los bloques.

«Este es el contrato que usan frontend y backend. La SPA solo habla con este API, nunca con la base.»

### 3. Recorrido de punta a punta: login → crear un bloque → F12 → psql (3 min)

**3.1 Login en la SPA.** `http://localhost:5173` → `admin@demo.gesedu.pe` / `Gesedu2027!`. Se llega a `/intranet/inicio`: el menú se arma con los permisos de la cuenta (Inicio, Bloques horarios, Auditoría).

**3.2 Crear un bloque.** Menú **Bloques horarios** (año 2027 muestra lunes a viernes). Con **F12 → Network → Fetch/XHR** abierto y *Preserve log* activado:
- Formulario «Nuevo bloque para 2027»: **Sábado**, **08:00** a **08:45** → *Crear bloque*. Aparece «Bloque creado» y el sábado en la lista.

**3.3 Qué mostrar en F12.** Seleccionar la petición `bloques` (POST):
- **Headers**: `POST http://localhost:5173/api/v1/anios-escolares/2027/bloques`, estado **201**. La SPA llama a su propio origen y Vite reenvía `/api` al :8080 (el arco SPA → API del diagrama). `Authorization: Bearer eyJ…` es el JWT.
- **Payload**: `{"diaSemana":"SABADO","horaInicio":"08:00","horaFin":"08:45"}`.
- **Response**: el bloque creado, con su `id` (UUIDv7).

**3.4 psql: el dato está en el último contenedor** (arco API → BD):
```powershell
psql-app -c "SELECT id, dia_semana, hora_inicio, hora_fin FROM horarios.bloque_horario WHERE dia_semana = 'SABADO';"
```
El `id` coincide con el de la respuesta de F12. Esta consulta ya corre como `gesedu_app`, el mismo rol que usa la API.

> Si se repite la demo sin reiniciar la base, ese bloque ya existe y la API responde **409** (se superpone). Es otra regla que se puede mostrar, o se reinicia la base (ver «Reiniciar entre ensayos»).

### 4. El alumno no puede crear: 403 con problem+json (1 min)
En la terminal 3:
```powershell
$tokenAlumno = ((Enviar '/auth/inicio-sesion' @{ correo = 'alumno@demo.gesedu.pe'; contrasena = 'Gesedu2027!' }) |
  Select-Object -Last 1 | ConvertFrom-Json).tokenAcceso

Enviar '/anios-escolares/2027/bloques' @{ diaSemana = 'SABADO'; horaInicio = '09:00'; horaFin = '09:45' } $tokenAlumno
```
Resultado:
```
HTTP/1.1 403
Content-Type: application/problem+json;charset=UTF-8

{"type":"https://gesedu.pe/problemas/ACCESO_DENEGADO","title":"No tienes permiso para realizar esta acción","status":403,"detail":"No tienes permiso para realizar esta acción","instance":"/api/v1/anios-escolares/2027/bloques","codigo":"ACCESO_DENEGADO"}
```
Señalar: `Content-Type: application/problem+json` (RFC 9457) y la propiedad `codigo`. «El alumno sí puede **listar** los bloques (permiso `HORARIO_CONSULTAR`); crear exige `HORARIO_CONFIGURAR`. La SPA oculta el formulario, pero quien decide es el backend.» En la SPA, con `alumno@demo.gesedu.pe`, la pantalla de bloques no muestra el formulario ni la opción Auditoría.

### 5. Bloqueo tras 5 intentos fallidos (1 min)
```powershell
1..5 | ForEach-Object {
  "--- Intento $_"
  Enviar '/auth/inicio-sesion' @{ correo = 'bloqueo@demo.gesedu.pe'; contrasena = 'incorrecta' } | Select-Object -Last 1
}
```
Los intentos 1 a 4 responden `CREDENCIALES_INVALIDAS` (401). El **quinto** ya responde `CUENTA_BLOQUEADA` (401) con `bloqueadoHasta` (15 minutos después). Probar luego con la contraseña **correcta**:
```powershell
Enviar '/auth/inicio-sesion' @{ correo = 'bloqueo@demo.gesedu.pe'; contrasena = 'Gesedu2027!' } | Select-Object -Last 1
```
Sigue respondiendo `CUENTA_BLOQUEADA`: el bloqueo vale aunque la clave sea la correcta (RNF-06). El mismo mensaje se ve en la pantalla de login de la SPA.

### 6. Auditoría (1 min)
En la SPA, con `admin@demo.gesedu.pe`: menú **Auditoría** (los demás roles no la ven). Aparecen, más recientes primero:
- `BLOQUEAR_CUENTA` (módulo `SEGURIDAD`), del paso 5.
- `CREAR_BLOQUE_HORARIO` (módulo `HORARIOS`, entidad `BloqueHorario`), del paso 3, con el nombre de Carmen Salas Paredes.

Probar el filtro por módulo `HORARIOS`. «Cada operación crítica se anota con `@Auditable` en la **misma transacción**: si la operación se revierte, el registro también.»

### 7. La auditoría no se puede borrar: el DELETE falla en psql (1 min)
Primero como la aplicación (`gesedu_app`):
```powershell
psql-app -c "DELETE FROM auditoria.registro_auditoria;"
```
```
ERROR:  permission denied for table registro_auditoria
```
Ahora como **dueño del esquema** (`gesedu_migrador`), que sí tiene todos los permisos:
```powershell
psql-duenio -c "DELETE FROM auditoria.registro_auditoria;"
```
```
ERROR:  Las tablas de auditoría son de solo inserción: DELETE no está permitido en auditoria.registro_auditoria
```
«Dos defensas: la aplicación solo tiene `INSERT` y `SELECT`, y un trigger rechaza `UPDATE`, `DELETE` y `TRUNCATE` incluso al dueño (RNF-08).» Confirmar que sigue todo:
```powershell
psql-app -c "SELECT count(*) FROM auditoria.registro_auditoria;"
```

### 8. ArchUnit en verde (1 min)
```powershell
cd backend
.\mvnw.cmd -B test "-Dtest=ArquitecturaTest"
```
Resultado esperado (unos 10 s, no necesita Docker):
```
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
«Las reglas del documento de arquitectura se verifican en cada build: el dominio no depende de otras capas, la API no usa la infraestructura, un módulo solo usa a otro por su capa de aplicación y no hay ciclos entre módulos.»

> La verificación completa del backend (pruebas de integración con Testcontainers incluidas) es `.\mvnw.cmd -B verify` y tarda varios minutos: **no ejecutarla en vivo**.

### Cierre (30 s)
Volver a la Figura 3. «Lo que vimos es el esqueleto vertical: SPA → API → base de datos, con seguridad por permisos, auditoría inmutable y reglas de arquitectura automatizadas. El resto de módulos se construye sobre esto.»

---

## Si algo falla

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| El backend no arranca: `Connection refused` o `password authentication failed` | Docker no está corriendo, el contenedor no está `healthy` o **otro PostgreSQL usa el 5432** | `docker ps`; `.\scripts\reiniciar-bd.ps1`; repetir la comprobación del puerto 5432 y detener el PostgreSQL local |
| Flyway: `Validate failed` / checksum distinto | La base tiene migraciones de otra versión | `.\scripts\reiniciar-bd.ps1` y arrancar el backend de nuevo |
| El login de la SPA no responde o dice «No se pudo conectar con el servidor» | El backend no está arriba o el proxy no llega al :8080 | `curl.exe -s http://localhost:8080/actuator/health`; reiniciar el backend. Usar Swagger como plan B |
| Login: `CREDENCIALES_INVALIDAS` con la clave correcta | Base sin datos de demo (backend arrancado sin el perfil `demo`) | Detener el backend, `.\scripts\reiniciar-bd.ps1` y arrancar con `local,demo` |
| De pronto todo da 401 / la SPA vuelve a `/login` | El token venció (15 min) | Iniciar sesión de nuevo |
| Crear bloque devuelve **409** | El sábado 08:00–08:45 ya existe o se superpone | Es la regla de no superposición; crear otro horario libre (por ejemplo, Sábado 10:00–10:45) o reiniciar la base |
| `bloqueo@` ya está bloqueada antes de la demo | Se ensayó el paso 5 | Ver «Reiniciar entre ensayos» |
| `Enviar` o `psql-app` «no se reconoce» | Se abrió otra terminal | Pegar de nuevo el bloque del paso 5 de «Antes de presentar» |
| `curl.exe` falla con comillas | Se usó `curl` (alias de `Invoke-WebRequest`) | Usar `curl.exe` o la función `Enviar` |
| F12 no muestra la petición | Se abrió F12 después de enviar | Activar *Preserve log* y crear otro bloque (cambiar la hora) |
| `mvnw` falla por falta de disco o de JDK | Disco C: lleno o `JAVA_HOME` incorrecto | Liberar espacio; `java -version` debe indicar 25. Plan B: mostrar el último resultado verde de `ArquitecturaTest` en IntelliJ (`Pruebas backend`) |
| La SPA muestra pantalla en blanco | Falló la compilación de Vite | Mirar la terminal 2; `npm ci` y `npm run dev` |

**Plan B general:** si la SPA falla, todo el recorrido se puede hacer desde Swagger (paso 2) y las terminales (pasos 4 a 8).

### Reiniciar entre ensayos
Sin tocar el resto de los datos, solo desbloquear la cuenta de `bloqueo@` (como dueño del esquema):
```powershell
psql-duenio -c "UPDATE seguridad.usuario SET intentos_fallidos = 0, bloqueado_hasta = NULL WHERE correo = 'bloqueo@demo.gesedu.pe';"
```
Para volver al estado inicial por completo (sin el bloque del sábado ni los registros de auditoría): detener el backend, ejecutar `.\scripts\reiniciar-bd.ps1` y arrancar el backend con `local,demo`.
