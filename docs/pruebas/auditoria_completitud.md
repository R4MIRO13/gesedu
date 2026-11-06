# Auditoría de completitud de GESEDU

Fecha: 06/11/2026 · Rama `main` (920684d) · Solo lectura y ejecución de pruebas; no se cambió código.
Fuentes: `docs/ers.pdf` (v3), `docs/catalogo_requisitos.pdf` (v4), `docs/AVANCE.md`, `docs/PLAN.md` § 13 y § 14.

## 0. Resumen

**Completo.** Los 25 casos de uso tienen implementado y probado su flujo básico y todos sus flujos alternativos y excepcionales (§ 3.1), en el backend y, donde hay pantalla, en la SPA. Los 52 RF están implementados y probados (§ 3.2). `./mvnw.cmd -B clean verify` y lint, typecheck, test y build del frontend pasan sin fallos. No hay `TODO`, `FIXME`, `XXX`, `UnsupportedOperationException`, `@Disabled` ni `skip`. La SPA no llama a ningún endpoint inexistente.

**Falta en el código (menor).**
- P3: RNF-03, RNF-07 y RNF-08 no tienen una prueba que mida el tiempo límite (2 s, 1 s y 2 s). Se cumplen por diseño, sin evidencia medida.
- P3: dos endpoints del backend que la SPA no usa (§ 4). Están probados por IT.
- P3: el título de UC-10, UC-11 y UC-21 en la tabla de `AVANCE.md` no coincide con el de la ERS (§ 3.1).

**Pendiente fuera del código.**
- RNF-04: prueba manual en Edge y Firefox. Safari no probado.
- RNF-12: medición válida en QA/AWS. La local da p95 de la solicitud de 1,59 s en el pico de 200, pero el flujo completo da p95 de 3,40 s.
- RNF-05: disponibilidad del 99 %, solo se mide en operación.
- RNF-01: HTTPS lo pone la infraestructura (en el código solo están HSTS y las cabeceras).
- P-04, P-05, P-06, P-07 y P-08 de `PLAN.md` § 14, más S-03 y S-04 sin confirmar con la institución.
- Publicar los 920684d locales: `main` va 1 commit por delante de `origin/main`.

## 1. Estado de git

| Comprobación | Resultado |
|---|---|
| Rama actual | `main`, árbol limpio al inicio |
| `git log main..feat/fase-6` | vacío: `main` contiene todo `feat/fase-6` |
| `git log feat/fase-6..main` | 1 commit: `920684d` «Revisión de los 117 casos de prueba…» |
| `main` vs `origin/main` | **adelantada 1 commit** (`920684d`, sin publicar); `origin/main` = `814bac5` = `feat/fase-6` |
| Otras ramas | `feat/fase-0` a `feat/fase-5`, `wip/fase-3`, `respaldo-antes-de-reset`, `feat/demo-esqueleto-seguridad` |

Tras ejecutar `npm run build`, `frontend/src/routeTree.gen.ts` apareció modificado solo por fin de línea (LF→CRLF). Se restauró con `git checkout` y no entra en el commit.

## 2. Verificación completa en `main`

Condiciones: `MAVEN_OPTS=-Xmx1g`, un fork, sin otros procesos del proyecto (solo el MCP de Chrome DevTools de la herramienta).

| Paso | Resultado |
|---|---|
| `.\mvnw.cmd -B clean verify` | **BUILD SUCCESS**, 14 min 30 s |
| Pruebas unitarias (surefire) | **184**, 0 fallos, 0 errores, 0 omitidas |
| Pruebas de integración (failsafe, Testcontainers `postgres:18`) | **638**, 0 fallos, 0 errores, 0 omitidas |
| Total backend | **822** |
| `npm run lint` (oxlint) | sin hallazgos |
| `npm run typecheck` | sin errores |
| `npm test -- --run` (Vitest 5) | **365 pruebas en 43 archivos**, todas pasan |
| `npm run build` | correcto, 5,7 s |

Nota: el log de Maven muestra un `ERROR: update or delete on table "curso" violates foreign key constraint "carga_horaria_curso_curso_id_fkey"`. Es el mensaje esperado de PostgreSQL en `CursosIT.unCursoSinUsoSeEliminaYUnoConCargaHorariaNo` (el caso negativo); la prueba pasa.

## 3. Cobertura contra la ERS y el catálogo

