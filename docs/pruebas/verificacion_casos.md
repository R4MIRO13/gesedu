# Verificación de los casos de prueba (v1_CasosPrueba_Grupo02.docx)

Para cada prueba: confirmar que la clase indicada en «Verificado por» existe y que **realmente** ejecuta el escenario descrito y comprueba el resultado esperado. Marcar con ✅ (correcto), ✏️ (corregir: indicar el texto correcto) o ❌ (no existe una prueba que lo cubra).

## UC-01: Solicitar matrícula
### Prueba 1 (unitaria)
- Objetivo: Verificar las reglas de dominio de la solicitud: DNI de 8 dígitos, documentos exigidos según el tipo de estudiante y archivos PDF/JPG/PNG de hasta 5 MB.
- Clases asociadas: ReglasMatricula, ReglasArchivo, SolicitudApoderadoService
- Descripción: Evaluar las reglas con un DNI de 7 y de 8 dígitos, un estudiante nuevo sin certificado de estudios y archivos de 4,9 MB, 5 MB y 5,1 MB en formatos permitidos y no permitidos.
- Esperado: Solo se aceptan el DNI de 8 dígitos y los archivos permitidos de hasta 5 MB; cada incumplimiento devuelve el campo y el motivo.
- Verificado por: `ReglasMatriculaTest, ReglasYDiscoTest`
- Resultado de la revisión: ✏️ `ReglasMatriculaTest` no prueba ni el DNI ni los archivos, y `ReglasYDiscoTest` no usa 4,9 / 5,1 MB. Lo que hay: `ReglasYDiscoTest.soloSePermitenPdfJpgYPngDeHasta5MB` (acepta PDF, JPG y PNG; con exactamente 5 MB —`TAMANO_MAXIMO_BYTES`— pasa, con +1 byte, 0 bytes, `text/html` y tipo nulo da `ARCHIVO_INVALIDO`); el DNI de 8 dígitos se prueba en `SolicitudApoderadoIT.fa2UnDocumentoConFormatoInvalidoSeRechaza` (DNI «12AB», campo `alumno.documento`, mensaje «8 dígitos»; no hay caso de 7 dígitos); el certificado de estudios exigido al nuevo ingreso, en `SolicitudApoderadoIT.fa1FaltanDocumentosObligatoriosSeResaltanYNoSeGuardaNada`. Texto corregido de la descripción: «DNI inválido («12AB»), archivos de exactamente 5 MB y de 5 MB + 1 byte, tipo no permitido, y nuevo ingreso sin certificado».

### Prueba 2 (caso de uso)
- Objetivo: Probar el flujo básico: registrar una solicitud de matrícula completa.
- Precondición: Apoderado autenticado; año escolar no cerrado con costos configurados y vacantes en el grado elegido.
- Descripción: Completar los datos del estudiante y del apoderado, subir los documentos exigidos con la URL prefirmada y enviar la solicitud.
- Esperado: Se registra la solicitud en estado PENDIENTE_REVISION con un código MAT-AAAA-NNNNN, se muestra la constancia y el evento queda auditado.
- Verificado por: `SolicitudApoderadoIT · SPA: solicitud.test`
- Resultado de la revisión: ✅

### Prueba 3 (caso de uso)
- Objetivo: Probar el flujo alternativo FA-1 «datos o documentos incompletos».
- Precondición: Apoderado autenticado.
- Descripción: Enviar la solicitud sin el nombre del estudiante y sin el certificado de estudios.
- Esperado: Respuesta 400 con errores[] por campo (alumno.nombres, documentos.CERTIFICADO_ESTUDIOS); la pantalla indica «Corrige los N campos marcados en rojo» y no se guarda nada.
- Verificado por: `SolicitudApoderadoIT · SPA: solicitud.test`
- Resultado de la revisión: ✏️ `SolicitudApoderadoIT` lo prueba en dos tests: `fa1FaltanCamposObligatoriosYElErrorNombraCadaCampo` (sin `nombres` y sin `fechaNacimiento` → 400 con `alumno.nombres` y `alumno.fechaNacimiento`) y `fa1FaltanDocumentosObligatoriosSeResaltanYNoSeGuardaNada` (sin certificado → 400 `VALIDACION_INVALIDA`, campo `documentos.CERTIFICADO_ESTUDIOS`, y no se guarda nada). La pantalla (`solicitud.test` «FA-1…») dice «No se pudo enviar la solicitud. Corrige los N campos marcados en rojo.» (singular: «el campo marcado»).

### Prueba 4 (caso de uso)
- Objetivo: Probar el flujo alternativo FA-2 «solicitud duplicada o DNI ya registrado».
- Precondición: Existe una solicitud en trámite para el mismo DNI y año, o el DNI pertenece al hijo de otro apoderado.
- Descripción: Enviar una segunda solicitud con el mismo DNI; repetir con el DNI de un estudiante de otra familia.
- Esperado: 409 SOLICITUD_EN_TRAMITE («Ya existe una solicitud en trámite con este DNI para 2027») y 409 DOCUMENTO_DUPLICADO, respectivamente.
- Verificado por: `SolicitudApoderadoIT`
- Resultado de la revisión: ✅

### Prueba 5 (caso de uso)
- Objetivo: Verificar que envíos simultáneos de la misma solicitud no la dupliquen.
- Precondición: Apoderado autenticado.
- Descripción: Enviar cuatro veces la misma solicitud en paralelo.
- Esperado: Se registra una sola solicitud; las demás peticiones reciben 409.
- Verificado por: `SolicitudApoderadoIT`
- Resultado de la revisión: ✅

## UC-02: Registrar matrícula
### Prueba 6 (unitaria)
- Objetivo: Verificar la generación del cronograma de obligaciones y las reglas de confirmación.
- Clases asociadas: GeneradorObligaciones, PlanDeCobros, ReglasMatricula
- Descripción: Generar las obligaciones de una matrícula y evaluar las reglas de confirmación con solicitudes en distintos estados.
- Esperado: Se generan 11 obligaciones con montos exactos de dos decimales y sus vencimientos; solo se confirma una solicitud pendiente.
- Verificado por: `GeneradorObligacionesTest, ReglasMatriculaTest`
- Resultado de la revisión: ✏️ `GeneradorObligacionesTest.generaUnaObligacionPorLineaConSusMontosExactos` comprueba 11 obligaciones de 350.00 (total 3850.00, primera MATRICULA, última `2027-12`), pero no asserta las fechas de vencimiento; la cuota de ingreso hace 12 en `laCuotaDeIngresoSoloSeCobraAlNuevoIngreso`. `ReglasMatriculaTest` prueba las validaciones (recurrente al día, admitido, matrícula activa, deuda, documentación), no el estado de la solicitud: «solo se confirma una solicitud pendiente» lo prueba `ConfirmacionMatriculaIT.unaSolicitudYaEvaluadaNoSeConfirmaDeNuevo` (409).

### Prueba 7 (caso de uso)
- Objetivo: Probar el flujo básico: confirmar una matrícula.
- Precondición: Administrador autenticado; solicitud pendiente y sección con vacantes.
- Descripción: Revisar la solicitud, elegir una sección con vacantes y confirmar.
- Esperado: En una sola transacción se descuenta la vacante, se crea la matrícula, se generan las 11 obligaciones y la solicitud pasa a APROBADA; el correo de confirmación sale después del commit.
- Verificado por: `ConfirmacionMatriculaIT · SPA: ConfirmarMatricula.test`
- Resultado de la revisión: ✅

### Prueba 8 (caso de uso)
- Objetivo: Probar los flujos alternativos FA-1 a FA-5 de la ERS, incluidos el rechazo de la solicitud y la sección sin vacantes.
- Precondición: Administrador autenticado; solicitudes preparadas para cada flujo alternativo.
- Descripción: Ejecutar cada flujo alternativo: rechazar una solicitud, confirmar en una sección sin vacantes y confirmar una solicitud modificada por otro usuario (If-Match), entre otros.
- Esperado: Cada flujo responde con su código de error sin cambiar nada (la sección sin vacantes responde 409 SIN_VACANTE); el rechazo envía el correo correspondiente.
- Verificado por: `ConfirmacionMatriculaIT`
- Resultado de la revisión: ✅

### Prueba 9 (caso de uso)
- Objetivo: Verificar RNF-09: dos apoderados no pueden ocupar la última vacante.
- Precondición: Sección con una sola vacante.
- Descripción: Confirmar 20 matrículas en paralelo contra la misma vacante; repetir 3 veces.
- Esperado: Solo una confirmación tiene éxito y la vacante nunca queda negativa.
- Verificado por: `VacantesConcurrentesIT`
- Resultado de la revisión: ✅

### Prueba 10 (caso de uso)
- Objetivo: Verificar RNF-10: un error durante la confirmación revierte todo.
- Precondición: Solicitud pendiente.
- Descripción: Forzar un fallo después de crear la matrícula.
- Esperado: No queda matrícula, vacante descontada, obligaciones ni correo enviado.
- Verificado por: `ConfirmacionMatriculaIT`
- Resultado de la revisión: ✅

## UC-03: Consultar matrícula y obligaciones de pago
### Prueba 11 (unitaria)
- Objetivo: Verificar el cálculo del total a pagar (base + penalidad) y del resumen de deuda.
- Clases asociadas: ConsultaObligacionesService, Dinero
- Descripción: Calcular el total de cada obligación y el resumen con deuda vencida y próximo vencimiento.
- Esperado: Los importes usan dos decimales exactos y el resumen coincide con la suma de las obligaciones.
- Verificado por: `ConsultaObligacionesIT, NucleoCompartidoTest`
- Resultado de la revisión: ✅

### Prueba 12 (caso de uso)
- Objetivo: Probar el flujo básico: el apoderado consulta la matrícula y las obligaciones de su hijo.
- Precondición: Apoderado con un hijo matriculado.
- Descripción: Abrir «Matrícula y pagos» y seleccionar al estudiante.
- Esperado: Se muestran la matrícula vigente, la deuda vencida, el próximo vencimiento y la tabla «Importe base + penalidad = total».
- Verificado por: `ConsultaObligacionesIT · SPA: pagos.test`
- Resultado de la revisión: ✅

