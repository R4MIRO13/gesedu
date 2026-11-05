# Guion de la demo ampliada (fases 1 a 4: seguridad, matrícula y pagos, configuración académica y horarios, asistencia)

Duración: **15 minutos** (el corte mínimo, pasos 0 a 6, dura unos **10**). Hilo conductor: el **diagrama de contenedores** (`docs/arquitectura.pdf`, Figura 3, § 7). Tenerlo abierto en una pestaña y volver a él en cada bloque.

```
Usuario ──HTTPS──▶ SPA (React, :5173) ──JSON/REST + JWT──▶ API Backend (Spring Boot, :8080) ──JDBC──▶ PostgreSQL 18 (Docker, :5432)
                                                                                    └─ SMTP ──▶ Mailpit (:8025, en AWS: SES)
```

Fuera de la demo (en el diagrama, pero no se muestran): almacén de documentos (S3). Los documentos de las solicitudes de matrícula figuran por nombre, pero sus archivos no están sembrados: no abrir «Ver».

Credenciales: todas las cuentas usan la contraseña `Gesedu2027!`.

| Cuenta | Rol |
|---|---|
| `admin@demo.gesedu.pe` | Administrador (Carmen, Secretaría académica) |
| `directivo@demo.gesedu.pe` | Administrador (Ricardo, Coordinación académica) |
| `docente@demo.gesedu.pe`, `alumno@demo.gesedu.pe`, `apoderado@demo.gesedu.pe` | Docente (Rosa Quispe), Alumno (Lucía García), Apoderado (María Rojas) |
| `bloqueo@demo.gesedu.pe` | Docente (Hugo), reservada para mostrar el bloqueo |
| `familia.torres@demo.gesedu.pe` | Apoderado (Marcos Torres): hijo **Tomás** ya matriculado en 1° A con sus 11 compromisos; su otra hija, Valeria, tiene la solicitud `00482` pendiente. Para los pagos (paso 11) |

Qué cubre cada paso:

| Paso | Contenido | Prioridad | Requisitos |
|---|---|---|---|
| 1 a 2 | Contenedores y Swagger | P1 | RNF-04 |
| 3 | Inicio de sesión por rol y 403 | P1 | RF-52, RNF-07 |
| 4 | Bloqueo tras 5 intentos | P1 | UC-24 FA-3, RNF-06 |
| 5 | Elaborar horario | P1 | UC-21, RF-39 a RF-42, RF-47 |
| 6 | Auditoría y exportación | P1 | UC-25, RF-49, RF-51, RNF-08 |
| 7 | Recuperación de contraseña y desbloqueo | P2 | UC-24 FA-5, RF-52 |
| 11 | Pagos: consulta, comprobante, validación e historial *(fase 2)* | P2 | UC-03 a UC-06, RF-08 a RF-11, RNF-10, RNF-11 |
| 8 | Registrar matrícula | P2 | UC-02, RF-04, RF-05, RF-07, RF-12, RNF-10 |
| 9 | Última vacante en vivo | P2 | RNF-09 |
| 12 | Configuración académica y horarios *(fase 3)* | P2 | UC-13, UC-14, UC-20, UC-22, UC-23, RF-03, RF-04, RF-43 a RF-47 |
| 13 | Asistencia: marcación docente, pase de lista, cambio a tardanza, cierre diario, reportes y resumen del alumno *(fase 4)* | P2 | UC-07 a UC-12, RF-13 a RF-21, RNF-03 |
| 10 | ArchUnit | P1 | DA-02 |

Si P2 no llega a tiempo, se omiten los pasos 7 a 9 y la demo sigue siendo válida.

---

## Antes de presentar (20 minutos antes)

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

Comprobar también que 8080, 5173, 8025 y 1025 estén libres (deben salir vacíos antes de arrancar, salvo los de Mailpit si ya está levantado):

```powershell
Get-NetTCPConnection -LocalPort 8080,5173 -State Listen -ErrorAction SilentlyContinue
```

### 2. Base de datos limpia y servicios

Detener el backend si estuviera corriendo (Ctrl+C o parar en IntelliJ) y luego:

```powershell
.\scripts\reiniciar-bd.ps1     # borra el volumen y recrea postgres:18 (roles gesedu_migrador y gesedu_app incluidos)
docker compose up -d postgres mailpit
```

