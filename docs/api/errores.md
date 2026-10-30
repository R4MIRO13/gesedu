# Catálogo de errores de la API

Todas las respuestas de error son `application/problem+json` (RFC 9457) con `type` (`https://gesedu.pe/problemas/<codigo>`),
`title`, `status`, `detail`, `instance` y la propiedad `codigo`. Fuente: `docs/PLAN.md` § 5.1 y § 5.2;
implementación: `pe.edu.gesedu.compartido.error.CodigoError`.

## Implementados en la demo del 23/10

| HTTP | `codigo` | Cuándo | Propiedades extra |
|---|---|---|---|
| 400 | `VALIDACION_INVALIDA` | Bean Validation o cuerpo ilegible | `errores[]` con `campo` y `mensaje` |
| 400 | `SOLICITUD_INVALIDA` ¹ | Otro error 4xx del framework (405, 415…) | — |
| 401 | `NO_AUTENTICADO` | Sin token o token inválido | — |
| 401 | `CREDENCIALES_INVALIDAS` | Correo o contraseña incorrectos (misma respuesta si el correo no existe) | — |
| 401 | `CUENTA_BLOQUEADA` | 5 intentos fallidos; 15 min (RNF-06, S-03) | `bloqueadoHasta` (ISO 8601, `-05:00`) en el inicio de sesión |
| 401 | `CUENTA_INACTIVA` | Cuenta desactivada (solo se informa con la contraseña correcta) | — |
| 401 | `SESION_EXPIRADA` | Token de acceso vencido | — |
| 403 | `ACCESO_DENEGADO` | Falta el permiso de `@PreAuthorize` | — |
| 404 | `RECURSO_NO_ENCONTRADO` | Ruta o recurso inexistente | — |
| 409 | `CONFLICTO_HORARIO` | Un bloque horario se superpone con otro del mismo día | `conflictos[]` con los bloques que se cruzan |
| 500 | `ERROR_INTERNO` ¹ | Error no controlado (el detalle no se expone) | — |

¹ No figuran en `docs/PLAN.md` § 5.2; se agregan para que ninguna respuesta de error salga sin `codigo`.

## Catálogo completo (tarea 0.4, `CodigoError`)

Los códigos de abajo ya existen en el enum; cada módulo los empieza a lanzar en su fase.

| HTTP | `codigo` |
|---|---|
| 400 | `VERSION_REQUERIDA` (falta `If-Match`), `TOKEN_RECUPERACION_INVALIDO` |
| 403 | `FUERA_DE_ALCANCE` |
| 404 | `HORARIO_NO_PUBLICADO` |
| 409 | `CONFLICTO_CONCURRENCIA`, `SIN_VACANTE`, `MATRICULA_DUPLICADA`, `SOLICITUD_EN_TRAMITE`, `DOCUMENTO_DUPLICADO`, `CORREO_DUPLICADO`, `NUMERO_OPERACION_DUPLICADO`, `COMPROBANTE_PENDIENTE` ³, `ANIO_ESCOLAR_DUPLICADO` ⁵, `CURSO_DUPLICADO` ⁵, `SECCION_DUPLICADA` ⁵, `TUTOR_YA_ASIGNADO` ⁵, `ASISTENCIA_YA_REGISTRADA` (con el registro existente, C-39) |
| 422 | `DEUDA_VENCIDA`, `NO_ADMITIDO`, `DOCUMENTACION_INCOMPLETA`, `OBLIGACION_YA_PAGADA`, `MATRICULA_NO_VIGENTE` ⁴, `MONTO_INSUFICIENTE`, `DOCENTE_NO_ASOCIADO`, `DOCENTE_NO_DISPONIBLE`, `HORARIO_INCOMPLETO`, `PERIODO_DISPONIBILIDAD_CERRADO`, `DIA_NO_LECTIVO`, `JORNADA_CERRADA`, `SIN_CLASES_PROGRAMADAS`, `PLAZO_TARDANZA_EXCEDIDO`, `RANGO_ANIO_INVALIDO`, `BIMESTRES_SUPERPUESTOS`, `BIMESTRE_INCOMPLETO`, `NIVEL_LOGRO_INVALIDO`, `ARCHIVO_INVALIDO`, `VINCULO_ACADEMICO_INEXISTENTE`, `PERIODO_INVALIDO` ², `CALENDARIO_NO_DISPONIBLE` ² |
| 429 | `LIMITE_PETICIONES_EXCEDIDO` ² (límite de peticiones en `/auth/*`) |

² No figuran en `docs/PLAN.md` § 5.2 (C-42).
³ Agregado en la fase 2 (UC-04): ya hay un comprobante pendiente de revisión para la obligación (índice único parcial `ux_comprobante_pendiente`).
⁴ Agregado en la fase 2 (RF-05): la matrícula está anulada o es de un año cerrado y no admite cambio de sección.
⁵ Agregados en la fase 3 (UC-13, UC-14, RF-03, RF-04): el año escolar, el nombre del curso o la letra de la sección ya existen, o el docente ya es tutor de otra sección del año (`ux_seccion_tutor`). Los de año, curso y sección no están en `docs/PLAN.md` § 5.2 (C-42).
⁶ Agregados en la tarea 3.2: `CURSO_EN_USO` (409; el curso o la competencia ya tiene docentes, cargas, clases o logros, lo decide la llave foránea) y `ANIO_CERRADO` (422; un año cerrado ya no se configura).
⁷ Agregado en la tarea 3.3: `CAPACIDAD_INSUFICIENTE` (422; la capacidad de la sección no puede quedar por debajo de los estudiantes matriculados).
⁸ Agregados en la tarea 3.4: `DOCENTE_CON_CLASES` (409; el docente tiene clases en el borrador del horario y no se puede deshabilitar ni desasociar de ese curso) y `BLOQUE_EN_USO` (409; el bloque ya está en disponibilidades, clases u horarios publicados).