### Prueba 13 (caso de uso)
- Objetivo: Probar los flujos alternativos FA-1 «sin obligaciones» y FA-2 «sin matrícula vigente».
- Precondición: Alumno sin obligaciones; alumno sin matrícula vigente.
- Descripción: Consultar a cada alumno.
- Esperado: Lista vacía de obligaciones y matrícula nula, con el mensaje correspondiente.
- Verificado por: `ConsultaObligacionesIT`
- Resultado de la revisión: ✅

### Prueba 14 (caso de uso)
- Objetivo: Verificar el alcance «mis hijos».
- Precondición: Apoderado autenticado.
- Descripción: Consultar las obligaciones de un alumno que no es su hijo.
- Esperado: 403 FUERA_DE_ALCANCE.
- Verificado por: `ConsultaObligacionesIT`
- Resultado de la revisión: ✅

## UC-04: Presentar comprobante de pago
### Prueba 15 (unitaria)
- Objetivo: Verificar las validaciones del comprobante: fecha no futura, monto mayor que cero y voucher permitido.
- Clases asociadas: PresentacionComprobanteService, ReglasArchivo
- Descripción: Presentar comprobantes con fecha de mañana, monto 0 y un archivo .exe.
- Esperado: Cada caso devuelve 400 con el campo y el motivo.
- Verificado por: `PresentacionComprobanteIT`
- Resultado de la revisión: ✏️ No hay prueba unitaria; `PresentacionComprobanteIT` cubre cada caso, pero no todos con 400: fecha futura y monto 0 (`laFechaDePagoNoPuedeSerFuturaNiElMontoCero`) solo comprueban el mensaje («La fecha de pago no puede ser futura», «mayor que cero»), sin el código HTTP; el archivo `.exe` (`elVoucherDebeHaberSidoSubidoPorElMismoApoderadoYSerUnArchivoValido`) responde **422 `ARCHIVO_INVALIDO`**, no 400. La falta de campos sí da 400 con el campo (`fa1FaltanDatosDelComprobanteYElErrorNombraCadaCampo`).

### Prueba 16 (caso de uso)
- Objetivo: Probar el flujo básico: subir un comprobante.
- Precondición: Apoderado con una obligación pendiente.
- Descripción: Seleccionar la obligación, ingresar número de operación, fecha y monto, subir el voucher y enviar.
- Esperado: Respuesta 201; el comprobante queda PENDIENTE, la obligación no cambia y la constancia dice «pendiente de validación».
- Verificado por: `PresentacionComprobanteIT · SPA: comprobante.test`
- Resultado de la revisión: ✅

### Prueba 17 (caso de uso)
- Objetivo: Probar el flujo alternativo FA-2 «obligación ya pagada» y el comprobante pendiente.
- Precondición: Obligación pagada; obligación con un comprobante en revisión.
- Descripción: Presentar un comprobante para cada una.
- Esperado: 422 OBLIGACION_YA_PAGADA y 409 COMPROBANTE_PENDIENTE.
- Verificado por: `PresentacionComprobanteIT`
- Resultado de la revisión: ✅

### Prueba 18 (caso de uso)
- Objetivo: Verificar que un número de operación no se repita, ni con envíos simultáneos.
- Precondición: Un comprobante ya registrado con el número 123456.
- Descripción: Presentar otro comprobante con el mismo número; luego enviar 4 comprobantes en paralelo con un número nuevo.
- Esperado: 409 NUMERO_OPERACION_DUPLICADO; en paralelo solo uno queda registrado.
- Verificado por: `NumeroOperacionUnicoIT`
- Resultado de la revisión: ✅

## UC-05: Validar y registrar pago de pensión
### Prueba 19 (unitaria)
- Objetivo: Verificar el cálculo de la penalidad con redondeo HALF_UP a dos decimales.
- Clases asociadas: CalculoPenalidad, Dinero
- Descripción: Calcular la penalidad para montos y porcentajes cuyo resultado tiene más de dos decimales.
- Esperado: El resultado se redondea HALF_UP a dos decimales (RNF-11).
- Verificado por: `CalculoPenalidadTest`
- Resultado de la revisión: ✅

### Prueba 20 (caso de uso)
- Objetivo: Probar el flujo básico: validar el pago y registrar la boleta.
- Precondición: Comprobante pendiente cuyo monto es igual a base + penalidad.
- Descripción: Revisar el comprobante y elegir «Validar y registrar boleta».
- Esperado: En una transacción se emite la boleta B001-NNNNN, el comprobante pasa a APROBADO y la obligación a PAGADA; el correo sale tras el commit.
- Verificado por: `ValidacionPagoIT · SPA: validar.test`
- Resultado de la revisión: ✅

### Prueba 21 (caso de uso)
- Objetivo: Probar los flujos alternativos FA-1 «observar o rechazar» y FA-2 «monto insuficiente».
- Precondición: Comprobantes pendientes.
- Descripción: Observar uno con un texto, rechazar otro y validar uno cuyo monto es menor al total.
- Esperado: Observar y rechazar no tocan la obligación y liberan el número de operación; el monto insuficiente devuelve 422 MONTO_INSUFICIENTE y el comprobante se observa.
- Verificado por: `ValidacionPagoIT`
- Resultado de la revisión: ✏️ `ValidacionPagoIT.fa2UnImporteInsuficienteEnUnaObligacionVencidaSeObservaYElApoderadoPresentaUnoNuevo`: validar con 350.00 cuando el total es 367.50 da 422 `MONTO_INSUFICIENTE` y la obligación no cambia; el comprobante **no se observa solo**: el administrador lo observa en un paso aparte (`observacion` → OBSERVADO). `fa1UnComprobanteQueNoCorrespondeSeRechazaSinTocarLaObligacion` cubre el rechazo. Que observar o rechazar liberan el número de operación se prueba en `NumeroOperacionUnicoIT.unNumeroAprobadoSigueOcupadoPeroUnoObservadoORechazadoSeLibera`, no en `ValidacionPagoIT`.

### Prueba 22 (caso de uso)
- Objetivo: Verificar RNF-10: un fallo después de insertar la boleta revierte todo.
- Precondición: Comprobante pendiente.
- Descripción: Forzar un error en el registro del pago después de generar la boleta.
- Esperado: No queda boleta, el comprobante sigue PENDIENTE y la obligación no cambia.
- Verificado por: `ValidacionPagoIT`
- Resultado de la revisión: ✅

### Prueba 23 (caso de uso)
- Objetivo: Verificar que dos administradores no validen el mismo comprobante.
- Precondición: Comprobante pendiente.
- Descripción: Validar el mismo comprobante desde dos hilos.
- Esperado: Solo una validación tiene éxito (bloqueo SELECT … FOR UPDATE); la otra responde 409.
- Verificado por: `ValidacionPagoIT`
- Resultado de la revisión: ✏️ `ValidacionPagoIT.dosValidacionesSimultaneasDelMismoComprobanteDejanUnaSolaBoleta`: 2 hilos (dos administradores); resultado `200` y **`422 OBLIGACION_YA_PAGADA`** (la segunda espera el bloqueo y encuentra la obligación pagada), no 409; queda una sola boleta y la obligación PAGADA. El 409 `CONFLICTO_CONCURRENCIA` aparece al revisar un comprobante ya revisado (`unComprobanteYaRevisadoNoSeVuelveARevisar`).

## UC-06: Consultar historial de pagos
### Prueba 24 (unitaria)
- Objetivo: Verificar el resumen del historial: total pagado, pendiente y vencido con penalidades.
- Clases asociadas: HistorialFinancieroService, MatriculasDeAlumnosAdaptador
- Descripción: Calcular el resumen del historial.
- Esperado: Los totales coinciden con las obligaciones y pagos registrados.
- Verificado por: `HistorialFinancieroIT`
- Resultado de la revisión: ✅

### Prueba 25 (caso de uso)
- Objetivo: Probar el flujo básico: buscar un alumno y ver su historial.
- Precondición: Administrador autenticado.
- Descripción: Buscar por nombre, DNI o código y abrir el historial.
- Esperado: Se muestran las pestañas Obligaciones, Boletas y pagos, y Deudas y penalidades.
- Verificado por: `HistorialFinancieroIT · SPA: historial.test`
- Resultado de la revisión: ✏️ `HistorialFinancieroIT.laBusquedaEncuentraEstudiantesPorNombreOCodigo` busca por nombre y por código del alumno; no hay caso de búsqueda por DNI. Las pestañas Obligaciones / Boletas y pagos / Deudas y penalidades las comprueba `historial.test` (la API devuelve los tres bloques en `flujoBasicoMuestraResumenObligacionesBoletasYDeudasConSuPenalidad`).

### Prueba 26 (caso de uso)
- Objetivo: Probar los flujos alternativos FA-1 «sin historial» y FA-2 «sin pagos».
- Precondición: Alumno sin obligaciones; alumno sin pagos.
- Descripción: Consultar a cada alumno.
- Esperado: La respuesta indica sinHistorial o sinPagos y la pantalla muestra el mensaje.
- Verificado por: `HistorialFinancieroIT`
- Resultado de la revisión: ✅

### Prueba 27 (caso de uso)
- Objetivo: Verificar que la consulta sea de solo lectura.
- Precondición: Datos del alumno cargados.
- Descripción: Comparar conteos y fechas de modificación antes y después de consultar.
- Esperado: Ningún registro cambia.
- Verificado por: `HistorialFinancieroIT`
- Resultado de la revisión: ✅

## UC-07: Registrar asistencia de docente
### Prueba 28 (unitaria)
- Objetivo: Verificar la clasificación PUNTUAL/TARDANZA con el límite inclusivo.
- Clases asociadas: ClasificadorAsistenciaDocente, AsistenciaProperties
- Descripción: Clasificar marcaciones antes del límite, exactamente en el límite (inicio de la primera clase del docente + 5 min) y un minuto después.
- Esperado: Antes y en el límite: PUNTUAL (límite inclusivo); después: TARDANZA.
- Verificado por: `ClasificadorAsistenciaDocenteTest, AsistenciaPropertiesTest`
- Resultado de la revisión: ✏️ `ClasificadorAsistenciaDocenteTest`: con inicio 07:45 y tolerancia 5 min, 07:44:59 y 06:30 → PUNTUAL; 07:50:00 (límite) → PUNTUAL (`elLimiteEsInclusivo`); **07:50:01** (un segundo después, no un minuto) y 08:30 → TARDANZA (`unSegundoDespuesDelLimiteEsTardanza`). `AsistenciaPropertiesTest` solo valida las propiedades (cierre, umbral y zona horaria).