Convención: **Impl.** = clase o endpoint. **Prueba** = clase#método (IT/Test del backend) o `x.test` (SPA). Todos los casos son «implementado y probado»; no hay ningún **FALTA** en los 25 UC, salvo lo indicado en observaciones.

### 3.1 Casos de uso

| UC | Flujo | Impl. | Prueba |
|---|---|---|---|
| UC-01 | Básico | `SolicitudApoderadoController` `POST /solicitudes-matricula` | `SolicitudApoderadoIT#flujoBasicoDeNuevoIngresoRegistraLaSolicitudPendienteConSuCodigo`; SPA `solicitud.test` |
| | FA-1 incompleta | ídem (400 con `errores[]`) | `#fa1FaltanDocumentosObligatoriosSeResaltanYNoSeGuardaNada`, `#fa1FaltanCamposObligatoriosYElErrorNombraCadaCampo` |
| | FA-2 DNI inválido/duplicado | ídem (409 `SOLICITUD_EN_TRAMITE`, `DOCUMENTO_DUPLICADO`) | `#fa2UnDocumentoConFormatoInvalidoSeRechaza`, `#fa2YaExisteUnaSolicitudEnTramiteConEseDocumento` |
| UC-02 | Básico | `SolicitudMatriculaController` `POST …/confirmacion`, `EvaluacionMatriculaService` | `ConfirmacionMatriculaIT#confirmarCreaLaMatriculaDescuentaLaVacanteGeneraLos11CompromisosAuditaYAvisaPorCorreo`; SPA `ConfirmarMatricula.test` |
| | FA-1 no admitido | `POST …/rechazo` | `#fa1NoAdmitidoBloqueaYSeRechazaPorNoAdmitido` |
| | FA-2 duplicidad | ídem | `#fa2MatriculaActivaBloqueaYSeRechazaPorDuplicidad` |
| | FA-3 deudas | ídem | `#fa3DeudaVencidaDeUnAnioAnteriorBloqueaYSeRechazaPorDeudasConElImporte` |
| | FA-4 sin vacantes | ídem | `#fa4SinVacantesLaConfirmacionDaSinVacanteYSePuedeRechazarSinVacante` |
| | FA-5 desistimiento | ídem | `#fa5ElDesistimientoSeRechazaSinTocarVacantesNiDeudas` |
| UC-03 | Básico | `MatriculaController`, `ObligacionController` | `ConsultaObligacionesIT#flujoBasicoMuestraLaMatriculaVigenteYLasObligacionesDelHijo`, `#laObligacionVencidaMuestraLaPenalidadYElTotalActualizado`; SPA `pagos.test` |
| | FA-1 sin obligaciones | ídem | `#fa1SinObligacionesGeneradasLaListaVieneVacia` |
| | FA-2 matrícula no vigente | ídem | `#fa2SinMatriculaVigenteNoHayMatriculaNiObligacionesDeUnAnioActivo` |
| UC-04 | Básico | `ComprobanteApoderadoController` | `PresentacionComprobanteIT#flujoBasicoRegistraElComprobantePendienteSinCambiarLaObligacion`; SPA `comprobante.test` |
| | FA-1 incompleto | ídem | `#fa1FaltanDatosDelComprobanteYElErrorNombraCadaCampo` |
| | FA-2 ya pagada | ídem | `#fa2UnaObligacionYaPagadaNoRecibeOtroComprobante` |
| UC-05 | Básico | `ComprobanteController` `POST …/validacion` | `ValidacionPagoIT#flujoBasicoEmiteLaBoletaApruebaElComprobanteYDejaLaObligacionPagada`, `#laObligacionVencidaSeValidaPorLaSumaDelImporteBaseYLaPenalidad`; SPA `validar.test` |
| | FA-1 no corresponde | `POST …/rechazo` | `#fa1UnComprobanteQueNoCorrespondeSeRechazaSinTocarLaObligacion` |
| | FA-2 importe insuficiente | `POST …/observacion` | `#fa2UnImporteInsuficienteEnUnaObligacionVencidaSeObservaYElApoderadoPresentaUnoNuevo` |
| UC-06 | Básico | `HistorialFinancieroController` | `HistorialFinancieroIT#flujoBasicoMuestraResumenObligacionesBoletasYDeudasConSuPenalidad`, `#laConsultaNoModificaObligacionesPagosNiBoletas`; SPA `historial.test` |
| | FA-1 / FA-2 | ídem | `#fa1UnEstudianteSinObligacionesNiPagosLoInformaSinHistorial`, `#fa2ConObligacionesPeroSinPagosSe…` |
| UC-07 | Básico | `AsistenciaDocenteController`, `ClasificadorAsistenciaDocente` | `MarcacionDocenteIT#marcarConHoraDelServidorGuardaElRegistroYSuEstado`, `#laHoraDelDispositivoNoSeUsa`; `ClasificadorAsistenciaDocenteTest`; SPA `miAsistencia.test` |
| | FA-1 existente | ídem | `#fa1UnSegundoMarcadoMuestraElRegistroExistenteYNoSobrescribe`, `#fa1SolicitudesSimultaneasDejanUnSoloRegistro` |
| | FA-2 sin clases/horario | ídem | `#fa2UnDocenteSinClasesEseDiaNoRegistraAsistencia`, `#fa2SinHorarioPublicadoNoSeRegistraAsistencia` |
| | FA-3 no lectivo/cerrada | ídem | `#fa3UnDiaSinBloquesNoEsLectivo`, `#fa3LaJornadaCierraEnElCierreExactoYAntesSiSePuedeMarcar` |
| UC-08 | Básico | `AsistenciaSeccionController` | `PaseListaConcurrenteIT#elLoteConLosTresEstadosSeGuardaCompletoYMuestraLosTotales`, `#laSeccionSeMuestraEnOrdenAlfabetico…`; SPA `registrarAsistencia.test` |
| | FA-1 a FA-5 | ídem | `#fa1UnLoteIncompleto…`, `#fa2LosYaGuardados…`, `#fa3UnaSeccionSinAlumnos…`, `#fa3UnDocenteSinTutoria…`, `#fa4DespuesDelCierre…`, `#fa4UnDiaNoLectivo…`, `#fa5SolicitudesSimultaneas…` |
| UC-09 | Básico | `ActualizacionAsistenciaController` `PATCH /asistencia-alumnos/{id}` | `ActualizacionAsistenciaIT#elTutorCambiaDeFaltaATardanzaElMismoRegistroYSuUpdatedAt`, `#elCambioQuedaEnLaAuditoria…`; SPA `registroDelDia.test` |
| | FA-1 / FA-2 / FA-3 | ídem | `#fa1ElLimiteEsInclusivo…`, `#fa1ConLaJornadaCerrada…`, `#fa2UnaAsistenciaPuntualNoSeModifica`, `#fa2UnRegistroQueYaEsTardanza…`, `#fa2UnRegistroDeOtraFecha…`, `#fa3ElTutorDeOtraSeccionNoPuedeCambiarlo` |
| UC-10 | Básico | `ReporteAsistenciaController` `GET /reportes/asistencia` | `ReporteAsistenciaIT#elTutorObtieneElResumenDeSuSeccion…`, `#excelTraeElResumen…`, `#pdfEsUnDocumento…`, `#elExcelDeDocentesYElDeAlumnosSeGeneranPorSeparado`; SPA `reportesAsistencia.test` |
| | FA-1 a FA-4 | ídem | `#fa1ElPeriodoEnCursoEsParcial…`, `#fa2UnPeriodoSinAsistencias…`, `#fa3ElTutorNoPuedeVerOtraSeccion…`, `#fa3UnPeriodoFuturoSeRechaza…`, `#fa4SinAnioActivo…`, `#fa4ElReporteDeDocentesSinHorarioPublicado…` |
| UC-11 | Básico | `CalculoResumenMensual` | `CalculoResumenMensualTest#laTardanzaCuentaComoPresencia`, `#redondeaAlCentesimoConHalfUp`, `#losDiasSinRegistroSeMuestranAparte…`; `ReporteMesesPasadosIT` |
| | FA-1 / FA-2 | ídem | `#sinDiasRegistradosNoSeDivideNiSeInformaCero`, `#losDiasSinRegistroSeMuestranAparte…` |
| UC-12 | Básico | `JobCierreDiario`, `CierreDiarioService` | `CierreDiarioIT#creaFaltaSoloParaLosDocentesConClasesQueNoMarcaron`, `#enElCierreExactoYaActua…`; SPA `asistenciaDocentes.test` |
| | FA-1 a FA-4 | ídem | `#fa1UnDiaSinJornada…`, `#fa2SiTodosYaTienenAsistencia…`, `#fa3EjecutarloDosVeces…`, `#fa3EjecucionesParalelas…`, `#fa4SinHorarioPublicado…`, `#fa4SinAnioActivo…` |
| UC-13 | Básico | `AnioEscolarController`, `ValidadorAnioEscolar` | `AnioEscolarIT#elDirectivoCreaElAnioConSusBimestresYGradosYSeAudita`; `ValidadorAnioEscolarTest`; SPA `anioNuevo.test` |
| | FA-1 / FA-2 | ídem | `#fa1UnAnioDeNueveMesesSeRechazaYNoQuedaNada`, `#fa2LosBimestresCruzadosSeRechazanConElTramoEnConflicto` |
| UC-14 | Básico | `CursoController` | `CursosIT#elDirectivoCreaUnCursoConSusCompetenciasYSeAudita`, `#editarAgregaEditaYQuitaCompetencias…`; SPA `cursos.test` |
| | FA-1 copia | `…/cursos/copia-anterior` | `#fa1LaCopiaDelAnioAnteriorTraeLosCursosConSusCompetenciasYNoDuplicaAlRepetirse` |
| UC-15 | Básico | `RegistroLogrosController` | `RegistroLogrosIT#laRejillaMuestraLosAlumnosEnOrdenAlfabetico…`, `#guardarAsociaLosLogrosAlCursoLaSeccionYElBimestreVigente`; SPA `registroNotas.test` |
| | FA-1 nivel inválido | ídem | `#fa1UnNivelFueraDeLaEscalaSeRechazaYNoSeGuardaNada`, `#fa1ReintentarConElNivelCorregidoGuarda` |
| | FA-2 incompleto | ídem | `#fa2RegistroIncompletoSeGuardaConElAvisoYLosPendientes` |
| UC-16 | Básico | `ReglaConsolidacionController` | `ConfiguracionReglaIT#elDocenteEligeUnaReglaYQuedaGuardada`, `#lasTresReglasTraenSuEjemploCalculadoPorElMotor`; SPA `configuracionCurso.test` |
| | FA-1 cambiar | ídem | `#fa1CambiarLaReglaYaConfiguradaLaReemplaza` |
| | FA-2 por defecto | ídem | `#sinHaberElegidoLaReglaEsElPromedioPonderadoConPesosIguales` |
| UC-17 | Básico (sin FA) | `NotasFinalesController` | `NotasFinalesIT#elPromedioPonderadoCalculaLaNotaAnualDeCadaCompetencia`, `#cadaReglaDaSuResultado…`; estrategias `PromedioPonderadoStrategyTest`, `ModaStrategyTest`, `UltimoRegistroStrategyTest`; SPA `notasFinales.test` |
| UC-18 | Básico | `ConsolidacionBimestralController` | `ConsolidacionBimestralIT#consolidarPasaLasNotasBimestralesALaLibretaDeCadaAlumno`, `#elAvanceCuentaLasCeldas…`; SPA `consolidarNotas.test` |
| | FA-1 último bimestre | ídem | `#fa1EnElUltimoBimestreCalculaLasNotasAnualesYDejaLaLibretaFinal` |
| | FA-2 incompleto | ídem | `#fa2ConNotasPendientesNoSeConsolidaYSeListanLosDocentes` |
| UC-19 | Básico | `ConsultaNotasController` | `ConsultaNotasIT#elAlumnoSoloSeVeASiMismo`, `#conBimestresConsolidadosSeVeLaNotaDeCadaCompetenciaPorBimestre`, `#elDetalleDeUnCurso…`, `#laLibretaYElInformeSeDescarganEnPdf`; SPA `misNotas.test` |
| | FA-1 varios hijos | ídem | `#fa1UnApoderadoConVariosHijosLosVeTodos`; `#elApoderadoDescargaLaLibretaDeCadaHijo` |
| | FA-2 libreta final | ídem | `#laLibretaCompletaTraeTodosLosBimestresConsolidadosYLaNotaFinal`, `#laLibretaDelAnioAnteriorSeConsultaConSuNotaFinal` |
| UC-20 | Básico | `DisponibilidadDocenteController` | `DisponibilidadDocenteIT#flujoBasicoLaMatrizVieneVaciaYAlConfirmarQuedaRegistrada`; SPA `disponibilidad.test` |
| | FA-1 / FA-2 | ídem | `#fa1ActualizarConservaSoloLaUltimaVersionConfirmada`, `#fa2UnaDisponibilidadInsuficienteAdvierteYAunAsiSePuedeConfirmar` |
| | FE-1 periodo cerrado | ídem | `#fe1ConElPeriodoCerradoSeRechazaYSeConservaLaUltimaDisponibilidad` |
| UC-21 | Básico | `BorradorHorarioController` | `BorradorHorarioIT#elBorradorSeCreaSiNoExisteYDevuelveElETag`, `#agregarUnaClaseSubeLaVersion…`; SPA `elaborar.test` |
| | FA-1 modificar | `PUT …/asignaciones/{id}` | `#modificarUnaClaseCambiaElDocenteYAudita`, `#modificarValidaDeNuevoLosConflictos` |
| | FA-2 eliminar | `DELETE …/asignaciones/{id}` | `#eliminarUnaClaseActualizaLaMatrizYAudita` |
| | FA-3 perspectiva | ídem | `#lasPerspectivasSeccionYDocenteVenElMismoBorrador` |
| | FE-1 docente no disponible | ídem | `#unDocenteNoDisponibleSeRechazaConDocenteNoDisponible`, `ValidadorConflictosTest#unDocenteNoDisponibleSeInformaAntesQueLosCruces` |
| | FE-2 conflicto | ídem | `#elCruceDeDocenteSeRechazaConLaCausa…`, `#elCruceDeSeccionSeRechazaConLaCausa` |
| UC-22 | Básico | `PublicacionHorarioController` | `PublicacionHorarioIT#publicarCreaLaVersion1VigenteConUnaCopiaInmutable…`, `#publicarUnaSegundaVersionRetiraLaVigente…`; SPA `publicar.test` |
| | FA-1 incompleto | ídem | `#fa1UnHorarioIncompletoNoSePublicaYElBorradorNoCambia` |
| | FE-1 cancelación | solo SPA (el servidor no guarda estado) | SPA `publicar.test` «FE-1: cancelar la publicación no manda nada» |
| UC-23 | Básico | `ConsultaHorarioController` `GET /horarios/{anio}/vigente` | `ConsultaHorarioIT#elAdministradorVeCualquierSeccion…`, `#elApoderadoVeSoloLasSeccionesDeSusHijos…`, `#elAlumnoVeSoloLaSeccion…`, `#elDocenteVeSusClases…`; SPA `horario.test` |
| | FA-1 / FA-2 | ídem | `#fa1SinVersionPublicadaTodosLosRolesRecibenQueAunNoEstaDisponible`, `#…cambiarDePerspectivaNoModificaNada` |
| UC-24 | Básico (crear/modificar/desactivar, rol, vínculo) | `UsuarioController`, `GestionUsuariosService`, `RolController` | `GestionUsuariosIT#laCuentaNuevaNace…`, `#modificarExigeLaVersion…`, `#desactivarCortaLaSesionDeInmediato…`; `GestionRolesIT`; SPA `gestionUsuarios.test`, `roles.test` |
| | FA-1 duplicada | ídem | `#unDocumentoOUnCorreoRepetidoSeRechazaConConflicto` |
| | FA-2 sin vínculo | ídem | `#elAlumnoSinMatriculaActivaNoSePuedeHabilitar` |
| | FA-3 5 intentos | `AuthController`, `BloqueoFicticio` | `BloqueoCuentaIT#cincoFallosBloqueanLaCuentaYLaContrasenaCorrectaYaNoSirve`, `#unBloqueoVencidoYaNoImpideEntrar` |
| | FA-4 inicio/cierre | `AuthController` | `BloqueoCuentaIT#elInicioDeSesionDevuelveTokenPermisosYCargo`; `RefreshTokenIT#cerrarSesionRevocaElRefresh…`; `JwtYPermisosIT`; SPA `login.test` |
| | FA-5 recuperación | `RecuperacionController` | `RecuperacionContrasenaIT#laRespuestaEsLaMismaExistaONoLaCuenta…`, `#elEnlaceFuncionaUnaSolaVez…`, `#unEnlaceVencidoSeRechaza…`, `#alDefinirLaContrasenaSeRevocanLasSesionesAbiertas`; SPA `recuperacion.test` |
| UC-25 | Básico (registro automático, inmutable, consulta, filtros, exportación) | `@Auditable`, `AspectoAuditable`, `AuditoriaController` | `AspectoAuditableIT`; `AuditoriaInmutableIT`; `ConsultaAuditoriaIT#filtraPorModuloAccionYUsuario…`; `AuditoriaConsultaExportacionIT#exportarAExcel…`, `#exportarACsv…`; SPA `auditoria.test` |