**Hay que reiniciar la base antes de cada ensayo completo**: UC-02 gasta vacantes y la semilla (`V1000` a `V1002`) vuelve al estado de la demo.

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
docker ps --format "table {{.Names}}`t{{.Image}}`t{{.Ports}}`t{{.Status}}"   # gesedu-postgres  postgres:18 y gesedu-mailpit, ambos (healthy)
curl.exe -s http://localhost:8080/actuator/health                            # {"groups":["liveness","readiness"],"status":"UP"}
curl.exe -s -o NUL -w "SPA: %{http_code}`n" http://localhost:5173/login      # SPA: 200
psql-app -c "SELECT count(*) FROM horarios.bloque_horario;"                  # 45 (9 bloques x 5 días)
psql-app -c "SELECT codigo_seguimiento, estado FROM matricula.solicitud_matricula WHERE estado = 'PENDIENTE_REVISION' ORDER BY 1;"   # 8 filas, MAT-2027-00479 a 00486
psql-app -c "SELECT s.letra, s.vacantes_disponibles FROM academico.seccion s JOIN academico.grado g ON g.id = s.grado_id JOIN academico.anio_escolar a ON a.id = g.anio_escolar_id WHERE a.anio = 2027 AND ((g.numero_nivel = 3 AND s.letra = 'A') OR (g.numero_nivel = 2 AND s.letra = 'B'));"   # 3° A = 4 vacantes; 2° B = 1
```

- [ ] Pestañas del navegador listas: (1) `arquitectura.pdf` en el diagrama de contenedores, (2) `http://localhost:5173/login`, (3) `http://localhost:8080/swagger-ui.html`, (4) Mailpit `http://localhost:8025` (vacío: sin correos).
- [ ] Nadie ha iniciado sesión en la SPA (cerrar sesión o ventana nueva). Usar una ventana de incógnito evita sesiones viejas.
- [ ] F12 se abre en la pestaña de la SPA solo durante el paso 5.
- [ ] Zoom del navegador y de las terminales subido para que se lea desde atrás.
- [ ] El token dura **15 minutos** y la SPA lo renueva sola con la cookie de refresh; en Swagger, el token pegado sí vence: volver a iniciar sesión si la demo se alarga.
- [ ] La cuenta `bloqueo@` no debe estar ya bloqueada (si se ensayó, ver «Reiniciar entre ensayos»).

---

## Recorrido

### 0. El diagrama (30 s)
Mostrar la Figura 3. «Hoy recorremos estos contenedores con tres casos de uso reales, de la pantalla a la base de datos, y comprobamos que las reglas de seguridad, de concurrencia y de arquitectura se cumplen.»

### 1. Cada contenedor está corriendo (1 min)
| Contenedor del diagrama | Dónde corre | Comando | Resultado |
|---|---|---|---|
| Base de datos | Docker, `postgres:18` | `docker ps --format "table {{.Names}}`t{{.Image}}`t{{.Ports}}`t{{.Status}}"` | `gesedu-postgres` … `(healthy)` |
| Correo (SES en AWS) | Docker, Mailpit | misma tabla; abrir `http://localhost:8025` | `gesedu-mailpit` … `(healthy)` |
| API Backend | JVM, perfiles `local,demo` | `curl.exe -s http://localhost:8080/actuator/health` | `"status":"UP"` |
| SPA | Vite | abrir `http://localhost:5173` | pantalla de login |

Mostrar los esquemas por módulo en la base:

```powershell
psql-app -c "\dn"
```
Aparecen `seguridad`, `academico`, `matricula`, `pagos`, `horarios`, `auditoria`, etc. (los módulos de la arquitectura, un esquema por cada uno).

### 2. Swagger: el contrato de la API (1 min)
Pestaña `http://localhost:8080/swagger-ui.html`. El selector de arriba tiene un grupo por módulo: **Seguridad**, **Horarios**, **Auditoría** y **Matrícula**.
1. En **Seguridad** → `POST /api/v1/auth/inicio-sesion` → *Try it out* (el cuerpo ya trae un ejemplo) con `{"correo":"admin@demo.gesedu.pe","contrasena":"Gesedu2027!"}` → *Execute*. Mostrar `tokenAcceso`, `expiraEn` (15 min) y la lista de `permisos`.
2. Copiar el `tokenAcceso`, botón **Authorize** (candado), pegarlo → *Authorize*.
3. Cambiar al grupo **Horarios** → `GET /api/v1/anios-escolares/{anio}/bloques` con `anio = 2027` → 200 con los 45 bloques. Mostrar de paso los endpoints del borrador (`If-Match`, 409) y, en **Matrícula**, `POST …/confirmacion`.

«Este es el contrato que usan frontend y backend; los tipos de la SPA se generan de aquí. La SPA solo habla con este API, nunca con la base.»

### 3. Inicio de sesión por rol y 403 (1,5 min) — RF-52, RNF-07
En la SPA (`http://localhost:5173`) iniciar sesión con cada cuenta y mostrar el menú lateral:

| Cuenta | Opciones del menú |
|---|---|
| `admin@demo.gesedu.pe` | Inicio, Bloques horarios, Elaborar horario, Matrículas, Usuarios, Auditoría |
| `directivo@demo.gesedu.pe` | las mismas (es un Administrador con otro cargo: «Coordinación académica») |
| `docente@demo.gesedu.pe` | Inicio, Bloques horarios |
| `alumno@demo.gesedu.pe` | Inicio, Bloques horarios |
| `apoderado@demo.gesedu.pe` | Inicio, Bloques horarios |

Con el **alumno**, en la terminal 3, el backend es quien decide:
```powershell
$tokenAlumno = ((Enviar '/auth/inicio-sesion' @{ correo = 'alumno@demo.gesedu.pe'; contrasena = 'Gesedu2027!' }) |
  Select-Object -Last 1 | ConvertFrom-Json).tokenAcceso

curl.exe -s -i "$api/auditoria/registros" -H "Authorization: Bearer $tokenAlumno"
```
```
HTTP/1.1 403
Content-Type: application/problem+json;charset=UTF-8

{"type":"https://gesedu.pe/problemas/ACCESO_DENEGADO","title":"No tienes permiso para realizar esta acción","status":403,…,"codigo":"ACCESO_DENEGADO"}
```
Señalar `application/problem+json` (RFC 9457) y la propiedad `codigo`; la respuesta llega en menos de un segundo. «La SPA solo oculta opciones; la autorización real es del backend.»