### Prueba 29 (caso de uso)
- Objetivo: Probar el flujo básico: el docente marca su ingreso.
- Precondición: Docente con clases hoy en el horario publicado; día lectivo y jornada abierta.
- Descripción: Abrir «Mi asistencia» y presionar «Registrar asistencia».
- Esperado: Respuesta 201 con el registro; la hora es la del servidor y el estado se calcula sobre la primera clase del docente.
- Verificado por: `MarcacionDocenteIT · SPA: miAsistencia.test`
- Resultado de la revisión: ✅

### Prueba 30 (caso de uso)
- Objetivo: Probar el flujo alternativo FA-1 «asistencia ya registrada».
- Precondición: El docente ya marcó hoy.
- Descripción: Volver a marcar.
- Esperado: 409 ASISTENCIA_YA_REGISTRADA con el registro existente; no se duplica.
- Verificado por: `MarcacionDocenteIT`
- Resultado de la revisión: ✅

### Prueba 31 (caso de uso)
- Objetivo: Probar los flujos alternativos FA-2 «sin clases» y FA-3 «día no lectivo o jornada cerrada».
- Precondición: Docente sin clases hoy; sábado; hora posterior al cierre.
- Descripción: Intentar marcar en cada situación.
- Esperado: 422 SIN_CLASES_PROGRAMADAS, 422 DIA_NO_LECTIVO y 422 JORNADA_CERRADA.
- Verificado por: `MarcacionDocenteIT`
- Resultado de la revisión: ✅

### Prueba 32 (caso de uso)
- Objetivo: Verificar que marcaciones simultáneas no dupliquen el registro.
- Precondición: Docente con clases hoy.
- Descripción: Enviar 8 marcaciones en paralelo.
- Esperado: Un solo registro (201) y siete respuestas 409.
- Verificado por: `MarcacionDocenteIT`
- Resultado de la revisión: ✅

## UC-08: Registrar asistencia de alumnos
### Prueba 33 (unitaria)
- Objetivo: Verificar las validaciones del lote de asistencia.
- Clases asociadas: AsistenciaSeccionService
- Descripción: Enviar lotes con un estado inválido, un alumno repetido y un alumno de otra sección.
- Esperado: 400 con errores[] y no se guarda ningún registro.
- Verificado por: `PaseListaConcurrenteIT`
- Resultado de la revisión: ✅

### Prueba 34 (caso de uso)
- Objetivo: Probar el flujo básico: el tutor registra el pase de lista.
- Precondición: Tutor autenticado; día lectivo y jornada abierta.
- Descripción: Marcar a cada alumno como Puntual, Tardanza o Falta y guardar.
- Esperado: Respuesta 201; el lote se guarda completo con la fecha y hora del servidor.
- Verificado por: `PaseListaConcurrenteIT · SPA: registrarAsistencia.test`
- Resultado de la revisión: ✅

### Prueba 35 (caso de uso)
- Objetivo: Probar el flujo alternativo FA-1 «faltan alumnos en el lote».
- Precondición: Tutor autenticado.
- Descripción: Guardar sin marcar a dos alumnos.
- Esperado: 400 con errores[] por alumno; no se guarda nada (lote atómico).
- Verificado por: `PaseListaConcurrenteIT · SPA: registrarAsistencia.test`
- Resultado de la revisión: ✅

### Prueba 36 (caso de uso)
- Objetivo: Probar los flujos alternativos FA-2/FA-5 «registro ya existente», FA-3 «sin tutoría o sin alumnos» y FA-4 «día no lectivo».
- Precondición: Lote ya registrado por otra solicitud; docente sin tutoría; sección vacía; sábado.
- Descripción: Repetir el registro del lote y consultar en cada situación.
- Esperado: 409 ASISTENCIA_YA_REGISTRADA sin sobrescribir; 403 FUERA_DE_ALCANCE; 422 SECCION_SIN_ALUMNOS; 422 por día no lectivo.
- Verificado por: `PaseListaConcurrenteIT`
- Resultado de la revisión: ✅

## UC-09: Actualizar estado de asistencia
### Prueba 37 (unitaria)
- Objetivo: Verificar el plazo para cambiar FALTA a TARDANZA con el límite inclusivo.
- Clases asociadas: PlazoTardanza
- Descripción: Evaluar una hora anterior al corte, la hora exacta del corte y una posterior.
- Esperado: Antes y en el corte se admite el cambio (límite inclusivo); después no.
- Verificado por: `PlazoTardanzaTest`
- Resultado de la revisión: ✅

### Prueba 38 (caso de uso)
- Objetivo: Probar el flujo básico: cambiar una falta a tardanza.
- Precondición: Alumno de la sección del tutor marcado hoy como FALTA; hora dentro del plazo.
- Descripción: En «Registro del día», elegir «Cambiar a Tardanza».
- Esperado: El mismo registro pasa a TARDANZA con la hora del servidor y el cambio queda auditado.
- Verificado por: `ActualizacionAsistenciaIT · SPA: registroDelDia.test`
- Resultado de la revisión: ✅

### Prueba 39 (caso de uso)
- Objetivo: Probar los flujos alternativos FA-1 «plazo excedido», FA-2 «registro no elegible» y FA-3 «fuera de alcance».
- Precondición: Hora posterior al corte; registro PUNTUAL o de otro día; registro de otra sección.
- Descripción: Intentar el cambio en cada situación.
- Esperado: 422 PLAZO_TARDANZA_EXCEDIDO; 400 SOLICITUD_INVALIDA; 403 FUERA_DE_ALCANCE.
- Verificado por: `ActualizacionAsistenciaIT`
- Resultado de la revisión: ✅

### Prueba 40 (caso de uso)
- Objetivo: Verificar que cambios simultáneos se apliquen una sola vez.
- Precondición: Alumno en FALTA.
- Descripción: Enviar 6 cambios en paralelo.
- Esperado: Un solo cambio con modificado = true y un solo registro de auditoría.
- Verificado por: `ActualizacionAsistenciaIT`
- Resultado de la revisión: ✅

## UC-10: Exportar reportes de asistencia
### Prueba 41 (unitaria)
- Objetivo: Verificar la generación de los archivos Excel y PDF del reporte.
- Clases asociadas: GeneradorExcel, GeneradorPdf
- Descripción: Generar el reporte en ambos formatos.
- Esperado: Los archivos se generan con las columnas, la zona horaria institucional y los porcentajes esperados.
- Verificado por: `GeneradorExcelTest, GeneradorPdfTest`
- Resultado de la revisión: ✅

### Prueba 42 (caso de uso)
- Objetivo: Probar el flujo básico: el tutor exporta el reporte mensual de su sección.
- Precondición: Tutor autenticado; registros de asistencia en el mes.
- Descripción: Elegir mes y formato (Excel o PDF) y descargar.
- Esperado: Se descarga el archivo con los conteos y porcentajes calculados al momento; no se guarda ningún consolidado.
- Verificado por: `ReporteAsistenciaIT · SPA: reportesAsistencia.test`
- Resultado de la revisión: ✅

### Prueba 43 (caso de uso)
- Objetivo: Probar los flujos alternativos: mes en curso, sin registros y periodo futuro.
- Precondición: Mes actual con jornada abierta; mes sin asistencias; mes siguiente.
- Descripción: Pedir el reporte de cada periodo.
- Esperado: Reporte parcial con fecha de corte; 422 SIN_REGISTROS_ASISTENCIA; 422 PERIODO_INVALIDO.
- Verificado por: `ReporteAsistenciaIT`
- Resultado de la revisión: ✅

### Prueba 44 (caso de uso)
- Objetivo: Verificar el alcance del tutor y el reporte de meses pasados.
- Precondición: Tutor de 3° A; alumno trasladado a mitad de mes; dos versiones del horario.
- Descripción: Pedir el reporte de otra sección y el de un mes pasado.
- Esperado: 403 FUERA_DE_ALCANCE para otra sección; en el mes pasado cada día usa la sección del alumno y la versión del horario vigentes en esa fecha.
- Verificado por: `ReporteAsistenciaIT, ReporteMesesPasadosIT`
- Resultado de la revisión: ✅

## UC-11: Calcular resumen mensual de asistencia
### Prueba 45 (unitaria)
- Objetivo: Verificar la fórmula del porcentaje mensual.
- Clases asociadas: CalculoResumenMensual
- Descripción: Calcular con conteos de Puntual (P), Tardanza (T), Falta (F) y días sin registro; repetir sin ningún registro.
- Esperado: Porcentaje = 100 × (P + T) / (P + T + F) con dos decimales HALF_UP (por ejemplo, P = 15, T = 3, F = 2 da 90,00 %); los días sin registro no entran en el denominador; sin registros el porcentaje es «No disponible».
- Verificado por: `CalculoResumenMensualTest`
- Resultado de la revisión: ✏️ `CalculoResumenMensualTest` usa otros datos: `laTardanzaCuentaComoPresencia` (18 P + 2 T + 1 F → 95.24; 0 P + 5 T → 100.00), `redondeaAlCentesimoConHalfUp` (66.67, 12.50, 6.25, 3.13), `unRegistroFueraDeLosDiasEsperados…` y el caso de 13 registrados / 7 no registrados → 92.31. El ejemplo P = 15, T = 3, F = 2 → 90,00 % no figura; «No disponible» (porcentaje nulo, no 0 %) está en `sinDiasRegistradosNoSeDivideNiSeInformaCero`.

### Prueba 46 (caso de uso)
- Objetivo: Probar el resumen de un alumno en su inicio.
- Precondición: Alumno con registros en el mes.
- Descripción: Abrir el inicio del alumno.
- Esperado: La tarjeta «Mi asistencia» muestra porcentaje, conteos y días sin registro, que «no se cuentan como faltas».
- Verificado por: `ResumenAsistenciaAlumnoIT · SPA: inicioAlumno.test`
- Resultado de la revisión: ✅

### Prueba 47 (caso de uso)
- Objetivo: Verificar que el reporte general no cuente dos veces a un alumno trasladado.
- Precondición: Alumno con cambio de sección en el mes.
- Descripción: Pedir el reporte general del mes.
- Esperado: Cada día del alumno se cuenta una sola vez, en la sección que tenía ese día.
- Verificado por: `ReporteMesesPasadosIT`
- Resultado de la revisión: ✅

