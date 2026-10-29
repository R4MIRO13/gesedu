package pe.edu.gesedu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import pe.edu.gesedu.apoyo.BaseIT;

/**
 * El esquema completo (V1 a V11) con las restricciones que sostienen las reglas críticas: vacantes (RNF-09),
 * bimestres sin cruce (UC-13), número de operación (RF-08), horario publicado inmutable (RF-44) y unicidades.
 */
class MigracionesIT extends BaseIT {

    // Cada prueba usa su propio año (2040+) para no chocar con otras pruebas ni con la semilla
    private static final AtomicInteger SIGUIENTE_ANIO = new AtomicInteger(2040);

    private static String id() {
        return UUID.randomUUID().toString();
    }

    private String crearAnio() {
        String anio = id();
        int numero = SIGUIENTE_ANIO.getAndIncrement();
        jdbc.update("INSERT INTO academico.anio_escolar (id, anio, fecha_inicio, fecha_fin, estado) VALUES (?, ?, ?, ?, 'PLANIFICADO')",
                anio, numero, java.sql.Date.valueOf(numero + "-02-01"), java.sql.Date.valueOf(numero + "-12-15"));
        return anio;
    }

    private String crearGrado(String anio, int nivel) {
        String grado = id();
        jdbc.update("INSERT INTO academico.grado (id, anio_escolar_id, numero_nivel) VALUES (?, ?, ?)", grado, anio, nivel);
        return grado;
    }

    private String crearSeccion(String anio, String grado, String letra, int capacidad, int vacantes) {
        String seccion = id();
        jdbc.update("""
                INSERT INTO academico.seccion (id, grado_id, anio_escolar_id, letra, capacidad, vacantes_disponibles)
                VALUES (?, ?, ?, ?, ?, ?)""", seccion, grado, anio, letra, capacidad, vacantes);
        return seccion;
    }

    private String crearApoderado() {
        String usuario = idDe(crearUsuario("APODERADO", true));
        String apoderado = id();
        jdbc.update("INSERT INTO seguridad.apoderado (id, usuario_id) VALUES (?, ?)", apoderado, usuario);
        return apoderado;
    }

    private String crearAlumno(String apoderado) {
        String usuario = idDe(crearUsuario("ALUMNO", true));
        String alumno = id();
        jdbc.update("""
                INSERT INTO seguridad.alumno (id, usuario_id, codigo_alumno, fecha_nacimiento, apoderado_id, parentesco_apoderado)
                VALUES (?, ?, ?, DATE '2012-05-20', ?, 'MADRE')""", alumno, usuario, "A" + alumno.substring(0, 8), apoderado);
        return alumno;
    }

    private String crearDocente() {
        String usuario = idDe(crearUsuario("DOCENTE", true));
        String docente = id();
        jdbc.update("INSERT INTO seguridad.docente (id, usuario_id, codigo_docente) VALUES (?, ?, ?)",
                docente, usuario, "D" + docente.substring(0, 8));
        return docente;
    }

    /** Matrícula confirmada con una obligación; devuelve {obligacionId, apoderadoId}. */
    private String[] crearObligacion() {
        String anio = crearAnio();
        String seccion = crearSeccion(anio, crearGrado(anio, 3), "A", 30, 29);
        String apoderado = crearApoderado();
        String alumno = crearAlumno(apoderado);
        String grado = jdbc.queryForObject("SELECT grado_id FROM academico.seccion WHERE id = ?", String.class, seccion);
        String solicitud = id();
        jdbc.update("""
                INSERT INTO matricula.solicitud_matricula (id, codigo_seguimiento, anio_escolar_id, alumno_id, apoderado_id,
                    grado_id, fecha_solicitud, tipo_estudiante, estado)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_DATE, 'RECURRENTE', 'APROBADA')""",
                solicitud, "MAT-T-" + solicitud.substring(0, 8), anio, alumno, apoderado, grado);
        String matricula = id();
        jdbc.update("""
                INSERT INTO matricula.matricula (id, solicitud_id, alumno_id, anio_escolar_id, seccion_id, fecha_matricula)
                VALUES (?, ?, ?, ?, ?, CURRENT_DATE)""", matricula, solicitud, alumno, anio, seccion);
        String obligacion = id();
        jdbc.update("""
                INSERT INTO pagos.obligacion_pago (id, matricula_id, anio_escolar_id, alumno_id, apoderado_id, concepto,
                    periodo, monto_base, fecha_vencimiento)
                VALUES (?, ?, ?, ?, ?, 'MATRICULA', 'UNICO', 350.00, DATE '2027-02-15')""",
                obligacion, matricula, anio, alumno, apoderado);
        return new String[] {obligacion, apoderado};
    }