### 4. Bloqueo tras 5 intentos fallidos (1 min) — UC-24 FA-3, RNF-06
En la SPA, cerrar sesión y en `/login` escribir 5 veces una contraseña incorrecta con `bloqueo@demo.gesedu.pe`. Los intentos 1 a 4 muestran «Correo o contraseña incorrectos.»; el quinto muestra el mensaje del wireframe («Tu cuenta se bloqueó temporalmente por 5 intentos fallidos…») con la hora hasta la que dura. Probar luego con la contraseña **correcta** `Gesedu2027!`: sigue bloqueada (el bloqueo vale aunque la clave sea la correcta).

Mismo resultado desde la API (opcional):
```powershell
Enviar '/auth/inicio-sesion' @{ correo = 'bloqueo@demo.gesedu.pe'; contrasena = 'incorrecta' } | Select-Object -Last 1
```
El quinto intento responde `CUENTA_BLOQUEADA` (401) con `bloqueadoHasta` (15 minutos después, en `-05:00`).

### 5. Elaborar horario (3 min) — UC-21, RF-39 a RF-42, RF-47
Con `admin@demo.gesedu.pe`: menú **Elaborar horario** (año 2027, perspectiva *Sección*). Abrir F12 → Network → Fetch/XHR con *Preserve log*.

1. Elegir **3° A**. La matriz muestra el borrador y, a la derecha, «Carga horaria · 3° A»: **19 de 22 horas asignadas**, con las horas de cada curso.
2. Tocar el bloque **martes 9:15 – 10:00** (libre) y elegir **Matemática**. El panel de docentes solo ofrece a los asociados al curso (RF-47) y valida en vivo:
   - **Rosa Quispe** → «Tiene clase en 4° B».
   - **Carlos Ríos** → «No disponible».
   - **Fernando Medina** → disponible.
3. Elegir a Rosa: el aviso en vivo dice «Prof. R. Quispe ya tiene clase en 4° B el martes de 9:15 a 10:00» (en F12, `POST …/borrador/validaciones`). El botón **Guardar** no se bloquea por los avisos: al pulsarlo es el **servidor** quien rechaza, con **409 `CONFLICTO_HORARIO`**, y la causa aparece en rojo. Repetir con Carlos: **422 `DOCENTE_NO_DISPONIBLE`**.
4. Elegir a **Fernando Medina** («Sin conflictos: se puede guardar.») y **Guardar**: F12 muestra `POST …/borrador/asignaciones` con la cabecera `If-Match: "n"` y estado **201**. La carga de 3° A pasa a **20 de 22**.
5. Cambiar a la perspectiva **Docente** y elegir a Fernando: es el **mismo borrador**, con la clase nueva.
6. **Versión (`@Version`)**: abrir una segunda pestaña en la misma pantalla, guardar una clase en la primera y luego intentar otra en la segunda sin recargar → **409 `CONFLICTO_CONCURRENCIA`** y el aviso «Recargar matriz».

«Dos personas no pueden pisarse: la versión del borrador viaja en `If-Match`, y los índices únicos de la base cierran la carrera aunque las dos validaciones pasen.» El alumno no ve esta opción ni puede llamar al borrador (RF-44).

### 6. Auditoría y exportación (2 min) — UC-25, RF-49, RF-51, RNF-08
En la SPA, con `admin@demo.gesedu.pe`: menú **Auditoría** (los demás roles no la ven). Aparecen, más recientes primero:
- `CREAR_ASIGNACION_HORARIO` (módulo `HORARIOS`), del paso 5, con el nombre de Carmen Salas Paredes. **Ver cambios** muestra el curso y el docente (valor anterior vacío, valor nuevo).
- `BLOQUEAR_CUENTA` (módulo `SEGURIDAD`, usuario *Sistema*), del paso 4.

Filtrar por módulo `HORARIOS` (las acciones del segundo filtro dependen del módulo) y por fechas. **Exportar ▾ → Excel (.xlsx)** descarga el archivo con los mismos filtros; mostrarlo abierto.

**La auditoría no se puede borrar.** Primero como la aplicación (`gesedu_app`):
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
«Dos defensas: la aplicación solo tiene `INSERT` y `SELECT`, y un trigger rechaza `UPDATE`, `DELETE` y `TRUNCATE` incluso al dueño. Cada operación crítica se anota con `@Auditable` en la **misma transacción**: si la operación se revierte, el registro también.»

### 7. Recuperación de contraseña y desbloqueo (2 min) — UC-24 FA-5 *(P2)*
1. En `/login`, **¿Olvidaste tu contraseña?** → escribir `bloqueo@demo.gesedu.pe` → *Enviar enlace*. El mensaje es el mismo si se escribe un correo que no existe (probarlo con `nadie@demo.gesedu.pe`: no llega nada a Mailpit).
2. Abrir Mailpit (`http://localhost:8025`): llegó «Restablece tu contraseña de GESEDU» con el enlace de un solo uso (30 minutos).
3. Abrir el enlace, definir una contraseña nueva (8 a 72 caracteres, con una letra y un número, por ejemplo `NuevaClave2027`) → vuelve al login. Volver a abrir el **mismo enlace** y definir otra contraseña: «El enlace no es válido o ya venció», con el botón *Pedir otro enlace*.
4. Restablecer la contraseña **levanta el bloqueo y reinicia el contador**: `bloqueo@demo.gesedu.pe` ya entra con la contraseña nueva. Para mostrar el desbloqueo del administrador, antes del paso 3 (o volviendo a fallar 5 veces) abre, con el administrador, el menú **Usuarios** → filtrar por estado *Bloqueados* → Hugo Gonzales aparece como «Bloqueado · 5 intentos» → **Desbloquear**.
5. En **Auditoría** aparecen `RESTABLECER_CONTRASENA` y `DESBLOQUEAR_CUENTA`.