## UC-12: Registrar faltas de docentes (cierre diario)
### Prueba 48 (unitaria)
- Objetivo: Verificar la precondición de hora del cierre diario.
- Clases asociadas: CierreDiarioService, AsistenciaProperties
- Descripción: Ejecutar el cierre antes y después de SchoolDayClosingTime.
- Esperado: Antes de la hora de cierre no hace nada; después procesa la fecha.
- Verificado por: `CierreDiarioIT`
- Resultado de la revisión: ✅

### Prueba 49 (caso de uso)
- Objetivo: Probar el flujo básico: el cierre registra FALTA a los docentes que no marcaron.
- Precondición: Día lectivo con horario publicado; un docente con clases no marcó.
- Descripción: Ejecutar el job de cierre diario.
- Esperado: Se crea FALTA sin hora para ese docente; las marcaciones existentes no se tocan; queda auditado como «Sistema».
- Verificado por: `CierreDiarioIT · SPA: asistenciaDocentes.test`
- Resultado de la revisión: ✅

### Prueba 50 (caso de uso)
- Objetivo: Verificar la idempotencia y la ejecución en varias instancias.
- Precondición: Cierre ya ejecutado para la fecha.
- Descripción: Ejecutar el cierre dos veces y en paralelo desde dos instancias simuladas (ShedLock).
- Esperado: No se duplican faltas; la segunda ejecución queda como YA_PROCESADO.
- Verificado por: `CierreDiarioIT, ShedLockIT`
- Resultado de la revisión: ✅

### Prueba 51 (caso de uso)
- Objetivo: Probar los flujos alternativos FA-1 «día sin clases» y FA-4 «sin año activo o sin horario».
- Precondición: Fecha fuera de los bimestres; sistema sin horario publicado.
- Descripción: Ejecutar el cierre.
- Esperado: FA-1: cierre EXITOSO sin registros. FA-4: cierre FALLIDO con el motivo, sin inventar faltas, y se reintenta en el siguiente disparo.
- Verificado por: `CierreDiarioIT`
- Resultado de la revisión: ✅

### Prueba 52 (caso de uso)
- Objetivo: Verificar que el cierre solo afecte a docentes.
- Precondición: Alumnos sin registro en el día.
- Descripción: Ejecutar el cierre.
- Esperado: No se crean registros de alumnos (la ausencia de registro no es falta).
- Verificado por: `CierreDiarioIT`
- Resultado de la revisión: ✅

## UC-13: Creación del año escolar
### Prueba 53 (unitaria)
- Objetivo: Verificar las reglas del año escolar: duración de 10 a 12 meses y bimestres sin cruces y en orden.
- Clases asociadas: ValidadorAnioEscolar
- Descripción: Validar un año de 9 meses, uno de 10 meses y 5 días y bimestres superpuestos.
- Esperado: Se rechazan el rango inválido y el cruce, con el tramo en conflicto; el año válido muestra su duración.
- Verificado por: `ValidadorAnioEscolarTest`
- Resultado de la revisión: ✅

### Prueba 54 (caso de uso)
- Objetivo: Probar el flujo básico: crear el año escolar con sus bimestres.
- Precondición: Administrador autenticado.
- Descripción: Ingresar fechas del año y de los 4 bimestres, revisar la línea de tiempo y guardar.
- Esperado: Se crea el año PLANIFICADO con 4 bimestres y los grados 1° a 5°; queda auditado.
- Verificado por: `AnioEscolarIT · SPA: anioNuevo.test`
- Resultado de la revisión: ✅

### Prueba 55 (caso de uso)
- Objetivo: Probar los flujos alternativos FA-1 «rango inválido», FA-2 «bimestres superpuestos» y el año duplicado.
- Precondición: Administrador autenticado; año 2027 ya existe.
- Descripción: Guardar un año de 9 meses, otro con bimestres cruzados y otro 2027 (también 4 altas simultáneas).
- Esperado: 422 RANGO_ANIO_INVALIDO; 422 BIMESTRES_SUPERPUESTOS; 409 ANIO_ESCOLAR_DUPLICADO con una sola alta exitosa.
- Verificado por: `AnioEscolarIT`
- Resultado de la revisión: ✅

### Prueba 56 (caso de uso)
- Objetivo: Probar la activación del año escolar (extensión de UC-13, C-47).
- Precondición: Año 2028 PLANIFICADO y 2027 ACTIVO.
- Descripción: Elegir «Activar 2028»; repetir con dos activaciones simultáneas.
- Esperado: En una transacción se cierra 2027 y se activa 2028; con activaciones simultáneas queda un solo año activo y la otra recibe 409.
- Verificado por: `ActivacionAnioIT · SPA: anios.test, anioActivo.test`
- Resultado de la revisión: ✏️ `ActivacionAnioIT.activarUnAnioPlanificadoCierraElActivoAnteriorYQuedaAuditado` prueba la activación en una transacción. En `dosActivacionesSimultaneasNuncaDejanDosAniosActivos` son 2 hilos y la prueba exige que al menos una respuesta sea 200 y que ninguna sea distinta de 200 o 409 (no obliga a que la otra sea 409); lo que garantiza es un solo año ACTIVO. SPA: `anios.test` («el directivo activa un año planificado tras confirmar») y `anioActivo.test`.

## UC-14: Configuración de cursos y competencias
### Prueba 57 (unitaria)
- Objetivo: Verificar las reglas del curso: slug sin tildes, códigos de competencia C1, C2… sin reutilizar y horas de 1 a 40.
- Clases asociadas: ReglasCurso
- Descripción: Crear cursos con tildes en el nombre, 0 y 41 horas, y quitar y volver a agregar competencias.
- Esperado: El slug se normaliza, las horas fuera de rango se rechazan y los códigos no se reutilizan.
- Verificado por: `ReglasCursoTest`
- Resultado de la revisión: ✅

### Prueba 58 (caso de uso)
- Objetivo: Probar el flujo básico: crear un curso con sus competencias.
- Precondición: Año no cerrado.
- Descripción: Elegir «+ Nuevo curso», ingresar nombre, horas y competencias y guardar.
- Esperado: El curso se crea con sus competencias y el total de horas se actualiza.
- Verificado por: `CursosIT · SPA: cursos.test`
- Resultado de la revisión: ✅

### Prueba 59 (caso de uso)
- Objetivo: Probar el flujo alternativo FA-1 «copiar la configuración del año anterior».
- Precondición: Año 2026 con cursos; año 2027 sin parte de ellos.
- Descripción: Elegir «Copiar configuración de 2026» dos veces.
- Esperado: Se copian solo los cursos que faltan; repetir la copia no duplica nada.
- Verificado por: `CursosIT · SPA: cursos.test`
- Resultado de la revisión: ✅

### Prueba 60 (caso de uso)
- Objetivo: Probar curso duplicado, curso en uso y año cerrado.
- Precondición: Curso existente; curso con clases o logros; año cerrado.
- Descripción: Crear un curso con el mismo nombre, eliminar un curso en uso y editar en un año cerrado.
- Esperado: 409 CURSO_DUPLICADO; 409 CURSO_EN_USO; 422 ANIO_CERRADO.
- Verificado por: `CursosIT`
- Resultado de la revisión: ✏️ `CursosIT`: `unNombreRepetidoOSuVersionSinTildesEs409` (409 `CURSO_DUPLICADO`); `unCursoSinUsoSeEliminaYUnoConCargaHorariaNo` (409 `CURSO_EN_USO`, pero «en uso» se prueba solo con una carga horaria del grado en `horarios.carga_horaria_curso`; no hay caso con clases asignadas ni con logros); `unAnioCerradoNoSeConfigura` (crear, editar y eliminar → 422 `ANIO_CERRADO`).

## UC-15: Registrar evaluación por competencias
### Prueba 61 (unitaria)
- Objetivo: Verificar la conversión y validación de los niveles de logro (AD, A, B, C).
- Clases asociadas: NivelLogro
- Descripción: Convertir «AD», «A», «B», «C», «ad» y «D».
- Esperado: Solo se aceptan los cuatro niveles exactos; minúsculas y otros valores se rechazan.
- Verificado por: `NivelLogroTest`
- Resultado de la revisión: ✏️ `NivelLogroTest` no convierte texto: prueba `valor()` de AD/A/B/C (4 a 1), `desdeValor` (5 lanza excepción) y `desdePromedio` (1.49→C … 3.50→AD). El rechazo de «ad», «a», «AD », «E», «5», vacío y nulo está en `RegistroLogrosIT.fa1UnNivelFueraDeLaEscalaSeRechazaYNoSeGuardaNada` (422 `NIVEL_LOGRO_INVALIDO`, mensaje «AD, A, B o C»); «D» concreto no se prueba (sí «E»).

### Prueba 62 (caso de uso)
- Objetivo: Probar el flujo básico: registrar los logros del bimestre vigente.
- Precondición: Docente con la asignación del curso y sección; bimestre vigente.
- Descripción: Abrir «Registro de notas», calificar a todos los alumnos en cada competencia y guardar.
- Esperado: Se guardan los logros (un logro por alumno, competencia y bimestre) y cada cambio queda auditado con el valor anterior y el nuevo.
- Verificado por: `RegistroLogrosIT · SPA: registroNotas.test`
- Resultado de la revisión: ✅

### Prueba 63 (caso de uso)
- Objetivo: Probar los flujos alternativos FA-1 «nivel inválido» y FA-2 «registro incompleto».
- Precondición: Docente autenticado.
- Descripción: Guardar una celda con «D»; guardar dejando dos alumnos sin calificar.
- Esperado: FA-1: 422 NIVEL_LOGRO_INVALIDO y no se guarda nada. FA-2: se guarda y se avisa «Hay 2 alumnos con competencias sin calificar».
- Verificado por: `RegistroLogrosIT · SPA: registroNotas.test`
- Resultado de la revisión: ✅

### Prueba 64 (caso de uso)
- Objetivo: Verificar el bimestre vigente, el alcance y la corrección después de consolidar.
- Precondición: Bimestre pasado; asignación de otro docente; bimestre vigente ya consolidado.
- Descripción: Editar en cada situación.
- Esperado: 422 BIMESTRE_NO_VIGENTE; 403 FUERA_DE_ALCANCE; en el bimestre consolidado se guarda y la consolidación queda DESACTUALIZADA.
- Verificado por: `RegistroLogrosIT`
- Resultado de la revisión: ✅