Observaciones sobre los UC:
- UC-25 pide el registro «en un máximo de 2 segundos» (RNF-08): se escribe en la misma transacción; no hay prueba con medición de tiempo.
- La tabla de cobertura de `AVANCE.md` rotula UC-10 «Reporte de la sección», UC-11 «Reporte general» y UC-21 «Gestionar horario». En la ERS son «Exportar Reportes de Asistencia», «Calcular Resumen Mensual de Asistencia» y «Elaborar Horario». Las clases listadas sí corresponden al contenido de la ERS. P3.

### 3.2 Requisitos funcionales

Las clases de implementación y prueba son las de la tabla «Cobertura RF / UC» de `AVANCE.md`, que contrasté con la lista real de métodos de prueba.

| RF | Estado | Evidencia principal |
|---|---|---|
| RF-01 | Completo | `GestionUsuariosIT`, `UsuariosIT` |
| RF-02 | Completo | `SolicitudApoderadoIT`, `EdicionDatosPersonalesIT` |
| RF-03, RF-04 | Completo | `AnioEscolarIT`, `SeccionesIT`, `VacantesConcurrentesIT`, `MovimientoVacantesTest` |
| RF-05 | Completo | `ConfirmacionMatriculaIT`, `CambioSeccionMatriculaIT`, `ReglasMatriculaTest` |
| RF-06 a RF-11 | Completo | `ConfiguracionCobrosIT`, `ActualizacionObligacionesIT`, `ConsultaObligacionesIT`, `PresentacionComprobanteIT`, `ValidacionPagoIT`, `NumeroOperacionUnicoIT`, `JobVencimientosIT`, `CalculoPenalidadTest`, `HistorialFinancieroIT` |
| RF-12 | Completo | `ConfirmacionMatriculaIT`, `NotificacionesIT`, `NotificadorSmtpIT`, `PlantillasYSesTest` |
| RF-13 a RF-15 | Completo | `ClasificadorAsistenciaDocenteTest`, `PlazoTardanzaTest`, `MarcacionDocenteIT`, `PaseListaConcurrenteIT`, `ActualizacionAsistenciaIT` |
| RF-16, RF-17, RF-21 | Completo | `CalculoResumenMensualTest`, `ReporteAsistenciaIT`, `ReporteMesesPasadosIT`, `ResumenAsistenciaAlumnoIT` |
| RF-18 a RF-20 | Completo | `MarcacionDocenteIT`, `CierreDiarioIT` |
| RF-22 a RF-25 | Completo | `RegistroLogrosIT`, `CursosIT`, `AnioEscolarIT` |
| RF-26 a RF-29, RF-38 | Completo | `ConfiguracionReglaIT`, `ConsolidacionBimestralIT`, `NotasFinalesIT`, estrategias (`*StrategyTest`), `NivelLogroTest` |
| RF-30 a RF-37 | Completo | `ConsultaNotasIT`, `ConsolidacionBimestralIT` |
| RF-39 a RF-47 | Completo | `BorradorHorarioIT`, `ConsultaHorarioIT`, `PublicacionHorarioIT`, `ConfiguracionHorariosIT`, `DisponibilidadDocenteIT`, `BloquesHorariosIT`, `ValidadorConflictosTest`, `ValidadorCompletitudTest` |
| RF-48 a RF-52 | Completo | `GestionRolesIT`, `CacheCuentasIT`, `AspectoAuditableIT`, `EndpointsProtegidosIT`, `JwtYPermisosIT`, `ConsultaAuditoriaIT`, `AuditoriaConsultaExportacionIT`, `BloqueoCuentaIT`, `RefreshTokenIT`, `RecuperacionContrasenaIT` |