### 8. Registrar matrícula (4 min) — UC-02 *(P2)*
Con `admin@demo.gesedu.pe`: menú **Matrículas**. La bandeja muestra 8 solicitudes pendientes (`MAT-2027-00479` a `00486`).

**Flujo básico — `MAT-2027-00482`, Valeria Torres Gil (3°).**
1. *Evaluar*. Las cuatro validaciones dicen «Cumple» (documentación, admisión, matrícula activa y deudas). Las secciones muestran **3° A: 4 de 30 vacantes** y 3° B: 30.
2. El **resumen de costos** lista 11 compromisos (matrícula y 10 pensiones de S/ 350.00): **S/ 3850.00**. La SPA no calcula nada: viene del backend.
3. Elegir **3° A**, marcar «El apoderado aceptó los costos» → **Confirmar matrícula**. Aparece: «3° A pasa a 3 vacantes y se generan 11 compromisos de pago por S/ 3850.00».
4. En Mailpit llegó «Matrícula confirmada 2027 — Valeria Torres Gil» a `familia.torres@demo.gesedu.pe`, con el cronograma. Sale **después del commit**: si la transacción falla, no sale.
5. Comprobar en la base que todo ocurrió junto:
```powershell
psql-app -c "SELECT count(*), sum(monto_base) FROM pagos.obligacion_pago o JOIN seguridad.alumno a ON a.id = o.alumno_id WHERE a.codigo_alumno = '2026-0404';"   # 11 | 3850.00
```
y en **Auditoría**, `CONFIRMAR_MATRICULA` con los cambios (estado, sección, vacantes 4 → 3, compromisos generados).

**Alternativas (se muestran con otras solicitudes; cada una bloquea la confirmación y se rechaza con su motivo):**

| Solicitud | Alternativa | Qué se ve | Rechazo |
|---|---|---|---|
| `00479` Diego Paredes | FA-3, deuda | «Tiene deudas vencidas de años anteriores por S/ 700.00»; *Confirmar* deshabilitado | Motivo **Deudas** → «Rechazada por Deudas»; el correo a `familia.paredes@` incluye el importe |
| `00480` Camila Soto | FA-1, no admitida | Admisión «No admitido» (se puede cambiar en «Resultado de admisión» si el colegio la admitió) | **No Admitido** |
| `00481` Mateo Ríos | FA-2, matrícula activa | «Ya tiene una matrícula activa en el año» (está en 3° B) | **Duplicidad** |
| `00485` Joaquín Méndez | Documentación incompleta | «Documentos incompletos» en la bandeja; no se puede confirmar | (se espera a que complete la documentación) |
| `00486` Renata Castillo | FA-5, desistimiento | Todo en verde | **Voluntad del apoderado**, sin tocar vacantes ni deudas |

Tras rechazar, la solicitud queda «Rechazada por …» y **no queda matrícula** (`SELECT count(*) FROM matricula.matricula …`). Si se elige un motivo que las validaciones no respaldan, el backend responde 400 `SOLICITUD_INVALIDA`.

### 9. La última vacante, en vivo (1,5 min) — RNF-09 *(P2)*
`MAT-2027-00483` (Andrés Quispe) y `00484` (Sofía Vargas) son admitidos y compiten por **2° B, que tiene 1 vacante**.
1. Abrir las dos solicitudes en dos pestañas, elegir **2° B** en ambas y marcar los costos.
2. Confirmar la primera: «2° B pasa a 0 vacantes y se generan 11 compromisos de pago».
3. Confirmar la segunda (su pantalla todavía muestra «1 de 30 vacantes»): el servidor responde **409 `SIN_VACANTE`** y la SPA avisa que la sección ya no tiene vacantes y sugiere elegir otra o rechazar la solicitud por falta de vacante.
4. Rechazar la segunda con motivo **Sin vacante** → «Rechazada sin Vacante».

La prueba con 20 hilos simultáneos sobre una sola vacante (una gana, 19 reciben `SIN_VACANTE`, las vacantes nunca bajan de 0) se ejecuta con:
```powershell
cd backend
.\mvnw.cmd -B verify "-Dtest=NoExiste" "-Dit.test=VacantesConcurrentesIT" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dfailsafe.failIfNoSpecifiedTests=false"
```
Necesita Docker y tarda cerca de un minuto: mejor mostrar el último resultado verde en IntelliJ (`Todas las pruebas`) que correrla en vivo.

«La vacante se descuenta con un `UPDATE` condicional (`vacantes > 0`): la base serializa a quienes compiten y el `CHECK` impide que baje de cero.»