## UC-16: Definir regla de consolidación
### Prueba 65 (unitaria)
- Objetivo: Verificar las tres estrategias de consolidación (patrón Strategy, DA-09).
- Clases asociadas: PromedioPonderadoStrategy, ModaStrategy, UltimoRegistroStrategy, ConsolidacionService
- Descripción: Aplicar cada estrategia a los logros AD, AD, A, B de los bimestres I a IV; probar los límites 1,49, 1,5, 2,5 y 3,5 y pesos 1 y 3.
- Esperado: Promedio = A, moda = AD (empate al más reciente), último = B; 1,49→C, 1,5→B, 2,5→A, 3,5→AD; los pesos cambian el resultado frente al promedio simple.
- Verificado por: `PromedioPonderadoStrategyTest, ModaStrategyTest, UltimoRegistroStrategyTest, ConsolidacionServiceTest`
- Resultado de la revisión: ✏️ Los tests unitarios usan otros datos: `PromedioPonderadoStrategyTest.limitesExactos` / `justoDebajoDelLimiteNoSube` (1,5→B, 2,5→A, 3,5→AD) y `losPesosDistintosCambianElResultadoFrenteAlPromedioSimple` (pesos 1 y 3; también `NivelLogroTest.promedioAlNivelMasCercano` con 1.49→C); `ModaStrategyTest.elEmpateLoGanaElRegistroMasReciente`; `UltimoRegistroStrategyTest.tomaElDelBimestreDeMayorNumero`; `ConsolidacionServiceTest.cadaReglaUsaSuEstrategia`. El caso AD, AD, A, B (promedio A = 3,25; moda AD; último B) se prueba en `ConfiguracionReglaIT` (ejemplos calculados) y `NotasFinalesIT.cadaReglaDaSuResultadoConLosMismosLogros`, no en las clases unitarias.

### Prueba 66 (caso de uso)
- Objetivo: Probar el flujo básico: elegir la regla y los pesos de un curso.
- Precondición: Docente con la asignación.
- Descripción: Abrir «Configuración del curso», elegir una regla y ajustar los pesos por bimestre.
- Esperado: Se guarda la regla y los pesos; el ejemplo se calcula con el motor real; el cambio queda auditado.
- Verificado por: `ConfiguracionReglaIT · SPA: configuracionCurso.test`
- Resultado de la revisión: ✅

### Prueba 67 (caso de uso)
- Objetivo: Probar los flujos alternativos FA-1 «cambiar la regla» y FA-2 «regla por defecto».
- Precondición: Asignación sin regla elegida.
- Descripción: Consultar la configuración y luego cambiar la regla dos veces.
- Esperado: Sin elección rige PROMEDIO_PONDERADO; la regla puede cambiarse cuantas veces se quiera.
- Verificado por: `ConfiguracionReglaIT`
- Resultado de la revisión: ✅

### Prueba 68 (caso de uso)
- Objetivo: Verificar las validaciones de los pesos.
- Precondición: Docente autenticado.
- Descripción: Guardar un peso 0, uno con tres decimales, uno mayor que 100 y un bimestre repetido.
- Esperado: 400 VALIDACION_INVALIDA con errores[] por peso; no se guarda nada, tampoco la regla.
- Verificado por: `ConfiguracionReglaIT`
- Resultado de la revisión: ✅

## UC-17: Cálculo de notas finales
### Prueba 69 (unitaria)
- Objetivo: Verificar el cálculo de la nota anual con la regla y los pesos del curso.
- Clases asociadas: CalculadorNotaAnual, ConsolidacionService
- Descripción: Calcular la nota anual por competencia.
- Esperado: La nota anual aplica la regla del curso con los pesos de cada bimestre.
- Verificado por: `NotasFinalesIT`
- Resultado de la revisión: ✅

### Prueba 70 (caso de uso)
- Objetivo: Probar el flujo básico: calcular las notas finales de la asignación.
- Precondición: Docente con logros registrados.
- Descripción: Abrir «Notas finales» y elegir «Calcular notas finales».
- Esperado: Se guarda una nota anual por alumno y competencia; las celdas sin logros no reciben nota y se informan.
- Verificado por: `NotasFinalesIT · SPA: notasFinales.test`
- Resultado de la revisión: ✅

### Prueba 71 (caso de uso)
- Objetivo: Verificar la idempotencia del cálculo.
- Precondición: Notas finales ya calculadas.
- Descripción: Repetir el cálculo sin cambios, luego cambiar un logro y recalcular; ejecutar 8 cálculos en paralelo.
- Esperado: Sin cambios no se modifica nada ni se audita; con cambios se reemplaza la nota; en paralelo queda una nota por celda y una auditoría.
- Verificado por: `NotasFinalesIT`
- Resultado de la revisión: ✅

### Prueba 72 (caso de uso)
- Objetivo: Probar la asignación sin logros y el año cerrado.
- Precondición: Asignación sin logros; año cerrado.
- Descripción: Pedir el cálculo.
- Esperado: 422 SIN_LOGROS_REGISTRADOS y 422 ANIO_CERRADO.
- Verificado por: `NotasFinalesIT`
- Resultado de la revisión: ✅

## UC-18: Consolidar notas bimestrales del alumno
### Prueba 73 (unitaria)
- Objetivo: Verificar el cálculo del avance de registro por curso y sección.
- Clases asociadas: ConsolidacionBimestralService
- Descripción: Consultar el avance.
- Esperado: Cada curso muestra celdas con logro sobre el total y porcentaje; los incompletos aparecen primero.
- Verificado por: `ConsolidacionBimestralIT`
- Resultado de la revisión: ✅

### Prueba 74 (caso de uso)
- Objetivo: Probar el flujo básico: consolidar un bimestre completo.
- Precondición: Todos los cursos del bimestre al 100 %.
- Descripción: Abrir «Consolidar notas» y confirmar.
- Esperado: Se guardan las notas bimestrales, se crean o actualizan las libretas PARCIAL de cada alumno y la consolidación queda COMPLETADA.
- Verificado por: `ConsolidacionBimestralIT · SPA: consolidarNotas.test`
- Resultado de la revisión: ✅

### Prueba 75 (caso de uso)
- Objetivo: Probar el flujo alternativo FA-2 «registro incompleto».
- Precondición: Un curso por debajo del 100 %.
- Descripción: Intentar consolidar.
- Esperado: 422 BIMESTRE_INCOMPLETO con los cursos pendientes y sus docentes; el botón aparece deshabilitado; no cambia nada.
- Verificado por: `ConsolidacionBimestralIT · SPA: consolidarNotas.test`
- Resultado de la revisión: ✅

### Prueba 76 (caso de uso)
- Objetivo: Probar el flujo alternativo FA-1 «consolidado final» (último bimestre).
- Precondición: Bimestres I a III consolidados; luego, uno desactualizado.
- Descripción: Consolidar el bimestre IV.
- Esperado: Se calculan las notas anuales de todas las asignaciones y las libretas pasan a FINAL; con un bimestre anterior pendiente o desactualizado responde 422 BIMESTRES_ANTERIORES_PENDIENTES.
- Verificado por: `ConsolidacionBimestralIT`
- Resultado de la revisión: ✅

## UC-19: Consultar notas y libreta
### Prueba 77 (caso de uso)
- Objetivo: Verificar el contenido del PDF de la libreta.
- Clases asociadas: GeneradorPdf, ConsultaNotasService
- Precondición: Semilla de demo (V1002, V1005): Valeria Torres (2026-0404), 2° A de 2026 con cuatro bimestres consolidados y libreta FINAL actualizada el 18/12/2026 12:00.
- Descripción: Generar la libreta en PDF de Valeria, extraer el texto con PDFBox y compararlo con la base; luego agregar a 2° A un curso sin ninguna nota y generarla otra vez.
- Esperado: El PDF contiene nombre, código, «Sección: 2° A · Año escolar 2026», el estado y la fecha de actualización de `evaluacion.libreta` («Libreta final, actualizada el 18/12/2026 12:00», en la zona del reloj inyectado), cada curso y cada competencia, y en cada fila los calificativos AD/A/B/C de los bimestres I a IV y el final iguales a `evaluacion.nota_consolidada`. El curso sin notas aparece con «—» en cada calificativo, no cambia el estado ni la fecha, y el PDF se genera sin error.
- Verificado por: `LibretaPdfContenidoIT`
- Resultado de la revisión: ✅ (observación 4 de la evaluación del primer incremento, 11/11/2026). `LibretaPdfContenidoIT` (2 casos) extrae el texto con PDFBox 3.0.8 (solo pruebas) y lee los calificativos por posición, en las columnas «Bim. I» a «Final», para no confundirlos con el texto del nivel esperado. El PDF no imprime una fecha de emisión aparte: la fecha verificada es la de actualización de la libreta (RF-35 y RF-36 del Catálogo v4 no exigen fecha de emisión). Revisión anterior: ❌ Ninguna prueba verificaba el contenido del PDF de la libreta (solo 200, tipo, firma `%PDF-` y nombre del archivo).

### Prueba 78 (caso de uso)
- Objetivo: Probar el flujo básico: el alumno consulta sus notas.
- Precondición: Alumno con un bimestre consolidado.
- Descripción: Abrir «Mis notas».
- Esperado: Se muestran los cursos con docente, competencias, nivel esperado y las notas consolidadas; solo lo consolidado es visible.
- Verificado por: `ConsultaNotasIT · SPA: misNotas.test`
- Resultado de la revisión: ✅

### Prueba 79 (caso de uso)
- Objetivo: Probar los flujos alternativos FA-1 «apoderado con varios hijos» y FA-2 «libreta final».
- Precondición: Apoderado con dos hijos; año anterior con libreta FINAL.
- Descripción: Elegir a cada hijo; descargar la libreta FINAL en PDF.
- Esperado: Cada hijo muestra sus notas; la libreta final incluye la nota anual y se descarga en PDF.
- Verificado por: `ConsultaNotasIT · SPA: misNotas.test`
- Resultado de la revisión: ✅