Ninguno de los 52 RF queda en **FALTA**.

### 3.3 Requisitos no funcionales

| RNF | Qué se verifica en código | Prueba | Estado |
|---|---|---|---|
| RNF-01 HTTPS y contraseñas cifradas | BCrypt (`ConfiguracionCriptografia`), HSTS y cabeceras (`ConfiguracionSeguridad`) | `CabecerasSeguridadIT#hstsSoloSeEnviaEnPeticionesHttps`; el hash de contraseñas se ejercita en los IT de login | **Parcial:** HTTPS lo termina la infraestructura (AWS), fuera del código |
| RNF-02 Responsive | CSS con `@media` (≤ 640, ≤ 767, ≤ 900 px), matriz móvil | `MatrizHorario.test` (nombre accesible móvil); revisión de la tarea 6.5d | Implementado; sin prueba automatizada de todas las pantallas |
| RNF-03 ≤ 2 s en consultas estándar | Caché de horario y cuentas | Solo la carga k6 de UC-01 (local) | **Sin prueba que mida el tiempo de las consultas estándar.** P3 |
| RNF-04 Navegadores | — | Lista manual en `AVANCE.md` | **Fuera del código:** Edge y Firefox por probar, Safari no probado |
| RNF-05 99 % de disponibilidad | — | — | **Solo se verifica fuera del código** (operación) |
| RNF-06 Bloqueo tras 5 intentos | `AutenticacionService`, `BloqueoFicticio` | `BloqueoCuentaIT`, `BloqueoFicticioTest`, `LimiteAuthIT` | Completo |
| RNF-07 Permisos ≤ 1 s | `@PreAuthorize`, `CacheCuentas` | `EndpointsProtegidosIT`, `JwtYPermisosIT`, `CacheCuentasIT`, `GestionRolesIT` | Implementado y probado en lo funcional; **sin medición de tiempo.** P3 |
| RNF-08 Auditoría ≤ 2 s | `AspectoAuditable` escribe en la misma transacción | `AspectoAuditableIT`, `AuditoriaInmutableIT` | Implementado por diseño; **sin medición de tiempo.** P3 |
| RNF-09 Concurrencia en vacantes | UPDATE condicional | `VacantesConcurrentesIT`, `CambioSeccionMatriculaIT#veinteCambios…`, `MigracionesIT` | Completo |
| RNF-10 Pago en una transacción | `ValidacionPagoService` con `SELECT … FOR UPDATE` | `ValidacionPagoIT#unFalloDespuesDeInsertarLaBoletaRevierteTodo`, `ConfirmacionMatriculaIT#siFallaLaGeneracionDeObligaciones…` | Completo |
| RNF-11 Montos exactos | `Dinero`, `NUMERIC(12,2)` | `NucleoCompartidoTest#dineroUsaEscalaDosYHalfUp`, `CalculoPenalidadTest`, `GeneradorObligacionesTest` | Completo |
| RNF-12 Pico de 200 apoderados | Secuencia V13 y candado por documento | k6 (`docs/despliegue/carga`), `SolicitudApoderadoIT#altasSimultaneas…` | **Medición válida pendiente en QA/AWS.** Local: la solicitud p95 1,59 s; el flujo completo p95 3,40 s en el pico |

