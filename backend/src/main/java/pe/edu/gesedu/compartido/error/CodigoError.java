package pe.edu.gesedu.compartido.error;

import org.springframework.http.HttpStatus;

/**
 * Códigos de error de negocio y su estado HTTP (docs/PLAN.md § 5.2, docs/api/errores.md).
 * Viajan en la propiedad {@code codigo} de la respuesta {@code application/problem+json}.
 */
public enum CodigoError {

    // 400
    VALIDACION_INVALIDA(HttpStatus.BAD_REQUEST, "La solicitud no es válida"),
    SOLICITUD_INVALIDA(HttpStatus.BAD_REQUEST, "La solicitud no puede procesarse"),
    VERSION_REQUERIDA(HttpStatus.BAD_REQUEST, "Falta la cabecera If-Match con la versión del recurso"),
    TOKEN_RECUPERACION_INVALIDO(HttpStatus.BAD_REQUEST, "El enlace de recuperación no es válido o venció"),

    // 401
    NO_AUTENTICADO(HttpStatus.UNAUTHORIZED, "Debes iniciar sesión"),
    CREDENCIALES_INVALIDAS(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos"),
    CUENTA_BLOQUEADA(HttpStatus.UNAUTHORIZED, "La cuenta está bloqueada temporalmente"),
    CUENTA_INACTIVA(HttpStatus.UNAUTHORIZED, "La cuenta está desactivada"),
    SESION_EXPIRADA(HttpStatus.UNAUTHORIZED, "La sesión expiró; inicia sesión de nuevo"),

    // 403
    ACCESO_DENEGADO(HttpStatus.FORBIDDEN, "No tienes permiso para realizar esta acción"),
    FUERA_DE_ALCANCE(HttpStatus.FORBIDDEN, "El recurso no pertenece a tu alcance"),

    // 404
    RECURSO_NO_ENCONTRADO(HttpStatus.NOT_FOUND, "El recurso solicitado no existe"),
    HORARIO_NO_PUBLICADO(HttpStatus.NOT_FOUND, "El horario aún no está publicado"),

    // 409
    CONFLICTO_CONCURRENCIA(HttpStatus.CONFLICT, "El recurso cambió; recarga e inténtalo de nuevo"),
    SIN_VACANTE(HttpStatus.CONFLICT, "La sección no tiene vacantes disponibles"),
    MATRICULA_DUPLICADA(HttpStatus.CONFLICT, "El alumno ya tiene una matrícula activa en el año"),
    SOLICITUD_EN_TRAMITE(HttpStatus.CONFLICT, "Ya existe una solicitud en trámite para este alumno"),
    DOCUMENTO_DUPLICADO(HttpStatus.CONFLICT, "Ya existe una cuenta registrada con ese documento de identidad"),
    CORREO_DUPLICADO(HttpStatus.CONFLICT, "Ya existe una cuenta registrada con ese correo"),
    CONFLICTO_HORARIO(HttpStatus.CONFLICT, "El horario entra en conflicto con otro existente"),
    ANIO_ESCOLAR_DUPLICADO(HttpStatus.CONFLICT, "Ya existe un año escolar con ese número"),
    CURSO_DUPLICADO(HttpStatus.CONFLICT, "Ya existe un curso con ese nombre en el año escolar"),
    SECCION_DUPLICADA(HttpStatus.CONFLICT, "Ya existe una sección con esa letra en el grado"),
    TUTOR_YA_ASIGNADO(HttpStatus.CONFLICT, "El docente ya es tutor de otra sección del año"),
    DOCENTE_CON_CLASES(HttpStatus.CONFLICT, "El docente tiene clases en el borrador del horario"),
    BLOQUE_EN_USO(HttpStatus.CONFLICT, "El bloque ya se usa en disponibilidades o en horarios"),
    CURSO_EN_USO(HttpStatus.CONFLICT, "El curso o la competencia ya se usa en horarios o evaluaciones"),
    NUMERO_OPERACION_DUPLICADO(HttpStatus.CONFLICT, "El número de operación ya fue presentado"),
    COMPROBANTE_PENDIENTE(HttpStatus.CONFLICT, "La obligación ya tiene un comprobante pendiente de revisión"),
    ASISTENCIA_YA_REGISTRADA(HttpStatus.CONFLICT, "La asistencia ya fue registrada"),

    // 422
    DEUDA_VENCIDA(HttpStatus.UNPROCESSABLE_ENTITY, "El alumno tiene deudas vencidas"),
    NO_ADMITIDO(HttpStatus.UNPROCESSABLE_ENTITY, "El alumno no fue admitido"),
    DOCUMENTACION_INCOMPLETA(HttpStatus.UNPROCESSABLE_ENTITY, "La documentación está incompleta"),
    OBLIGACION_YA_PAGADA(HttpStatus.UNPROCESSABLE_ENTITY, "La obligación ya fue pagada"),
    MATRICULA_NO_VIGENTE(HttpStatus.UNPROCESSABLE_ENTITY, "La matrícula no está vigente"),
    MONTO_INSUFICIENTE(HttpStatus.UNPROCESSABLE_ENTITY, "El monto no cubre la obligación"),
    DOCENTE_NO_ASOCIADO(HttpStatus.UNPROCESSABLE_ENTITY, "El docente no está asociado al curso"),
    DOCENTE_NO_DISPONIBLE(HttpStatus.UNPROCESSABLE_ENTITY, "El docente no está disponible en ese bloque"),
    HORARIO_INCOMPLETO(HttpStatus.UNPROCESSABLE_ENTITY, "El horario está incompleto o excedido"),
    PERIODO_DISPONIBILIDAD_CERRADO(HttpStatus.UNPROCESSABLE_ENTITY,
            "El periodo de registro de disponibilidad está cerrado"),
    DIA_NO_LECTIVO(HttpStatus.UNPROCESSABLE_ENTITY, "La fecha no es un día lectivo"),
    JORNADA_CERRADA(HttpStatus.UNPROCESSABLE_ENTITY, "La jornada escolar ya cerró"),
    SIN_CLASES_PROGRAMADAS(HttpStatus.UNPROCESSABLE_ENTITY, "No hay clases programadas para la fecha"),
    SIN_REGISTROS_ASISTENCIA(HttpStatus.UNPROCESSABLE_ENTITY, "No hay asistencias registradas para el alcance y el periodo"),
    SECCION_SIN_ALUMNOS(HttpStatus.UNPROCESSABLE_ENTITY, "La sección no tiene alumnos matriculados"),
    PLAZO_TARDANZA_EXCEDIDO(HttpStatus.UNPROCESSABLE_ENTITY, "El plazo para cambiar a tardanza venció"),
    RANGO_ANIO_INVALIDO(HttpStatus.UNPROCESSABLE_ENTITY, "El año escolar debe durar entre 10 y 12 meses"),
    BIMESTRES_SUPERPUESTOS(HttpStatus.UNPROCESSABLE_ENTITY, "Los bimestres se superponen o salen del año escolar"),
    BIMESTRE_INCOMPLETO(HttpStatus.UNPROCESSABLE_ENTITY, "El bimestre tiene registros incompletos"),
    SIN_LOGROS_REGISTRADOS(HttpStatus.UNPROCESSABLE_ENTITY, "Aún no hay logros registrados para calcular las notas finales"),
    BIMESTRES_ANTERIORES_PENDIENTES(HttpStatus.UNPROCESSABLE_ENTITY, "Hay bimestres anteriores sin consolidar o desactualizados"),
    BIMESTRE_NO_VIGENTE(HttpStatus.UNPROCESSABLE_ENTITY, "El bimestre no está vigente: ya no admite registro de logros"),
    NIVEL_LOGRO_INVALIDO(HttpStatus.UNPROCESSABLE_ENTITY, "El nivel de logro no es válido"),
    ARCHIVO_INVALIDO(HttpStatus.UNPROCESSABLE_ENTITY, "El archivo no es válido (PDF, JPG o PNG de hasta 5 MB)"),
    VINCULO_ACADEMICO_INEXISTENTE(HttpStatus.UNPROCESSABLE_ENTITY,
            "No existe un vínculo académico para habilitar la cuenta"),
    CAPACIDAD_INSUFICIENTE(HttpStatus.UNPROCESSABLE_ENTITY, "La capacidad no puede ser menor que los estudiantes matriculados"),
    ANIO_CERRADO(HttpStatus.UNPROCESSABLE_ENTITY, "El año escolar está cerrado y ya no admite cambios"),
    PERIODO_INVALIDO(HttpStatus.UNPROCESSABLE_ENTITY, "El periodo solicitado no es válido"),
    CALENDARIO_NO_DISPONIBLE(HttpStatus.UNPROCESSABLE_ENTITY, "No hay calendario u horario disponible para el periodo"),

    // 429 y 500
    LIMITE_PETICIONES_EXCEDIDO(HttpStatus.TOO_MANY_REQUESTS, "Demasiadas solicitudes; inténtalo más tarde"),
    ERROR_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado");

    private final HttpStatus estado;
    private final String titulo;

    CodigoError(HttpStatus estado, String titulo) {
        this.estado = estado;
        this.titulo = titulo;
    }

    public HttpStatus estado() {
        return estado;
    }

    public String titulo() {
        return titulo;
    }
}