### Prueba 80 (caso de uso)
- Objetivo: Verificar el alcance «yo» y «mis hijos».
- Precondición: Alumno y apoderado autenticados.
- Descripción: Consultar las notas de otro alumno.
- Esperado: 403 FUERA_DE_ALCANCE.
- Verificado por: `ConsultaNotasIT`
- Resultado de la revisión: ✅

## UC-20: Registrar disponibilidad docente
### Prueba 81 (unitaria)
- Objetivo: Verificar el cálculo de bloques faltantes frente a la carga de referencia.
- Clases asociadas: DisponibilidadDocenteService
- Descripción: Validar una disponibilidad con menos bloques que la carga.
- Esperado: Advertencia «Tu disponibilidad podría ser insuficiente para tu carga horaria. Faltan al menos N bloques», sin guardar.
- Verificado por: `DisponibilidadDocenteIT`
- Resultado de la revisión: ✅

### Prueba 82 (caso de uso)
- Objetivo: Probar el flujo básico: el docente confirma su disponibilidad.
- Precondición: Periodo de disponibilidad abierto; docente vinculado al año.
- Descripción: Marcar los bloques disponibles y elegir «Confirmar disponibilidad».
- Esperado: Se guarda la disponibilidad con auditoría; UC-21 la usa para ofrecer docentes.
- Verificado por: `DisponibilidadDocenteIT · SPA: disponibilidad.test`
- Resultado de la revisión: ✅

### Prueba 83 (caso de uso)
- Objetivo: Probar los flujos alternativos FA-1 «actualizar disponibilidad» y FA-2 «disponibilidad insuficiente».
- Precondición: Disponibilidad ya confirmada.
- Descripción: Cambiar bloques y confirmar; confirmar con menos bloques que la carga.
- Esperado: Se conserva solo la última versión; la advertencia no impide confirmar.
- Verificado por: `DisponibilidadDocenteIT`
- Resultado de la revisión: ✅

### Prueba 84 (caso de uso)
- Objetivo: Probar el periodo cerrado y las confirmaciones simultáneas.
- Precondición: Periodo cerrado o vencido.
- Descripción: Confirmar; enviar 6 confirmaciones en paralelo con el periodo abierto.
- Esperado: 422 PERIODO_DISPONIBILIDAD_CERRADO y la pantalla en solo lectura; en paralelo queda una sola disponibilidad coherente.
- Verificado por: `DisponibilidadDocenteIT · SPA: disponibilidad.test`
- Resultado de la revisión: ✅

## UC-21: Elaborar horario
### Prueba 85 (unitaria)
- Objetivo: Verificar las reglas de cruce del horario (RF-41, RF-47).
- Clases asociadas: ValidadorConflictos
- Descripción: Validar una asignación con el docente ocupado, la sección ocupada, un docente no disponible y un docente no habilitado para el curso.
- Esperado: Cada conflicto se identifica con su tipo y mensaje.
- Verificado por: `ValidadorConflictosTest`
- Resultado de la revisión: ✅

### Prueba 86 (caso de uso)
- Objetivo: Probar el flujo básico: asignar una clase en la matriz.
- Precondición: Borrador del año; docente disponible y habilitado.
- Descripción: Elegir una celda, un curso y un docente, y confirmar.
- Esperado: La clase se registra en el borrador, la carga horaria del curso se actualiza y queda auditada.
- Verificado por: `BorradorHorarioIT · SPA: elaborar.test`
- Resultado de la revisión: ✅

### Prueba 87 (caso de uso)
- Objetivo: Probar los flujos alternativos FA-1 «modificar», FA-2 «eliminar» y FA-3 «cambiar perspectiva».
- Precondición: Clase existente.
- Descripción: Modificar la clase, eliminarla y cambiar entre las perspectivas Sección y Docente.
- Esperado: Los cambios se aplican con If-Match; ambas perspectivas muestran el mismo borrador.
- Verificado por: `BorradorHorarioIT · SPA: elaborar.test`
- Resultado de la revisión: ✅

### Prueba 88 (caso de uso)
- Objetivo: Verificar que no se pueda confirmar una asignación con conflicto ni con una versión desactualizada.
- Precondición: Docente con clase en el mismo bloque; borrador modificado por otro usuario.
- Descripción: Confirmar la asignación en conflicto; confirmar con la versión anterior; dos altas simultáneas en la misma celda.
- Esperado: 409 con el cruce; 409 por versión con «Recargar matriz»; en paralelo gana una sola alta.
- Verificado por: `BorradorHorarioIT, HorarioBorradorConcurrenciaIT`
- Resultado de la revisión: ✅

## UC-22: Publicar horario
### Prueba 89 (unitaria)
- Objetivo: Verificar la completitud de la carga horaria curso por curso.
- Clases asociadas: ValidadorCompletitud, ResponsablesPorCurso
- Descripción: Evaluar la completitud de cada sección.
- Esperado: Estados COMPLETO, FALTAN, EXCEDE o FALTAN_Y_EXCEDE; faltar en un curso no se compensa con sobrar en otro.
- Verificado por: `ValidadorCompletitudTest, ResponsablesPorCursoTest`
- Resultado de la revisión: ✅

### Prueba 90 (caso de uso)
- Objetivo: Probar el flujo básico: publicar el horario completo.
- Precondición: Borrador sin observaciones.
- Descripción: Revisar la completitud y confirmar la publicación.
- Esperado: Se crea la versión N+1 inmutable, se retira la anterior y se sincronizan las asignaciones docentes en una transacción.
- Verificado por: `PublicacionHorarioIT · SPA: publicar.test`
- Resultado de la revisión: ✅

### Prueba 91 (caso de uso)
- Objetivo: Probar el flujo alternativo FA-1 «horario incompleto».
- Precondición: 3° A con 19 h de 22 y 4° B con 23 h.
- Descripción: Intentar publicar.
- Esperado: 422 HORARIO_INCOMPLETO con las secciones observadas; no cambia nada.
- Verificado por: `PublicacionHorarioIT · SPA: publicar.test`
- Resultado de la revisión: ✏️ `PublicacionHorarioIT.fa1UnHorarioIncompletoNoSePublicaYElBorradorNoCambia` prueba el 422 `HORARIO_INCOMPLETO` con 2 secciones observadas y sin cambios, pero con la fixture de la prueba (un horario mínimo de 3° A y 4° B; `unHorarioExactoEsPublicableUnoConFaltanteOExcesoNoYElDetalleDiceDonde` usa un faltante de 1 h y un exceso de 1 h). Los números del caso (3° A con 19 h de 22 → FALTAN 3; 4° B con 1 h de más → EXCEDE; 422 `HORARIO_INCOMPLETO`) están en `DatosDemoFase3IT.uc22LaDemoArrancaConDosSeccionesConObservacionesComoElWireframe` (semilla de demo).

### Prueba 92 (caso de uso)
- Objetivo: Verificar la publicación con un borrador modificado y la inmutabilidad de las versiones.
- Precondición: Borrador cambiado después de la revisión; versión publicada.
- Descripción: Publicar con la versión revisada anterior; intentar modificar una versión publicada en la base.
- Esperado: 409 por borrador cambiado; el trigger impide modificar la versión publicada.
- Verificado por: `PublicacionHorarioIT, MigracionesIT`
- Resultado de la revisión: ✅

## UC-23: Consultar horario
### Prueba 93 (unitaria)
- Objetivo: Verificar la caché del horario vigente y su invalidación.
- Clases asociadas: CacheHorarioVigente, ConsultaHorarioService
- Descripción: Consultar, publicar una nueva versión y consultar de nuevo.
- Esperado: La segunda consulta devuelve la versión nueva (la caché se invalida tras el commit).
- Verificado por: `ConsultaHorarioIT`
- Resultado de la revisión: ✅

### Prueba 94 (caso de uso)
- Objetivo: Probar el flujo básico: consultar el horario publicado.
- Precondición: Horario publicado.
- Descripción: Abrir «Horario» como administrador, docente, alumno y apoderado.
- Esperado: Cada rol ve la última versión publicada, nunca el borrador, dentro de su alcance.
- Verificado por: `ConsultaHorarioIT · SPA: horario.test`
- Resultado de la revisión: ✅

### Prueba 95 (caso de uso)
- Objetivo: Probar los flujos alternativos FA-1 «horario no publicado» y FA-2 «cambiar perspectiva».
- Precondición: Año sin versión publicada.
- Descripción: Consultar; cambiar entre las perspectivas Sección y Docente.
- Esperado: 404 HORARIO_NO_PUBLICADO; el alumno y el apoderado no tienen la perspectiva Docente.
- Verificado por: `ConsultaHorarioIT`
- Resultado de la revisión: ✅

### Prueba 96 (caso de uso)
- Objetivo: Verificar el alcance por rol.
- Precondición: Docente y alumno autenticados.
- Descripción: Consultar otra sección u otro docente.
- Esperado: 403 FUERA_DE_ALCANCE.
- Verificado por: `ConsultaHorarioIT`
- Resultado de la revisión: ✅

## UC-24: Gestionar la cuenta
### Prueba 97 (unitaria)
- Objetivo: Verificar los tokens de sesión y los tokens opacos de recuperación e invitación.
- Clases asociadas: JwtService, TokensOpacos, BloqueoFicticio, ValidacionClaveJwt
- Descripción: Emitir y validar un JWT (firma, expiración de 15 min); generar tokens de 32 bytes; validar el arranque con la clave JWT de desarrollo en el perfil aws.
- Esperado: El JWT alterado o vencido se rechaza; los tokens se guardan solo como hash; el arranque con la clave de desarrollo fuera de local/demo/test se rechaza.
- Verificado por: `JwtServiceTest, TokensOpacosTest, BloqueoFicticioTest, ValidacionClaveJwtIT`
- Resultado de la revisión: ✅

### Prueba 98 (caso de uso)
- Objetivo: Probar el flujo básico de inicio de sesión (FA-4).
- Precondición: Cuenta activa.
- Descripción: Ingresar correo y contraseña correctos.
- Esperado: Se emite el token de acceso y la cookie de refresco HttpOnly/Secure/SameSite=Strict; la SPA redirige según el rol.
- Verificado por: `RefreshTokenIT, JwtYPermisosIT · SPA: login.test`
- Resultado de la revisión: ✅

