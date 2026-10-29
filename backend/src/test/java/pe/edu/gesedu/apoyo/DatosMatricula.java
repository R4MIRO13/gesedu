package pe.edu.gesedu.apoyo;

import java.sql.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Datos de prueba para UC-02: un año propio con grados 2° y 3°, el cronograma de cobros de 11 compromisos
 * (S/ 3,850.00), un año anterior cerrado y solicitudes pendientes de alumnos nuevos o recurrentes.
 */
public final class DatosMatricula {

    private static final AtomicInteger SIGUIENTE_ANIO = new AtomicInteger(2400);
    // Años cerrados anteriores a cualquier escenario (2350 a 2399)
    private static final AtomicInteger SIGUIENTE_ANTERIOR = new AtomicInteger(2350);
    // Empieza lejos de la secuencia real (matricula.seq_solicitud), que usa UC-01 en las pruebas
    private static final AtomicInteger SIGUIENTE_SOLICITUD = new AtomicInteger(50000);

    /** {@code seccion3A} arranca con las vacantes pedidas; {@code seccion3B} con 30. */
    public record Escenario(int anio, String anioId, String anioAnteriorId, String grado3, String grado2,
                            String seccion3A, String seccion3B, String seccion2A) {
    }

    /** Una solicitud pendiente con sus ids y el correo del apoderado (donde llega el aviso). */
    public record Solicitud(String codigo, String id, String alumnoId, String apoderadoId, String correoApoderado) {
    }

    private final JdbcTemplate jdbc;

    public DatosMatricula(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }

    /** Año planificado propio, sin grados ni cobros. */
    public record AnioNuevo(int anio, String id) {
    }

    public AnioNuevo crearAnioSinCobros() {
        int anio = SIGUIENTE_ANIO.getAndIncrement();
        return new AnioNuevo(anio, anio(anio, "PLANIFICADO"));
    }

    public Escenario crearEscenario(int vacantes3A) {
        int anio = SIGUIENTE_ANIO.getAndIncrement();
        String anioId = anio(anio, "PLANIFICADO");
        String anioAnteriorId = anio(SIGUIENTE_ANTERIOR.getAndIncrement(), "CERRADO");
        String grado3 = grado(anioId, 3);
        String grado2 = grado(anioId, 2);
        String seccion3A = seccion(anioId, grado3, "A", vacantes3A);
        String seccion3B = seccion(anioId, grado3, "B", 30);
        String seccion2A = seccion(anioId, grado2, "A", 30);

        jdbc.update("INSERT INTO pagos.cronograma_cobro (id, anio_escolar_id, concepto, periodo, monto, fecha_vencimiento) "
                + "VALUES (?, ?, 'MATRICULA', ?, 350.00, ?)", id(), anioId, String.valueOf(anio),
                Date.valueOf(anio + "-02-28"));
        for (int mes = 3; mes <= 12; mes++) {
            jdbc.update("INSERT INTO pagos.cronograma_cobro (id, anio_escolar_id, concepto, periodo, monto, "
                    + "fecha_vencimiento) VALUES (?, ?, 'PENSION', ?, 350.00, ?)", id(), anioId,
                    "%d-%02d".formatted(anio, mes), Date.valueOf("%d-%02d-05".formatted(anio, mes)));
        }
        jdbc.update("INSERT INTO pagos.politica_cobro (id, anio_escolar_id, porcentaje_penalidad) VALUES (?, ?, 5.00)",
                id(), anioId);
        return new Escenario(anio, anioId, anioAnteriorId, grado3, grado2, seccion3A, seccion3B, seccion2A);
    }

    /** Solicitud pendiente de 3° con documentación completa. {@code nuevo}: nuevo ingreso admitido; si no, recurrente. */
    public Solicitud solicitud(Escenario e, boolean nuevo) {
        return solicitud(e, e.grado3(), nuevo ? "NUEVO_INGRESO" : "RECURRENTE", nuevo ? "ADMITIDO" : "NO_APLICA", "COMPLETA");
    }

    public Solicitud solicitud(Escenario e, String gradoId, String tipo, String admision, String documentacion) {
        String sufijo = id().substring(0, 8);
        String correo = "ap-" + sufijo + "@test.gesedu.pe";
        String usuarioApoderado = usuario("Apoderado", "Prueba " + sufijo, "APODERADO", correo);
        String apoderadoId = id();
        jdbc.update("INSERT INTO seguridad.apoderado (id, usuario_id) VALUES (?, ?)", apoderadoId, usuarioApoderado);
        String usuarioAlumno = usuario("Alumno", "Prueba " + sufijo, "ALUMNO", null);
        String alumnoId = id();
        jdbc.update("INSERT INTO seguridad.alumno (id, usuario_id, codigo_alumno, fecha_nacimiento, apoderado_id, "
                + "parentesco_apoderado) VALUES (?, ?, ?, DATE '2012-01-01', ?, 'MADRE')", alumnoId, usuarioAlumno,
                "T" + sufijo, apoderadoId);

        String solicitudId = id();
        String codigo = "MAT-%d-%05d".formatted(e.anio(), SIGUIENTE_SOLICITUD.getAndIncrement());
        jdbc.update("INSERT INTO matricula.solicitud_matricula (id, codigo_seguimiento, anio_escolar_id, alumno_id, "
                + "apoderado_id, grado_id, fecha_solicitud, tipo_estudiante, estado_documentacion, estado_admision) "
                + "VALUES (?, ?, ?, ?, ?, ?, DATE '2027-01-10', ?, ?, ?)", solicitudId, codigo, e.anioId(), alumnoId,
                apoderadoId, gradoId, tipo, documentacion, admision);
        jdbc.update("INSERT INTO matricula.documento_solicitud (id, solicitud_id, tipo, clave_objeto, nombre_archivo, "
                + "content_type, tamano_bytes) VALUES (?, ?, 'CERTIFICADO_ESTUDIOS', ?, 'certificado.pdf', "
                + "'application/pdf', 1024)", id(), solicitudId, "solicitudes/" + codigo + "/certificado.pdf");
        return new Solicitud(codigo, solicitudId, alumnoId, apoderadoId, correo);
    }

