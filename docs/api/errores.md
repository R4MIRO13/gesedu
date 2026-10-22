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
| 500 | `ERROR_INTERNO` ¹ | Error no controlado (el detalle no se expone) | — |

¹ No figuran en `docs/PLAN.md` § 5.2; se agregan para que ninguna respuesta de error salga sin `codigo`.

El resto de códigos del plan (409, 422 y los demás) se agregan con cada módulo.