    private void insertarComprobante(String obligacion, String apoderado, String operacion, String estado) {
        jdbc.update("""
                INSERT INTO pagos.comprobante_pago (id, obligacion_id, apoderado_id, numero_operacion, fecha_pago,
                    monto_presentado, medio_pago, referencia_comprobante, estado_revision)
                VALUES (?, ?, ?, ?, CURRENT_DATE, 350.00, 'TRANSFERENCIA', 'clave/objeto', ?)""",
                id(), obligacion, apoderado, operacion, estado);
    }

    @Test
    void existenLasTablasDeTodosLosModulos() {
        List<String> esperadas = List.of(
                "seguridad.usuario", "seguridad.docente", "seguridad.apoderado", "seguridad.alumno",
                "seguridad.refresh_token", "seguridad.token_recuperacion",
                "academico.anio_escolar", "academico.bimestre", "academico.grado", "academico.seccion",
                "academico.curso", "academico.competencia",
                "pagos.cronograma_cobro", "pagos.politica_cobro", "pagos.obligacion_pago", "pagos.comprobante_pago",
                "pagos.registro_pago", "matricula.solicitud_matricula", "matricula.documento_solicitud",
                "matricula.matricula", "horarios.bloque_horario", "horarios.periodo_registro_disponibilidad",
                "horarios.vinculacion_docente", "horarios.asociacion_docente_curso", "horarios.carga_horaria_curso",
                "horarios.disponibilidad_docente", "horarios.bloque_disponible", "horarios.horario_borrador",
                "horarios.asignacion_horario", "horarios.horario_publicado", "horarios.asignacion_publicada",
                "evaluacion.asignacion_docente", "evaluacion.peso_bimestre", "evaluacion.registro_logro",
                "evaluacion.nota_consolidada", "evaluacion.consolidacion_bimestral", "evaluacion.libreta",
                "asistencia.asistencia_alumno", "asistencia.asistencia_docente", "asistencia.cierre_diario",
                "auditoria.registro_auditoria", "auditoria.cambio_auditoria", "transversal.shedlock");
        for (String tabla : esperadas) {
            assertThat(jdbc.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, tabla))
                    .as(tabla).isTrue();
        }
    }

    @Test
    void lasVacantesNuncaSuperanLaCapacidadNiSonNegativas() {
        String anio = crearAnio();
        String grado = crearGrado(anio, 1);
        assertThatThrownBy(() -> crearSeccion(anio, grado, "A", 30, 31))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_seccion_vacantes");
        assertThatThrownBy(() -> crearSeccion(anio, grado, "B", 30, -1))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_seccion_vacantes");
        String seccion = crearSeccion(anio, grado, "C", 30, 0);
        // UPDATE condicional (RNF-09): con 0 vacantes no afecta ninguna fila
        assertThat(jdbc.update("UPDATE academico.seccion SET vacantes_disponibles = vacantes_disponibles - 1 "
                + "WHERE id = ? AND vacantes_disponibles > 0", seccion)).isZero();
        // Y aun sin la condición, la base no deja bajar de 0
        assertThatThrownBy(() -> jdbc.update("UPDATE academico.seccion SET vacantes_disponibles = vacantes_disponibles - 1 "
                + "WHERE id = ?", seccion)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void losBimestresNoPuedenSuperponerseDentroDelAnio() {
        String anio = crearAnio();
        jdbc.update("INSERT INTO academico.bimestre (id, anio_escolar_id, numero, fecha_inicio, fecha_fin) "
                + "VALUES (?, ?, 1, DATE '2087-02-01', DATE '2087-04-30')", id(), anio);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO academico.bimestre (id, anio_escolar_id, numero, fecha_inicio, fecha_fin) "
                + "VALUES (?, ?, 2, DATE '2087-04-30', DATE '2087-07-15')", id(), anio))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ex_bimestre_sin_cruce");
        jdbc.update("INSERT INTO academico.bimestre (id, anio_escolar_id, numero, fecha_inicio, fecha_fin) "
                + "VALUES (?, ?, 2, DATE '2087-05-01', DATE '2087-07-15')", id(), anio);
    }

    @Test
    void elAnioEscolarDuraEntreDiezYDoceMeses() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO academico.anio_escolar (id, anio, fecha_inicio, fecha_fin, estado) "
                + "VALUES (?, 2097, DATE '2097-03-01', DATE '2097-08-01', 'PLANIFICADO')", id()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_anio_escolar_duracion");
    }

    @Test
    void elNumeroDeOperacionEsUnicoSoloEntreComprobantesPendientesOAprobados() {
        String[] datos = crearObligacion();
        insertarComprobante(datos[0], datos[1], "OP-1", "RECHAZADO");
        insertarComprobante(datos[0], datos[1], "OP-1", "PENDIENTE"); // el rechazado libera el número
        String[] otra = crearObligacion();
        assertThatThrownBy(() -> insertarComprobante(otra[0], otra[1], "OP-1", "PENDIENTE"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ux_comprobante_operacion_vigente");
    }

    @Test
    void unaObligacionTieneAlMaximoUnComprobantePendienteYUnoAprobado() {
        String[] datos = crearObligacion();
        insertarComprobante(datos[0], datos[1], "OP-A", "PENDIENTE");
        assertThatThrownBy(() -> insertarComprobante(datos[0], datos[1], "OP-B", "PENDIENTE"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ux_comprobante_pendiente");
        insertarComprobante(datos[0], datos[1], "OP-C", "APROBADO");
        assertThatThrownBy(() -> insertarComprobante(datos[0], datos[1], "OP-D", "APROBADO"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ux_comprobante_aprobado");
    }

    @Test
    void unAlumnoTieneUnaSolaMatriculaConfirmadaPorAnio() {
        String anio = crearAnio();
        String seccion = crearSeccion(anio, crearGrado(anio, 2), "A", 30, 30);
        String apoderado = crearApoderado();
        String alumno = crearAlumno(apoderado);
        String grado = jdbc.queryForObject("SELECT grado_id FROM academico.seccion WHERE id = ?", String.class, seccion);
        String s1 = id();
        String s2 = id();
        for (String solicitud : new String[] {s1, s2}) {
            jdbc.update("""
                    INSERT INTO matricula.solicitud_matricula (id, codigo_seguimiento, anio_escolar_id, alumno_id, apoderado_id,
                        grado_id, fecha_solicitud, tipo_estudiante, estado)
                    VALUES (?, ?, ?, ?, ?, ?, CURRENT_DATE, 'RECURRENTE', 'APROBADA')""",
                    solicitud, "MAT-U-" + solicitud.substring(0, 8), anio, alumno, apoderado, grado);
        }
        jdbc.update("INSERT INTO matricula.matricula (id, solicitud_id, alumno_id, anio_escolar_id, seccion_id, fecha_matricula) "
                + "VALUES (?, ?, ?, ?, ?, CURRENT_DATE)", id(), s1, alumno, anio, seccion);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO matricula.matricula (id, solicitud_id, alumno_id, anio_escolar_id, seccion_id, fecha_matricula) "
                + "VALUES (?, ?, ?, ?, ?, CURRENT_DATE)", id(), s2, alumno, anio, seccion))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ux_matricula_confirmada");
    }

    @Test
    void soloUnaSolicitudEnTramitePorAlumnoYAnio() {
        String anio = crearAnio();
        String grado = crearGrado(anio, 4);
        String apoderado = crearApoderado();
        String alumno = crearAlumno(apoderado);
        String sql = """
                INSERT INTO matricula.solicitud_matricula (id, codigo_seguimiento, anio_escolar_id, alumno_id, apoderado_id,
                    grado_id, fecha_solicitud, tipo_estudiante)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_DATE, 'NUEVO_INGRESO')""";
        jdbc.update(sql, id(), "MAT-V-1" + id().substring(0, 6), anio, alumno, apoderado, grado);
        assertThatThrownBy(() -> jdbc.update(sql, id(), "MAT-V-2" + id().substring(0, 6), anio, alumno, apoderado, grado))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ux_solicitud_en_tramite");
    }

    @Test
    void unaSolicitudRechazadaExigeMotivo() {
        String anio = crearAnio();
        String grado = crearGrado(anio, 5);
        String apoderado = crearApoderado();
        String alumno = crearAlumno(apoderado);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO matricula.solicitud_matricula (id, codigo_seguimiento, anio_escolar_id, alumno_id, apoderado_id,
                    grado_id, fecha_solicitud, tipo_estudiante, estado)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_DATE, 'RECURRENTE', 'RECHAZADA')""",
                id(), "MAT-W-" + id().substring(0, 6), anio, alumno, apoderado, grado))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_solicitud_rechazo");
    }

    @Test
    void elHorarioPublicadoEsInmutableYSoloUnaVersionEsVigente() {
        String anio = crearAnio();
        String seccion = crearSeccion(anio, crearGrado(anio, 1), "A", 30, 30);
        String docente = crearDocente();
        String publicador = idDe(crearUsuario("ADMINISTRADOR", true));
        String curso = id();
        jdbc.update("INSERT INTO academico.curso (id, anio_escolar_id, nombre, slug, horas_lectivas) VALUES (?, ?, 'Matemática', 'matematica', 5)",
                curso, anio);
        String bloque = id();
        jdbc.update("INSERT INTO horarios.bloque_horario (id, anio_escolar_id, dia_semana, hora_inicio, hora_fin) "
                + "VALUES (?, ?, 'MARTES', TIME '09:15', TIME '10:00')", bloque, anio);

        String v1 = id();
        jdbc.update("INSERT INTO horarios.horario_publicado (id, anio_escolar_id, numero_version, fecha_hora_publicacion, vigente, publicado_por) "
                + "VALUES (?, ?, 1, now(), TRUE, ?)", v1, anio, publicador);
        String asignacion = id();
        jdbc.update("INSERT INTO horarios.asignacion_publicada (id, horario_publicado_id, bloque_id, seccion_id, curso_id, docente_id) "
                + "VALUES (?, ?, ?, ?, ?, ?)", asignacion, v1, bloque, seccion, curso, docente);

        // Otra versión vigente del mismo año: rechazada por el índice único parcial
        assertThatThrownBy(() -> jdbc.update("INSERT INTO horarios.horario_publicado (id, anio_escolar_id, numero_version, fecha_hora_publicacion, vigente, publicado_por) "
                + "VALUES (?, ?, 2, now(), TRUE, ?)", id(), anio, publicador))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ux_horario_publicado_vigente");

        // Las asignaciones publicadas no se modifican ni se borran
        assertThatThrownBy(() -> jdbc.update("UPDATE horarios.asignacion_publicada SET curso_id = curso_id WHERE id = ?", asignacion))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("inmutables");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM horarios.asignacion_publicada WHERE id = ?", asignacion))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("inmutables");
        // Del horario solo puede cambiar la marca "vigente"
        assertThatThrownBy(() -> jdbc.update("UPDATE horarios.horario_publicado SET numero_version = 9 WHERE id = ?", v1))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("inmutables");
        jdbc.update("UPDATE horarios.horario_publicado SET vigente = FALSE WHERE id = ?", v1);
        jdbc.update("INSERT INTO horarios.horario_publicado (id, anio_escolar_id, numero_version, fecha_hora_publicacion, vigente, publicado_por) "
                + "VALUES (?, ?, 2, now(), TRUE, ?)", id(), anio, publicador);
    }

    @Test
    void elBorradorNoAdmiteCrucesDeDocenteNiDeSeccion() {
        String anio = crearAnio();
        String grado = crearGrado(anio, 1);
        String seccionA = crearSeccion(anio, grado, "A", 30, 30);
        String seccionB = crearSeccion(anio, grado, "B", 30, 30);
        String docente1 = crearDocente();
        String docente2 = crearDocente();
        String curso = id();
        jdbc.update("INSERT INTO academico.curso (id, anio_escolar_id, nombre, slug, horas_lectivas) VALUES (?, ?, 'Inglés', 'ingles', 3)",
                curso, anio);
        String bloque = id();
        jdbc.update("INSERT INTO horarios.bloque_horario (id, anio_escolar_id, dia_semana, hora_inicio, hora_fin) "
                + "VALUES (?, ?, 'LUNES', TIME '07:45', TIME '08:30')", bloque, anio);
        String borrador = id();
        jdbc.update("INSERT INTO horarios.horario_borrador (id, anio_escolar_id, fecha_creacion, fecha_actualizacion) "
                + "VALUES (?, ?, now(), now())", borrador, anio);
        String asociacion1 = asociar(anio, docente1, curso);
        String asociacion2 = asociar(anio, docente2, curso);

        asignar(borrador, bloque, seccionA, curso, docente1, asociacion1);
        // Mismo docente en el mismo bloque, otra sección
        assertThatThrownBy(() -> asignar(borrador, bloque, seccionB, curso, docente1, asociacion1))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ux_asignacion_docente_bloque");
        // Misma sección en el mismo bloque, otro docente
        assertThatThrownBy(() -> asignar(borrador, bloque, seccionA, curso, docente2, asociacion2))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ux_asignacion_seccion_bloque");
    }

    private String asociar(String anio, String docente, String curso) {
        String vinculacion = id();
        jdbc.update("INSERT INTO horarios.vinculacion_docente (id, docente_id, anio_escolar_id) VALUES (?, ?, ?)",
                vinculacion, docente, anio);
        String asociacion = id();
        jdbc.update("INSERT INTO horarios.asociacion_docente_curso (id, vinculacion_id, curso_id) VALUES (?, ?, ?)",
                asociacion, vinculacion, curso);
        return asociacion;
    }

    private void asignar(String borrador, String bloque, String seccion, String curso, String docente, String asociacion) {
        jdbc.update("INSERT INTO horarios.asignacion_horario (id, horario_borrador_id, bloque_id, seccion_id, curso_id, "
                + "docente_id, asociacion_docente_curso_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id(), borrador, bloque, seccion, curso, docente, asociacion);
    }

    @Test
    void laAsistenciaEsUnicaPorPersonaYFechaYLaFaltaDocenteNoTieneHora() {
        String docente = crearDocente();
        jdbc.update("INSERT INTO asistencia.asistencia_docente (id, docente_id, fecha, hora_registro, estado) "
                + "VALUES (?, ?, DATE '2027-03-01', now(), 'PUNTUAL')", id(), docente);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO asistencia.asistencia_docente (id, docente_id, fecha, estado) "
                + "VALUES (?, ?, DATE '2027-03-01', 'FALTA')", id(), docente))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ux_asistencia_docente");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO asistencia.asistencia_docente (id, docente_id, fecha, hora_registro, estado) "
                + "VALUES (?, ?, DATE '2027-03-02', now(), 'FALTA')", id(), docente))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_asistencia_docente_hora");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO asistencia.asistencia_docente (id, docente_id, fecha, estado) "
                + "VALUES (?, ?, DATE '2027-03-03', 'PUNTUAL')", id(), docente))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_asistencia_docente_hora");
    }

    @Test
    void laAplicacionPuedeEscribirEnShedlockYNoBorraUsuarios() {
        jdbc.update("INSERT INTO transversal.shedlock (name, lock_until, locked_at, locked_by) "
                + "VALUES (?, now(), now(), 'prueba')", "job-" + id().substring(0, 8));
        assertThatThrownBy(() -> jdbc.update("DELETE FROM seguridad.usuario WHERE id = ?", id()))
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("permission denied");
    }
}