## 4. Restos de trabajo incompleto

| Búsqueda | Resultado |
|---|---|
| `TODO`, `FIXME`, `XXX`, `HACK` en `backend/src`, `frontend/src`, `scripts`, `infra`, compose y CI | **0** |
| `UnsupportedOperationException`, «not implemented» | **0** |
| `@Disabled`, `@Ignore`, `it.skip`, `test.skip`, `describe.skip`, `it.todo`, `xit` | **0** |
| Métodos vacíos en `src/main` | **0** |
| Endpoints del backend que la SPA no usa | **2** de 112: `GET /alumnos/{alumnoId}/libretas` y `GET /alumnos/{alumnoId}/cursos/{cursoId}/notas`. La SPA usa en su lugar `GET /alumnos/{id}/libreta` y `/cursos`. Ambos están probados en `ConsultaNotasIT`. P3 (API sin consumidor; no es un fallo) |
| Llamadas de la SPA a endpoints inexistentes | **0** de 78 (la ruta `/comprobantes/{id}/{accion}` resuelve a `observacion` o `rechazo`, que existen) |

El análisis de endpoints compara las rutas de los `@XxxMapping` con los literales de `api(...)` y `descargar(...)` en `frontend/src`. Ignora el verbo HTTP, así que una incoherencia solo de verbo no se detectaría.