### 10. ArchUnit en verde (1 min)
```powershell
cd backend
.\mvnw.cmd -B test "-Dtest=ArquitecturaTest"
```
Resultado esperado (unos 15 s, no necesita Docker):
```
Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
«Las reglas del documento de arquitectura se verifican en cada build: el dominio no depende de otras capas, la API no usa la infraestructura, un módulo solo usa a otro por su capa de aplicación y no hay ciclos entre módulos. Matrícula usa a Pagos por su capa de aplicación, no por sus tablas.»

> La verificación completa del backend (pruebas de integración con Testcontainers incluidas) es `.\mvnw.cmd -B verify` y tarda varios minutos: **no ejecutarla en vivo**.

### 11. Pagos: del comprobante a la boleta (5 min) — UC-03 a UC-06 *(fase 2)*
Se usa a la familia Torres, que ya viene lista en la semilla (no hace falta confirmar nada antes ni tocar la base).

1. Con `familia.torres@demo.gesedu.pe`: menú **Matrícula y pagos**. Arriba, los botones **Tomás · 1° A** y **Valeria** (Valeria todavía no tiene matrícula vigente: «no tiene una matrícula vigente», UC-03 FA-2). En Tomás: matrícula *Confirmada*, 11 obligaciones de S/ 350.00, «Próximo vencimiento S/ 350.00 · Matrícula · 28/02/2027», deuda vencida S/ 0.00.
2. En la fila **Matrícula**, **Subir comprobante** → número de operación `00458712`, medio «Transferencia - BCP», cualquier PDF o foto de hasta 5 MB como voucher → **Enviar comprobante**: «Está pendiente de validación» y la obligación sigue *Pendiente* (UC-04: presentar no cambia nada). De vuelta en la lista, esa fila dice «Comprobante en revisión» y ya no ofrece subir otro.
3. Repetir con la fila de **Pensión marzo** y el **mismo número** `00458712`: «El número de operación ya fue presentado en otro comprobante» (RF-08).
4. Con `admin@demo.gesedu.pe`: **Validar comprobantes** → abrir el comprobante de Tomás Torres Gil: obligación con importe base y total, lo declarado por el apoderado y las verificaciones (número no registrado antes, monto cubre el total). **Validar y registrar boleta**: «Pago registrado. Boleta B001-00001 por S/ 350.00». En Mailpit (http://localhost:8025) llegó «Pago registrado: Matrícula 2027» al apoderado (después del commit).
5. De nuevo como Torres: la matrícula figura **Pagada** con «Boleta B001-00001». En **Auditoría**, `PRESENTAR_COMPROBANTE` y `VALIDAR_PAGO` (con el estado de la obligación PENDIENTE → PAGADA).
6. Alternativas (admin): presentar otro comprobante y **Observar** con un texto («El monto no cubre la penalidad») → el apoderado recibe el correo con el importe total pendiente y puede volver a presentar; o **Rechazar** (UC-05 FA-1 y FA-2). Ninguna toca la obligación.
7. **Historial financiero** → buscar «Torres» → Tomás: tarjetas *Total pagado S/ 350.00*, *Pendiente*, pestañas Obligaciones / Boletas y pagos / Deudas y penalidades (UC-06, solo consulta).
8. **Configuración de cobros**: cambiar el monto de una pensión pendiente (por ejemplo, mayo a 400.00) → «Se actualizaron N compromisos pendientes»; en la cuenta de Torres esa pensión ya dice S/ 400.00 y la ya pagada no cambió (RF-07). El job de penalidades (00:05) no tiene pantalla: se muestra su prueba, `JobVencimientosIT`.

### 12. Configuración académica y horarios (8 min) — UC-13, UC-14, UC-20, UC-22, UC-23 *(fase 3)*
Contraseña de todas las cuentas: `Gesedu2027!`. Con `.\scriptseiniciar-bd.ps1` y el backend `local,demo` el borrador 2027 viene casi completo: 8 secciones con sus 22 h, **3° A con 19 h (faltan 3)** y **4° B con 23 h (excede 1)**.

1. **UC-13** · `directivo@demo.gesedu.pe` → **Año escolar** → **Nuevo año escolar**. Fechas 14/02/2028 – 19/12/2028: «Duración: 10 meses y 5 días» → **Siguiente**. Escribe los bimestres de modo que el III empiece antes de que termine el II: aparece «El Bimestre III se cruza con el Bimestre II. Corrige las fechas…», la etiqueta «Se superpone», la línea de tiempo con el tramo rayado y **Crear año escolar** queda deshabilitado (FA-2). Corrige las fechas y crea el año (queda *Planificado*, con sus grados 1° a 5°). Para FA-1, en el paso 1 pon como fin 14/11/2028: «El año escolar debe durar entre 10 y 12 meses».
2. **UC-14** · **Cursos y competencias** (año 2027): 6 cursos · 22 h. Elige *Matemática*, agrega una competencia y **Guardar curso**; intenta crear otra vez «Matemática» → 409 «Ya existe el curso». Para la copia (FA-1) hace falta un año sin cursos: abre `/intranet/academico/2028/cursos` → **Copiar configuración de 2027** → **Confirmar copia** («Se copiaron 6 cursos de 2027 con sus competencias»).
3. **Secciones y vacantes** · `/intranet/academico/2027/secciones`: 3° A con 26 matriculados y 4 vacantes. Sube la capacidad de 30 a 35 (vacantes 9) y baja a 25: «La capacidad no puede ser menor que los 26 estudiantes ya matriculados».
4. **Configuración de horarios** (admin): pestañas *Periodo de disponibilidad* (abierto hasta el 30/06/2027), *Docentes y cursos* (Marta Ibáñez figura «Sin confirmar») y *Cargas horarias* (bloques por curso y grado).
5. **UC-20** · `docente.nuevo@demo.gesedu.pe` → **Mi disponibilidad**: la matriz viene vacía y la carga es de 10 bloques. Marca 6 bloques: «Tu disponibilidad podría ser insuficiente para tu carga horaria. Faltan al menos 4 bloques.» (FA-2). Marca 12 y **Confirmar disponibilidad** (FA-1: al volver a confirmar solo queda la última versión). Con `admin` cierra el periodo (desmarcar *Periodo habilitado*) y vuelve a entrar como Marta: la matriz queda de solo lectura (FE-1). Con `docente@demo.gesedu.pe` (Rosa) la matriz ya tiene todos los bloques marcados.
6. **UC-22** · `admin@demo.gesedu.pe` → **Publicar horario**: «Hay 2 secciones con observaciones. Corrígelas antes de publicar. (3° A y 4° B)»; 3° A: «Faltan 3 h» (Inglés 2/3 h · Educación Física 0/2 h) y 4° B: «Excede 1 h»; **Publicar horario** está deshabilitado (FA-1). **Volver a elaborar horario**: en 4° B elimina una clase de Educación Física y en 3° A agrega 1 h de Inglés (Carmen Villanueva) y 2 h de Educación Física (Óscar Cárdenas). Vuelve a **Publicar horario**: todas las secciones *Completo* → **Publicar horario** → «¿Publicar el horario 2027? La versión 1 pasará a ser la vigente» (aquí **Cancelar** es FE-1) → **Confirmar publicación**. Quedan 220 clases en la versión 1.
7. **UC-23** · `alumno@demo.gesedu.pe` (Lucía, 3° A) → **Horario**: «Versión 1 publicada · vigente desde …», su matriz de la semana; clic en un bloque → *Detalle del bloque* (curso, sección, docente, día y bloque). `apoderado@demo.gesedu.pe` ve lo mismo; `familia.torres@demo.gesedu.pe`, el 1° A de Tomás. `docente@demo.gesedu.pe` ve sus 20 clases y puede cambiar a *Por sección* solo en las secciones donde dicta o es tutora. Antes de publicar (paso 6), cualquiera de ellos ve «El horario aún no se encuentra disponible» (FA-1).
8. **Auditoría**: `CREAR_ANIO_ESCOLAR`, `COPIAR_CURSOS_ANIO_ANTERIOR`, `MODIFICAR_SECCION`, `CONFIRMAR_DISPONIBILIDAD` (bloques disponibles 0 → 12) y `PUBLICAR_HORARIO` (versión vigente → 1, 220 clases).
9. La inmutabilidad se muestra en psql: `docker exec -it gesedu-postgres psql -U gesedu_app -d gesedu -c "DELETE FROM horarios.asignacion_publicada"` → «los horarios publicados son inmutables».

> Publicar es definitivo: para repetir el ensayo hay que reiniciar la base (`.\scriptseiniciar-bd.ps1`). Las pruebas de este recorrido son `DatosDemoFase3IT` (todo el flujo con estos usuarios) y `PublicacionHorarioIT` (concurrencia e inmutabilidad).

### 13. Asistencia (12 min) — UC-07 a UC-12 *(fase 4)*
**Dos preparaciones.** (1) **El reloj de la demo.** El perfil `demo` corre con un reloj propio, porque el año de la semilla es 2027: arranca el **lunes 15/03/2027 a las 7:40 a. m.** y avanza en tiempo real (`GESEDU_DEMO_RELOJ_INICIO` lo cambia; ver «Reiniciar entre ensayos»). Toda hora de asistencia la pone el servidor con ese reloj: la de tu computadora no cuenta. (2) **El horario publicado**: UC-07 y UC-12 lo necesitan. Si hiciste el paso 12 (UC-22), ya está; con una base recién reiniciada, publícalo con el atajo (una sola vez):
```powershell
docker exec -i -e PGPASSWORD=gesedu_app gesedu-postgres psql -h 127.0.0.1 -U gesedu_app -d gesedu < docs\demo\atajos\publicar-horario.sql
```
La semilla deja lo ocurrido antes de ese lunes: **3° A con seis alumnos** (Lucía y cinco más), asistencia de alumnos y docentes del 01/03 al 12/03, los cierres de esas fechas y **tres docentes que ya marcaron hoy** (Rosa no).

1. **UC-07 · Marcación docente** · `docente@demo.gesedu.pe` (Rosa) → **Mi asistencia**: «Lunes 15/03/2027», la **hora del servidor** corriendo (7:4x a. m.), «Tu primera clase hoy», «Tolerancia para docentes: 5 minutos · eres puntual hasta las …» y los últimos días (la secuencia del wireframe: puntual, falta «Generado al cierre de jornada», puntual, tardanza…). **Registrar asistencia** → «Asistencia registrada a las 7:4x a. m.: Puntual.» El botón queda deshabilitado y la fila de hoy pasa a «Puntual». *El límite se mide con la primera clase **de Rosa** ese día, no con la del colegio; la hora y la fecha salen del servidor (la petición no lleva ninguna).*
2. **UC-08 · Pase de lista** · misma cuenta → **Asistencia de mi sección** (te lleva a `/intranet/asistencia/3A/registrar`): seis alumnos en orden alfabético, cada uno con Puntual / Tardanza / Falta. **Guardar asistencia** sin elegir nada: «Faltan 6 alumnos por registrar» y las filas marcadas (FA-1). **Marcar todos como Puntual**, cambia uno a **Tardanza** y otro a **Falta** → **Guardar asistencia**: «Asistencia registrada: 4 puntuales, 1 tardanza y 1 falta» (el lote es atómico: todo o nada).
3. **UC-09 · Falta → Tardanza** · **Ver el registro del día**: «Jornada abierta», «Puntual 4 · Tardanza 1 · Falta 1». En la fila de quien está en falta, **Cambiar a Tardanza** → el diálogo muestra la hora límite (8:30 a. m.) → **Confirmar cambio** → «La asistencia de … ahora es Tardanza.» *Pasadas las 8:30 (hora del servidor) el botón desaparece y el servidor responde 422 `PLAZO_TARDANZA_EXCEDIDO`; el cambio queda en la auditoría y no hay historial de estados en el módulo (C-07).*
4. **UC-12 · Consulta del administrador** · `admin@demo.gesedu.pe` → **Asistencia docente**: los docentes con clases hoy, quiénes marcaron (Rosa y los tres sembrados), «Sin registrar» para el resto y el aviso «El cierre diario aún no procesó esta fecha». *«Sin registrar» no es falta: todavía no cierra la jornada.*
5. **UC-12 · El cierre diario** · el cierre corre a las 15:30 y no se puede esperar: **detén el backend** (Ctrl+C) y **arráncalo de nuevo con el reloj a las 15:29** (la base y todo lo hecho en la mañana se conservan):
   ```powershell
   $env:GESEDU_DEMO_RELOJ_INICIO = '2027-03-15T15:29:00-05:00'
   cd backend; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local,demo"
   ```
   Pasados 1 o 2 minutos (el job corre cada minuto y ShedLock evita ejecuciones dobles) refresca **Asistencia docente**: «El cierre diario ya procesó esta fecha: N faltas creadas de M docentes con clases» y, para quien no marcó, **Falta · Generado al cierre de jornada** (sin hora). *Rosa y los demás que marcaron no cambian; el cierre se puede repetir sin duplicar nada y nunca crea faltas de alumnos.* En **Mi asistencia** de Rosa: ya no se puede marcar («La jornada cerró a las 15:30»).
6. **UC-10 y UC-11 · Reportes** · `docente@` → **Reporte de asistencia de mi sección** → **Ver reporte** (el mes por omisión es «Mes actual», el del servidor: marzo de 2027): los seis alumnos con puntual, tardanza, falta, **no registrados** y **% de asistencia** (= 100 × (puntual + tardanza) / días registrados), el aviso de **reporte parcial** (el mes sigue en curso: se calcula hasta ayer) y de información incompleta. **Descargar Excel** y **Descargar PDF**: el archivo trae el alcance, el periodo, la fecha y hora de generación y la fórmula. Con `admin@` → **Reportes de asistencia** → *Docentes* → **Descargar PDF**. *Nada se guarda: cada reporte se calcula al pedirlo, y el tutor de otra sección o de docentes recibe 403.*
7. **Resumen del alumno** · `alumno@demo.gesedu.pe` (Lucía) → **Inicio**: la tarjeta **Mi asistencia de marzo** con su porcentaje, los conteos y los días sin registro (que no cuentan como faltas); es el mismo valor que la fila de Lucía en el reporte del tutor.
8. **Auditoría** · `admin@` → **Auditoría** → módulo *ASISTENCIA*: `CAMBIAR_ASISTENCIA_A_TARDANZA` (Falta → Tardanza, con el tutor como actor) y `CERRAR_ASISTENCIA_DOCENTE` (usuario «Sistema»). *La marcación en sí no se audita: el propio registro es la evidencia.*

> El paso 5 gasta el día (las faltas quedan escritas): para repetir el ensayo reinicia la base. Las pruebas de este recorrido son `DatosDemoFase4IT` (todo el flujo con estos usuarios y el atajo), `MarcacionDocenteIT`, `PaseListaConcurrenteIT`, `ActualizacionAsistenciaIT`, `CierreDiarioIT` (idempotencia, concurrencia y ShedLock) y `ReporteAsistenciaIT`.

### Cierre (30 s)
Volver a la Figura 3. «Vimos tres casos de uso completos sobre el esqueleto: seguridad por permisos, horarios sin cruces ni pisadas, matrícula atómica con correo, y una auditoría que nadie puede borrar. El resto de módulos se construye sobre esto.»

---

## Si algo falla

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| El backend no arranca: `Connection refused` o `password authentication failed` | Docker no está corriendo, el contenedor no está `healthy` o **otro PostgreSQL usa el 5432** | `docker ps`; `.\scripts\reiniciar-bd.ps1`; repetir la comprobación del puerto 5432 y detener el PostgreSQL local |
| Flyway: `Validate failed` / checksum distinto | La base tiene migraciones de otra versión | `.\scripts\reiniciar-bd.ps1` y arrancar el backend de nuevo |
| El login de la SPA no responde o dice «No se pudo conectar con el servidor» | El backend no está arriba o el proxy no llega al :8080 | `curl.exe -s http://localhost:8080/actuator/health`; reiniciar el backend. Usar Swagger como plan B |
| Login: `CREDENCIALES_INVALIDAS` con la clave correcta | Base sin datos de demo (backend arrancado sin el perfil `demo`) | Detener el backend, `.\scripts\reiniciar-bd.ps1` y arrancar con `local,demo` |
| De pronto todo da 401 / la SPA vuelve a `/login` | El token o la cookie de refresh vencieron, o se inició sesión en otra pestaña con otra cuenta | Iniciar sesión de nuevo |
| Elaborar horario: no aparece el conflicto de Rosa o de Carlos | Se ensayó y quedó una clase de Fernando (u otra) en el martes 9:15 de 3° A | Eliminar esa clase en la matriz (*Eliminar clase*) o reiniciar la base |
| Elaborar horario devuelve 409 al guardar | Otra pestaña cambió el borrador | Es la regla de versión: *Recargar matriz* y repetir |
| Mailpit no muestra el correo | El backend se arrancó sin el perfil `local` (usa otro SMTP) o Mailpit no está levantado | `docker compose up -d mailpit`; el correo se reintenta 3 veces y no revierte la operación |
| Matrículas: la bandeja está vacía o faltan solicitudes | Base sin la semilla `V1002` (datos de una versión anterior) | `.\scripts\reiniciar-bd.ps1` y arrancar con `local,demo` |
| Matrículas: «Confirmar matrícula» está deshabilitado | Una validación no se cumple (es el comportamiento esperado en las alternativas) o la solicitud ya se evaluó | Leer la validación fallida; rechazar con su motivo o elegir otra solicitud |
| Matrículas: «Ver» un documento da 404 | Los archivos de la semilla no existen (solo su registro) | No abrir los documentos en la demo |
| Usuarios: no aparece «Bloqueado» | La cuenta no llegó a 5 intentos fallidos consecutivos o ya se desbloqueó | Repetir el paso 4 |
| `Enviar` o `psql-app` «no se reconoce» | Se abrió otra terminal | Pegar de nuevo el bloque del paso 5 de «Antes de presentar» |
| `curl.exe` falla con comillas | Se usó `curl` (alias de `Invoke-WebRequest`) | Usar `curl.exe` o la función `Enviar` |
| F12 no muestra la petición | Se abrió F12 después de enviar | Activar *Preserve log* y guardar otra clase |
| `mvnw` falla por falta de disco o de JDK | Disco C: lleno o `JAVA_HOME` incorrecto | Liberar espacio; `java -version` debe indicar 25. Plan B: mostrar el último resultado verde de `ArquitecturaTest` en IntelliJ (`Pruebas backend`) |
| La SPA muestra pantalla en blanco | Falló la compilación de Vite | Mirar la terminal 2; `npm ci` y `npm run dev` |
| Asistencia: «Hoy no es un día lectivo» | El backend se arrancó sin el perfil `demo` (el reloj es el real, 2026) o con una fecha de `GESEDU_DEMO_RELOJ_INICIO` en fin de semana o fuera del año 2027 | Arrancar con `local,demo`; la fecha por omisión es el lunes 15/03/2027 |
| Marcar: «No tienes clases programadas hoy» / «Aún no hay un horario publicado» | El horario 2027 no está publicado (base recién reiniciada) | Hacer el paso 12 o ejecutar `docs\demo\atajos\publicar-horario.sql` |
| Asistencia docente: el cierre sigue «aún no procesó» | El reloj no llegó a las 15:30 o el job no corrió todavía (corre cada minuto) | Reiniciar con el reloj a las 15:29 y esperar 1 o 2 minutos; mirar «Cierre diario» en la terminal del backend |
| El reporte responde «No hay asistencias registradas…» | Se pidió un mes sin datos (la semilla cubre marzo de 2027) | Elegir marzo de 2027 |