### Prueba 99 (caso de uso)
- Objetivo: Probar el flujo alternativo FA-3 «bloqueo tras 5 intentos» (RNF-06).
- Precondición: Cuenta activa con el contador en 0.
- Descripción: Ingresar 5 contraseñas incorrectas y luego la correcta; repetir con un correo inexistente.
- Esperado: Al 5.º intento: CUENTA_BLOQUEADA con la hora de desbloqueo (15 min); el 6.º intento se rechaza aunque la contraseña sea correcta. Un correo inexistente recibe la misma respuesta (sin enumeración de cuentas).
- Verificado por: `BloqueoCuentaIT · SPA: login.test`
- Resultado de la revisión: ✅

### Prueba 100 (caso de uso)
- Objetivo: Probar el desbloqueo: por el administrador, por vencimiento y al restablecer la contraseña.
- Precondición: Cuenta bloqueada.
- Descripción: Desbloquear desde «Usuarios»; dejar vencer los 15 minutos; restablecer la contraseña.
- Esperado: En los tres casos la cuenta vuelve a admitir 5 intentos (contador en 0); el desbloqueo queda auditado.
- Verificado por: `UsuariosIT, BloqueoCuentaIT, RecuperacionContrasenaIT`
- Resultado de la revisión: ✅

### Prueba 101 (caso de uso)
- Objetivo: Probar el flujo alternativo FA-5 «recuperar contraseña».
- Precondición: Cuenta activa con correo.
- Descripción: Pedir la recuperación, abrir el enlace y definir una contraseña nueva; reutilizar el enlace.
- Esperado: Respuesta 202 idéntica exista o no la cuenta; el enlace vale 30 min y un solo uso; se revocan los refresh tokens; reutilizarlo da TOKEN_RECUPERACION_INVALIDO.
- Verificado por: `RecuperacionContrasenaIT · SPA: recuperacion.test`
- Resultado de la revisión: ✅

### Prueba 102 (caso de uso)
- Objetivo: Probar la creación de cuentas con invitación (pasos 1 a 5, FA-1).
- Precondición: Administrador autenticado.
- Descripción: Crear un docente; crear otra cuenta con el mismo DNI o correo.
- Esperado: La cuenta queda pendiente y recibe un enlace de 72 h para definir su contraseña (el administrador nunca la ve); el duplicado responde 409.
- Verificado por: `GestionUsuariosIT · SPA: gestionUsuarios.test`
- Resultado de la revisión: ✅

### Prueba 103 (caso de uso)
- Objetivo: Probar la desactivación y el vínculo académico (FA-2).
- Precondición: Cuenta activa con sesión iniciada; alumno sin matrícula confirmada.
- Descripción: Desactivar la cuenta y usar su token; habilitar al alumno.
- Esperado: El siguiente request da 401 (sesiones revocadas); habilitar al alumno responde 422 VINCULO_ACADEMICO_INEXISTENTE.
- Verificado por: `GestionUsuariosIT`
- Resultado de la revisión: ✅

### Prueba 104 (caso de uso)
- Objetivo: Probar la gestión de roles y permisos (RF-48, RNF-07).
- Precondición: Administrador autenticado.
- Descripción: Quitar un permiso a un rol y repetir la petición con el mismo token; intentar quitar ROL_GESTIONAR al administrador.
- Esperado: El cambio rige en el siguiente request; el permiso esencial no se puede quitar (400).
- Verificado por: `GestionRolesIT, CacheCuentasIT · SPA: roles.test`
- Resultado de la revisión: ✅

## UC-25: Exportar historial de auditoría consultado
### Prueba 105 (unitaria)
- Objetivo: Verificar la exportación CSV segura y la generación del Excel.
- Clases asociadas: GeneradorCsv, GeneradorExcel
- Descripción: Exportar los registros.
- Esperado: CSV con BOM UTF-8 según RFC 4180 y sin fórmulas ejecutables; Excel con la zona horaria institucional.
- Verificado por: `GeneradorCsvTest, GeneradorExcelTest`
- Resultado de la revisión: ✅

### Prueba 106 (caso de uso)
- Objetivo: Probar el flujo básico: filtrar y exportar la auditoría.
- Precondición: Administrador autenticado; registros de auditoría.
- Descripción: Filtrar por usuario, módulo, acción y fechas; ver los cambios de un registro; exportar a Excel y CSV.
- Esperado: Se muestran los registros filtrados con el valor anterior y el nuevo; la descarga respeta los filtros y la exportación también se audita.
- Verificado por: `AuditoriaConsultaExportacionIT, ConsultaAuditoriaIT · SPA: auditoria.test`
- Resultado de la revisión: ✅

### Prueba 107 (caso de uso)
- Objetivo: Verificar la inmutabilidad del registro de auditoría (RNF-08).
- Precondición: Registros de auditoría.
- Descripción: Ejecutar UPDATE y DELETE sobre la tabla de auditoría con el usuario de la aplicación.
- Esperado: Ambas sentencias fallan (trigger y permisos solo INSERT/SELECT).
- Verificado por: `AuditoriaInmutableIT`
- Resultado de la revisión: ✅

### Prueba 108 (caso de uso)
- Objetivo: Verificar el registro en la misma transacción y el acceso por rol.
- Precondición: Operación auditable que falla; usuarios no administradores.
- Descripción: Forzar el fallo de una operación; consultar la auditoría como alumno, apoderado y docente.
- Esperado: Si la operación falla no queda registro; los otros roles reciben 403.
- Verificado por: `AspectoAuditableIT, ConsultaAuditoriaIT`
- Resultado de la revisión: ✅

## Pruebas transversales
### Prueba 109
- Objetivo: Verificar las reglas de arquitectura (capas, módulos y dependencias).
- Descripción: Ejecutar ArchUnit: capas, módulos solo vía aplicación, sin ciclos, @Transactional solo en aplicación, controladores sin entidades, tiempo solo desde Clock.
- Esperado: Todas las reglas pasan; una violación hace fallar el build.
- Verificado por: `ArquitecturaTest`
- Resultado de la revisión: ✅