## 5. Pendientes declarados y si siguen abiertos

### AVANCE.md

| Pendiente | ¿Sigue abierto? |
|---|---|
| Tarea 6.10: prueba manual en Edge y Firefox; Safari no probado (RNF-04) | **Abierto**, fuera del código. Ninguna casilla de «Navegadores (RNF-04)» está marcada |
| Medición válida de RNF-12 en QA/AWS | **Abierto**, fuera del código. Lo local no basta (p95 sostenido 2,53 s; flujo completo del pico 3,40 s) |
| Primer despliegue compartido (QA/AWS): congelar esquema (`esquema-congelado`), V1 a V11 editables hasta entonces | **Abierto** hasta el primer despliegue |

### PLAN.md § 13 (contradicciones)

C-01 a C-56 tienen resolución anotada. Las que no son «resolución en código» sino de documentación:

| ID | Estado |
|---|---|
| C-14 (la Visión usa la numeración antigua) | Abierta en los documentos; no afecta al código (P-07) |
| C-22 (el Modelo Conceptual no tiene varios atributos y aún muestra Aula) | Abierta en los documentos (P-07). El código sigue el plan |
| C-29 (el wireframe p. 15 no admite TARDANZA en el pase de lista) | El wireframe «se queda como está»; el código admite PUNTUAL, TARDANZA y FALTA (`PaseListaConcurrenteIT#elLoteConLosTresEstados…`) |