**Plan B general:** si la SPA falla, el recorrido se puede hacer desde Swagger (paso 2: horarios, auditoría y matrícula tienen sus grupos) y las terminales (pasos 3, 6 y 10).

### Reiniciar entre ensayos
Solo desbloquear la cuenta de `bloqueo@` (desde **Usuarios → Desbloquear**, o como dueño del esquema):
```powershell
psql-duenio -c "UPDATE seguridad.usuario SET intentos_fallidos = 0, bloqueado_hasta = NULL WHERE correo = 'bloqueo@demo.gesedu.pe';"
```
Esto **no** deshace las matrículas confirmadas, las clases guardadas, los comprobantes y boletas del paso 11 ni los registros de auditoría. Para volver al estado inicial por completo (8 solicitudes pendientes, 3° A con 4 vacantes, 2° B con 1, Tomás Torres con sus 11 compromisos pendientes, contraseñas de la semilla): detener el backend, ejecutar `.\scripts\reiniciar-bd.ps1`, y arrancar el backend con `local,demo`. **El reloj de la demo** vuelve al lunes 15/03/2027 a las 7:40 cada vez que se arranca el backend con el perfil `demo`; para ensayar el cierre del paso 13 se arranca con `GESEDU_DEMO_RELOJ_INICIO='2027-03-15T15:29:00-05:00'` (una variable de entorno que vale solo en esa terminal). Si tras reiniciar la base no pasaste por el paso 12, publica el horario con `docs\demo\atajos\publicar-horario.sql` antes del paso 13. Mailpit conserva sus correos hasta que se borre desde su pantalla (*Delete all*) o se reinicie con `docker compose restart mailpit`.