### Prueba 110
- Objetivo: Verificar que todos los endpoints exijan autorización (RF-50, RNF-07).
- Descripción: Recorrer todos los endpoints registrados.
- Esperado: Todos tienen @PreAuthorize salvo seis públicos explícitos (/auth/*, salud y Swagger en local).
- Verificado por: `EndpointsProtegidosIT, JwtYPermisosIT`
- Resultado de la revisión: ✏️ `EndpointsProtegidosIT.todoEndpointDeNegocioExigePreAuthorizeSalvoLosPublicosDeLaLista` define exactamente 6 públicos, pero son: `POST /api/v1/auth/inicio-sesion`, `/auth/refresco`, `/auth/recuperacion`, `/auth/recuperacion/confirmacion` y `PUT`/`GET /api/v1/archivos-locales/{token}` (URL prefirmadas del disco local). La salud (`/actuator/health`) y Swagger no son parte de esa lista (no son controladores del `RequestMappingHandlerMapping`); su acceso público lo prueba `JwtYPermisosIT.laSaludDelServicioEsPublica` y `laDocumentacionDeLaApiEsPublicaYDeclaraElEsquemaBearer`.

### Prueba 111
- Objetivo: Verificar las cabeceras de seguridad (RNF-01).
- Descripción: Hacer peticiones HTTP y HTTPS.
- Esperado: Respuestas con X-Frame-Options, CSP frame-ancestors 'none', Referrer-Policy y Permissions-Policy; HSTS solo en HTTPS.
- Verificado por: `CabecerasSeguridadIT`
- Resultado de la revisión: ✅

### Prueba 112
- Objetivo: Verificar el límite de peticiones a /auth/*.
- Descripción: Enviar 31 peticiones desde la misma IP en un minuto.
- Esperado: La petición 31 recibe 429.
- Verificado por: `LimiteAuthIT`
- Resultado de la revisión: ✏️ `LimiteAuthIT` fija `gesedu.seguridad.limite-auth-por-minuto=3` y comprueba que la **4.ª** petición responde 429 `LIMITE_PETICIONES_EXCEDIDO` (`application/problem+json`, `Retry-After: 60`), y que `/auth/refresco` comparte el contador de la IP. El valor por defecto es 30 (`application.yml`), pero no hay prueba que envíe 31 peticiones.

### Prueba 113
- Objetivo: Verificar el formato de errores RFC 9457 y el contrato de la API.
- Descripción: Provocar errores de validación y de negocio; comparar el OpenAPI con los tipos del frontend.
- Esperado: Errores en application/problem+json con codigo y errores[]; los tipos generados compilan y coinciden con el contrato.
- Verificado por: `ProblemDetailsIT, SwaggerIT · SPA: contrato.test`
- Resultado de la revisión: ✅

### Prueba 114
- Objetivo: Verificar las migraciones y las restricciones de la base de datos.
- Descripción: Aplicar todas las migraciones y probar las restricciones (EXCLUDE de bimestres, CHECK, índices únicos y triggers de inmutabilidad).
- Esperado: Las migraciones se aplican y cada restricción rechaza los datos inválidos.
- Verificado por: `MigracionesIT`
- Resultado de la revisión: ✅

### Prueba 115
- Objetivo: Verificar la carga de 200 apoderados en UC-01 (RNF-12, RNF-03).
- Descripción: Ejecutar k6 en los escenarios «pico» (200 envíos a la vez) y «sostenida» (2 minutos).
- Esperado: Medición local del 06/11/2026: 0 % de errores; la solicitud cumple (p95 de 1,59 s en el pico); el p95 total es de 3,40 s (pico) y 2,53 s (sostenida). La medición válida se repite en QA.
- Verificado por: `docs/despliegue/carga/uc01.js (k6)`
- Resultado de la revisión: ✅

### Prueba 116
- Objetivo: Verificar el diseño responsive y la accesibilidad (RNF-02).
- Descripción: Recorrer 17 pantallas de administrador y apoderado en 375×812 con pantalla táctil y ejecutar Lighthouse.
- Esperado: Sin desbordes horizontales; Lighthouse 100 en accesibilidad.
- Verificado por: `Prueba manual con Chrome DevTools`
- Resultado de la revisión: ✏️ Sin prueba automatizada; el registro está en `docs/AVANCE.md` (tarea 6.5d, commit b493fe2): 17 pantallas del administrador y del apoderado a 375×812 con pantalla táctil, sin desbordes, etiquetas, nombres ni `lang` pendientes, y Lighthouse de accesibilidad 100 «en el login y en las pantallas autenticadas revisadas» (no se dice que sea en las 17). Texto corregido: «Lighthouse 100 en accesibilidad en el login y en las pantallas autenticadas revisadas».
- Actualización (observación 1, 11/11/2026): la parte responsive ya tiene pruebas automatizadas. `e2e/responsive.spec.ts` (Playwright) recorre 21 rutas del administrador, 11 de la docente y 4 del apoderado en 375, 768 y 1280 px sin desbordamiento horizontal, y prueba el menú plegable (☰ de 44 px, foco, cierre al navegar y con Escape); pasa en Chromium, Firefox y Edge (`docs/pruebas/compatibilidad.md`). `MenuMovil.test` (Vitest, 7 casos) cubre el menú plegable en la SPA. Lighthouse sigue siendo manual.

### Prueba 117
- Objetivo: Verificar la compatibilidad con navegadores (RNF-04).
- Descripción: Ejecutar los smoke tests de Playwright (`frontend/e2e`: inicio y cierre de sesión, credenciales incorrectas, navegación por rol de administrador, docente y apoderado, marcación y pase de lista, registro de notas, descarga de la libreta y pantallas responsive) en Chromium, Firefox y Edge, con los datos preparados antes de cada navegador.
- Esperado: Las 14 pruebas pasan en cada navegador. Safari queda fuera de alcance (no hay equipo Apple).
- Verificado por: `npm run e2e (frontend/e2e, Playwright)` · resumen en `docs/pruebas/compatibilidad.md`
- Resultado de la revisión: ✅ (observación 3, 11/11/2026). Chromium 156.0.8078.4, Firefox 157.0 y Edge 157.0.4341.46: **14/14 en cada uno**. Safari, fuera de alcance y anotado así. No entra en CI: necesita Docker, el backend con `local,demo` y Edge instalado. De la lista manual de AVANCE quedan sin automatizar el bloqueo tras 5 intentos, UC-21, UC-01, UC-04, las exportaciones y el teclado en diálogos; siguen cubiertos por sus pruebas de integración y de Vitest, no por navegador. Revisión anterior: ✏️ `docs/AVANCE.md` (tarea 6.10, «Navegadores (RNF-04)») figura **pendiente**: las nueve casillas de la lista están sin marcar. Dice que Chrome se usó en todas las pruebas de navegador de las fases 2 a 5, pero no que la lista se haya ejecutado completa en Chrome; Edge y Firefox, por probar; Safari, no probado. «Todas las comprobaciones pasan» no está registrado para ningún navegador.

## Datos para el plan

Revisión hecha leyendo el código y el historial de git (6/11/2026); no se ejecutó ninguna prueba.

### Cantidad de pruebas
- **Verificación completa del 11/11/2026** (rama `feat/observaciones-e5`, observaciones de la evaluación del primer incremento), ejecutada y contada en los reportes de surefire y failsafe: backend **184 unitarias + 640 de integración = 824**, 0 fallos, 0 errores y 0 omitidas (`./mvnw -B verify`, BUILD SUCCESS en 19:18 min). Antes de los cambios, en `main` (`6e657ae`): **184 + 638 = 822**, igual que los documentos entregados; las 2 nuevas son `LibretaPdfContenidoIT`. Frontend **372 pruebas en 44 archivos** (365 + 7 de `MenuMovil.test`), con lint, typecheck y build en verde. Playwright (`npm run e2e`): 14/14 en Chromium 156, Firefox 157 y Edge 157 (`docs/pruebas/compatibilidad.md`).
- Registro anterior de una verificación completa (`docs/AVANCE.md`, tarea 6.5, `clean verify` con `-Xmx1g` y un fork): backend **182 unitarias + 617 de integración = 799**; frontend **365 pruebas en 43 archivos** (lint, typecheck y build en verde).
- Registro anterior (tarea 6.4): 616 pruebas de integración. Cierre de la fase 5: 182 unitarias + 577 de integración y 341 del frontend.
- **Recuento estático actual** (lectura de `src/test` y `*.test.ts(x)`): backend **783** métodos con `@Test`, `@RepeatedTest` o `@ParameterizedTest` en 95 clases (629 en clases de integración —`@SpringBootTest` o `extends BaseIT`— y el resto unitarios) más las 17 reglas `@ArchTest` de `ArquitecturaTest`; el número que informa Maven es mayor porque `VacantesConcurrentesIT` se repite 3 veces y las pruebas parametrizadas se expanden. Frontend: **361** `it`/`test` en 43 archivos, más los casos de 2 `it.each` (365 con la expansión, igual que el registro).
- Después del último registro se agregaron pruebas (`ValidacionClaveJwtIT`, 6 casos, tarea 6.8; las de S-86 sobre la secuencia de códigos de alumno), así que el total real hoy es mayor que 799; confirmar con un `./mvnw -B clean verify` antes de citarlo en el plan.

### Fecha del commit de cierre de cada fase (`git log`)
| Fase | Commit de cierre | Fecha |
|---|---|---|
| 0 | `4b37651` Fase 0: contradicciones C-32 a C-43 en PLAN.md y resumen de la fase en AVANCE | 28/10/2026 |
| 1 | `889661d` Fase 1: cierre en AVANCE (estado, resumen de la fase y supuestos) | 28/10/2026 |
| 2 | `2056eba` Fase 2: cierre con prueba en navegador, correcciones de móvil y tipos del contrato (fusionada en `main` por `1374a4b`, 29/10/2026) | 29/10/2026 |
| 3 | `faa85be` Cierre de la fase 3: restablecer la contraseña levanta el bloqueo y prueba en navegador (después, `ca9e5e9` corrige los cuatro hallazgos, 04/11/2026) | 04/11/2026 |
| 4 | `eecb709` Cierre de la fase 4: guion (paso 13), PLAN § 13 y AVANCE | 05/11/2026 |
| 5 | `8b8aa33` Cierre de la fase 5: guion (paso 14), contrato, PLAN 13 y AVANCE | 05/11/2026 |
| 6 | `814bac5` Cierre de la fase 6: carga aceptada como medición local, lista de navegadores y estado en AVANCE (tareas 6.7 a 6.10) | 06/11/2026 |

La fase 6 queda con dos pendientes fuera del código: la prueba manual en Edge y Firefox (6.10) y la medición válida de carga en QA.

### Versiones reales de las tecnologías de pruebas
| Herramienta | Versión | De dónde sale |
|---|---|---|
| JUnit (Jupiter y Platform) | 6.0.3 | BOM de Spring Boot 4.1.1 (`junit-jupiter.version`); `backend/pom.xml` no la fija |
| Testcontainers | 2.0.5 | BOM de Spring Boot 4.1.1 (`testcontainers.version`) |
| ArchUnit | 1.5.1 | `archunit.version` en `backend/pom.xml` |
| AssertJ / Mockito (referencia) | 3.27.7 / 5.23.0 | BOM de Spring Boot 4.1.1 |
| Vitest | 5.0.5 (`^5.0.5`) | `frontend/package.json` y `node_modules` |
| MSW | 3.0.3 (`^3.0.3`) | `frontend/package.json` y `node_modules` |
| Testing Library (React / user-event) | 16.3.3 / 14.6.8 | `node_modules` |
| k6 | v2.3.0 (go1.27.1) | imagen `grafana/k6:latest` en este equipo; los comandos de `docs/despliegue/carga/README.md` usan `latest`, no una etiqueta fija, y los archivos `resultado-k6-*.txt` no registran la versión (conviene fijarla antes de repetir la medición en QA) |

### Contenido real de la semilla `db/demo` (V1000 a V1006)
- **V1000** `datos_demo.sql`: 6 cuentas con la contraseña común `Gesedu2027!` (admin, directivo, docente, alumno, apoderado y la cuenta `bloqueo@` para mostrar el bloqueo), el cargo de los administradores, el año escolar 2027 y sus bloques horarios de lunes a viernes.
- **V1001** `datos_demo_academico_horarios.sql`: los 4 bimestres de 2027, 5 grados, 10 secciones (3° A con 4 vacantes, 2° B con 1), 6 cursos de 22 h con 18 competencias y su carga horaria, 8 docentes con vinculación, asociaciones y disponibilidad (Carlos Ríos no disponible el martes 9:15), el borrador de horario con 3° A en 19 de 22 h y la matrícula confirmada de Lucía (`MAT-2027-00478`).
- **V1002** `datos_demo_matricula_cobros.sql`: cronograma de cobros 2027 (matrícula + 10 pensiones de S/ 350.00 = S/ 3,850.00) con política de penalidad, el año 2026 cerrado con una deuda de S/ 700.00, las solicitudes de matrícula `MAT-2027-00479` a `00486` (con apoderados, alumnos y documentos) para UC-02, y la familia Torres activa con un hijo matriculado (Tomás, 1° A) y sus 11 obligaciones.
- **V1003** `datos_demo_fase3.sql`: docentes nuevas (Elena Torres sin acceso, Marta Ibáñez con cuenta), el periodo de registro de disponibilidad, vinculaciones, asociaciones y disponibilidad (Rosa con todos los bloques marcados; la docente nueva sin disponibilidad) y asignaciones de horario para que 3° A y 4° B queden con observaciones de completitud (UC-14, UC-20, UC-22, UC-23).
- **V1004** `datos_demo_fase4.sql`: asistencia (UC-07 a UC-12): seis alumnos en 3° A con apoderado y matrícula, asistencia de alumnos y de docentes del 01/03 al 12/03/2027, los cierres diarios de esas fechas y las marcaciones del lunes 15/03 (tres docentes ya marcaron; Rosa no). Necesita el reloj de la demo (empieza el 15/03/2027 07:40) y el horario publicado.
- **V1005** `datos_demo_fase5.sql`: evaluación (UC-15 a UC-19): asignaciones docente-curso-sección 2027, logros del bimestre I de todos los alumnos (Matemática de 3° A queda incompleta a propósito: a dos alumnos les falta la competencia C3), y el año 2026 completo (2° A con cuatro bimestres consolidados, notas anuales y libretas FINAL, para el apoderado de Valeria Torres).
- **V1006** `ajuste_secuencia_alumnos.sql`: no inserta datos; reubica la secuencia `seguridad.seq_codigo_alumno` después del mayor código de alumno de la semilla para que el primer alumno nuevo no choque (acompaña a la migración V13).