### PLAN.md § 14 (supuestos y preguntas)

| ID | Estado | ¿En el código? |
|---|---|---|
| P-04 ¿Hay feriados? (S-07, S-57) | **Abierta** | Sin entidad de feriados: día lectivo = bimestre del año activo con bloques ese día. Si hay feriados, `ClasesDocenteService` y `AsistenciaDocenteService` cuentan esos días como lectivos |
| P-05 Valores institucionales de 5 min / 08:30 / 15:30 | **Abierta** | Configurables (`gesedu.asistencia.*`), validados por `AsistenciaPropertiesTest`; la demo usa los de los wireframes |
| P-06 Confirmar el bloqueo de 15 min (S-03) y el desempate de la moda (S-04) | **Abierta** | Implementados según el supuesto (`BloqueoCuentaIT`, `ModaStrategyTest#elEmpateLoGanaElRegistroMasReciente`) |
| P-07 Actualizar Modelo Conceptual y Visión | **Abierta**, solo documentación | — |
| P-08 Fecha de inicio de la fase 0 completa | **Obsoleta:** las fases 0 a 6 ya están hechas | — |
| P-01, P-02, P-03 | Resueltas | — |
| S-01 a S-86 | Aplicados en el código; solo S-03 y S-04 están marcados como por confirmar con la institución | — |