    /** Matrícula confirmada del alumno en el año del escenario (para provocar la duplicidad, FA-2). */
    public void matricularYa(Escenario e, Solicitud s, String seccionId) {
        String previa = id();
        jdbc.update("INSERT INTO matricula.solicitud_matricula (id, codigo_seguimiento, anio_escolar_id, alumno_id, "
                + "apoderado_id, grado_id, fecha_solicitud, tipo_estudiante, estado, costos_aceptados) "
                + "VALUES (?, ?, ?, ?, ?, ?, DATE '2027-01-05', 'RECURRENTE', 'APROBADA', TRUE)", previa,
                "P-" + previa.substring(0, 12), e.anioId(), s.alumnoId(), s.apoderadoId(), e.grado3());
        jdbc.update("INSERT INTO matricula.matricula (id, solicitud_id, alumno_id, anio_escolar_id, seccion_id, "
                + "fecha_matricula) VALUES (?, ?, ?, ?, ?, DATE '2027-01-06')", id(), previa, s.alumnoId(),
                e.anioId(), seccionId);
    }

    /**
     * Deuda de una pensión vencida del año anterior (FA-3): matrícula del año anterior y una obligación impaga con
     * vencimiento hace tiempo. Devuelve el monto.
     */
    public String deudaDelAnioAnterior(Escenario e, Solicitud s) {
        String grado = grado(e.anioAnteriorId(), 3);
        String seccion = seccion(e.anioAnteriorId(), grado, "A", 29);
        String previa = id();
        jdbc.update("INSERT INTO matricula.solicitud_matricula (id, codigo_seguimiento, anio_escolar_id, alumno_id, "
                + "apoderado_id, grado_id, fecha_solicitud, tipo_estudiante, estado, costos_aceptados) "
                + "VALUES (?, ?, ?, ?, ?, ?, DATE '2026-01-05', 'RECURRENTE', 'APROBADA', TRUE)", previa,
                "P-" + previa.substring(0, 12), e.anioAnteriorId(), s.alumnoId(), s.apoderadoId(), grado);
        String matricula = id();
        jdbc.update("INSERT INTO matricula.matricula (id, solicitud_id, alumno_id, anio_escolar_id, seccion_id, "
                + "fecha_matricula) VALUES (?, ?, ?, ?, ?, DATE '2026-01-06')", matricula, previa, s.alumnoId(),
                e.anioAnteriorId(), seccion);
        jdbc.update("INSERT INTO pagos.obligacion_pago (id, matricula_id, anio_escolar_id, alumno_id, apoderado_id, "
                + "concepto, periodo, monto_base, fecha_vencimiento, estado) VALUES (?, ?, ?, ?, ?, 'PENSION', "
                + "'2026-03', 350.00, DATE '2026-03-05', 'VENCIDA')", id(), matricula, e.anioAnteriorId(),
                s.alumnoId(), s.apoderadoId());
        return "350.00";
    }

    private String anio(int anio, String estado) {
        String id = id();
        jdbc.update("INSERT INTO academico.anio_escolar (id, anio, fecha_inicio, fecha_fin, estado) VALUES (?, ?, ?, ?, ?)",
                id, anio, Date.valueOf(anio + "-02-15"), Date.valueOf(anio + "-12-20"), estado);
        return id;
    }

    private String grado(String anioId, int nivel) {
        String id = id();
        jdbc.update("INSERT INTO academico.grado (id, anio_escolar_id, numero_nivel) VALUES (?, ?, ?)", id, anioId, nivel);
        return id;
    }

    private String seccion(String anioId, String gradoId, String letra, int vacantes) {
        String id = id();
        jdbc.update("INSERT INTO academico.seccion (id, grado_id, anio_escolar_id, letra, capacidad, vacantes_disponibles) "
                + "VALUES (?, ?, ?, ?, 30, ?)", id, gradoId, anioId, letra, vacantes);
        return id;
    }

    private String usuario(String nombres, String apellidos, String rol, String correo) {
        String id = id();
        jdbc.update("INSERT INTO seguridad.usuario (id, nombres, apellidos, documento_identidad, correo, activo, "
                + "rol_acceso_id) SELECT ?, ?, ?, ?, ?, FALSE, r.id FROM seguridad.rol_acceso r WHERE r.codigo = ?",
                id, nombres, apellidos, id.substring(0, 20), correo, rol);
        return id;
    }
}