Observación documental: `PLAN.md` § 14 omite S-64, S-65, S-75, S-76 y S-78 a S-81. Los dos primeros aparecen en `AVANCE.md`; el resto no aparece en ninguno de los dos documentos. Prioridad P3 (de numeración; no afecta al comportamiento).

## 6. Prioridades de lo que falta

| Prioridad | Qué | Dónde |
|---|---|---|
| **P1** | Publicar `920684d` (`main` va 1 commit por delante de `origin/main`) | git |
| **P1 (fuera del código)** | Medición válida de RNF-12 en QA/AWS | `docs/despliegue/carga/README.md` |
| **P1 (fuera del código)** | Prueba manual en Edge y Firefox (RNF-04); decidir qué se dice de Safari | `AVANCE.md` tarea 6.10 |
| P2 | Confirmar con la institución P-04, P-05 y P-06 (feriados, horas de asistencia, duración del bloqueo, desempate de la moda) | `PLAN.md` § 14 |
| P3 | Pruebas con medición de tiempo para RNF-03, RNF-07 y RNF-08 | pruebas |
| P3 | Corregir los títulos de UC-10, UC-11 y UC-21 en la tabla de `AVANCE.md`, y la numeración de S-xx | docs |
| P3 | Actualizar Modelo Conceptual y Visión (P-07) | docs |
| P3 | Decidir si se quitan o se usan `GET /alumnos/{id}/libretas` y `/cursos/{cursoId}/notas` | API |
