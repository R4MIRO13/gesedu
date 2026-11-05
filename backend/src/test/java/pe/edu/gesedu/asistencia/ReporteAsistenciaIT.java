package pe.edu.gesedu.asistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;

import pe.edu.gesedu.apoyo.BaseIT;
import pe.edu.gesedu.apoyo.DatosHorario;
import pe.edu.gesedu.apoyo.DatosMatricula;
import pe.edu.gesedu.apoyo.DatosMatricula.Escenario;
import pe.edu.gesedu.apoyo.RelojPrueba;
import pe.edu.gesedu.apoyo.RelojPruebaConfig;
import pe.edu.gesedu.asistencia.infraestructura.AsistenciaProperties;

/**
 * UC-10 y UC-11, reportes mensuales de asistencia (RF-16, RF-17, RF-21): calculados al pedirlos, con el alcance por rol, el
 * periodo en curso como parcial, los días sin registro aparte y los tres formatos. Reloj fijo en la zona de Lima.
 * <p>
 * Escenario: año ACTIVO con jornada los lunes y miércoles; 3° A (tutora Rosa) con tres alumnos, 3° B (tutor Luis) con uno.
 * Rosa dicta lunes y miércoles; Carmen solo los lunes. Los registros de marzo se siembran con conteos conocidos.
 */
@Import(RelojPruebaConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReporteAsistenciaIT extends BaseIT {

    private static final String RUTA = "/api/v1/reportes/asistencia";

    @Autowired
    private RelojPrueba reloj;

    @Autowired
    private AsistenciaProperties propiedades;

    private Escenario e;
    private String rosa;
    private String luis;
    private String carmen;
    private String alva;
    private String bravo;
    private String castro;
    private String ramos;
    private String horarioId;
    private List<LocalDate> lectivos;
    private final Map<String, String> tokens = new HashMap<>();

    @BeforeAll
    void escenario() {
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'PLANIFICADO' WHERE estado = 'ACTIVO'");
        DatosMatricula datos = new DatosMatricula(jdbc);
        e = datos.crearEscenario(30);
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'ACTIVO' WHERE id = ?", e.anioId());
        String[][] bimestres = {{"1", "02-15", "04-30"}, {"2", "05-01", "07-15"}, {"3", "07-16", "09-30"}, {"4", "10-01", "12-20"}};
        for (String[] b : bimestres) {
            jdbc.update("INSERT INTO academico.bimestre (id, anio_escolar_id, numero, fecha_inicio, fecha_fin) "
                    + "VALUES (?, ?, ?, ?::date, ?::date)", UUID.randomUUID().toString(), e.anioId(), Integer.parseInt(b[0]),
                    e.anio() + "-" + b[1], e.anio() + "-" + b[2]);
        }
        String lunes = bloque("LUNES");
        String miercoles = bloque("MIERCOLES");
        String curso = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO academico.curso (id, anio_escolar_id, nombre, slug, horas_lectivas) VALUES (?, ?, 'Matemática', 'matematica', 5)",
                curso, e.anioId());

        DatosHorario docentes = new DatosHorario(jdbc);
        rosa = docentes.docente("Rosa", "Quispe");
        luis = docentes.docente("Luis", "Castro");
        carmen = docentes.docente("Carmen", "Villanueva");
        jdbc.update("UPDATE academico.seccion SET tutor_docente_id = ? WHERE id = ?", rosa, e.seccion3A());
        jdbc.update("UPDATE academico.seccion SET tutor_docente_id = ? WHERE id = ?", luis, e.seccion3B());
        String admin = idDe(crearUsuario("ADMINISTRADOR", true));
        horarioId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO horarios.horario_publicado (id, anio_escolar_id, numero_version, fecha_hora_publicacion, vigente, "
                + "publicado_por) VALUES (?, ?, 1, now(), TRUE, ?)", horarioId, e.anioId(), admin);
        clase(lunes, e.seccion3A(), rosa, curso);
        clase(miercoles, e.seccion3A(), rosa, curso);
        clase(lunes, e.seccion3B(), carmen, curso);

        alva = alumno(e.seccion3A(), "Alva", "Lucía");
        bravo = alumno(e.seccion3A(), "Bravo", "Tomás");
        castro = alumno(e.seccion3A(), "Castro", "Mateo");
        ramos = alumno(e.seccion3B(), "Ramos", "Pedro");

        lectivos = new ArrayList<>();
        for (LocalDate d = marzo(1); d.getMonthValue() == 3; d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.MONDAY || d.getDayOfWeek() == DayOfWeek.WEDNESDAY) {
                lectivos.add(d);
            }
        }
        // Alva: 3 puntuales, 1 tardanza, 1 falta · Bravo: sin registros · Castro: todos puntuales · Ramos: 1 falta y 1 puntual
        for (int i = 0; i < lectivos.size(); i++) {
            LocalDate d = lectivos.get(i);
            if (i < 3) {
                registroAlumno(alva, d, "PUNTUAL");
            } else if (i == 3) {
                registroAlumno(alva, d, "TARDANZA");
            } else if (i == 4) {
                registroAlumno(alva, d, "FALTA");
            }
            registroAlumno(castro, d, "PUNTUAL");
            if (i < 2) {
                registroAlumno(ramos, d, i == 0 ? "FALTA" : "PUNTUAL");
            }
        }
        // Rosa: 2 puntuales, 1 tardanza, 1 falta del cierre (sin hora) y el resto sin registro · Carmen: puntual todos los lunes
        for (int i = 0; i < lectivos.size(); i++) {
            LocalDate d = lectivos.get(i);
            if (i < 2) {
                registroDocente(rosa, d, "PUNTUAL", "07:40");
            } else if (i == 2) {
                registroDocente(rosa, d, "TARDANZA", "07:58");
            } else if (i == 3) {
                registroDocente(rosa, d, "FALTA", null);
            }
            if (d.getDayOfWeek() == DayOfWeek.MONDAY) {
                registroDocente(carmen, d, "PUNTUAL", "07:41");
            }
        }
    }

    @AfterAll
    void devolverElAnio() {
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'PLANIFICADO' WHERE id = ?", e.anioId());
    }

    // ---- datos y ayudas ----

    private LocalDate marzo(int dia) {
        return LocalDate.of(e.anio(), 3, dia);
    }

    private String bloque(String dia) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO horarios.bloque_horario (id, anio_escolar_id, dia_semana, hora_inicio, hora_fin) "
                + "VALUES (?, ?, ?, '07:45', '08:30')", id, e.anioId(), dia);
        return id;
    }

    private void clase(String bloqueId, String seccionId, String docenteId, String cursoId) {
        jdbc.update("INSERT INTO horarios.asignacion_publicada (id, horario_publicado_id, bloque_id, seccion_id, curso_id, docente_id) "
                + "VALUES (?, ?, ?, ?, ?, ?)", UUID.randomUUID().toString(), horarioId, bloqueId, seccionId, cursoId, docenteId);
    }

    private String alumno(String seccionId, String apellidos, String nombres) {
        DatosMatricula datos = new DatosMatricula(jdbc);
        DatosMatricula.Solicitud s = datos.solicitud(e, false);
        datos.matricularYa(e, s, seccionId);
        jdbc.update("UPDATE seguridad.usuario SET apellidos = ?, nombres = ? WHERE id = (SELECT usuario_id FROM seguridad.alumno WHERE id = ?)",
                apellidos, nombres, s.alumnoId());
        return s.alumnoId();
    }

    private void registroAlumno(String alumnoId, LocalDate fecha, String estado) {
        jdbc.update("INSERT INTO asistencia.asistencia_alumno (id, alumno_id, fecha, estado) VALUES (?, ?, ?, ?)",
                UUID.randomUUID().toString(), alumnoId, Date.valueOf(fecha), estado);
    }

    private void registroDocente(String docenteId, LocalDate fecha, String estado, String hora) {
        Timestamp marca = hora == null ? null : Timestamp.from(fecha.atTime(LocalTime.parse(hora)).atZone(reloj.getZone()).toInstant());
        jdbc.update("INSERT INTO asistencia.asistencia_docente (id, docente_id, fecha, hora_registro, estado) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), docenteId, Date.valueOf(fecha), marca, estado);
    }

    private String token(String docenteId) throws Exception {
        String cacheado = tokens.get(docenteId);
        if (cacheado != null) {
            return cacheado;
        }
        String usuario = jdbc.queryForObject("SELECT usuario_id FROM seguridad.docente WHERE id = ?", String.class, docenteId);
        String correo = "reporte-" + usuario.substring(0, 8) + "@test.gesedu.pe";
        jdbc.update("UPDATE seguridad.usuario SET correo = ?, hash_contrasena = ?, activo = TRUE WHERE id = ?", correo,
                codificador.encode(CONTRASENA), usuario);
        invalidarCuentas();
        String token = tokenDe(correo);
        tokens.put(docenteId, token);
        return token;
    }

    private String admin() throws Exception {
        return tokens.computeIfAbsent("admin", k -> {
            try {
                return tokenDe(crearUsuario("ADMINISTRADOR", true));
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    /** Después de fin de mes: marzo completo. */
    private void abril() {
        reloj.fijar(LocalDate.of(e.anio(), 4, 10), "10:00");
    }

    private String consulta(String ambito, int mes, String seccion, String formato) {
        return RUTA + "?ambito=" + ambito + "&mes=" + mes + "&anio=" + e.anio() + (seccion == null ? "" : "&seccion=" + seccion)
                + (formato == null ? "" : "&formato=" + formato);
    }

    private static JsonNode fila(JsonNode reporte, String parteDelNombre) {
        for (JsonNode f : reporte.get("filas")) {
            if (f.get("nombre").asString().contains(parteDelNombre)) {
                return f;
            }
        }
        return null;
    }

    // ---- UC-11: el cálculo ----

    @Test
    void elTutorObtieneElResumenDeSuSeccionConLosTresConteosYElPorcentaje() throws Exception {
        abril();

        Respuesta r = get(consulta("ALUMNOS", 3, null, null), token(rosa));

        assertThat(r.estado()).isEqualTo(200);
        JsonNode reporte = r.cuerpo();
        assertThat(reporte.get("ambito").asString()).isEqualTo("ALUMNOS");
        assertThat(reporte.get("alcance").asString()).isEqualTo("3° A");
        assertThat(reporte.get("parcial").asBoolean()).isFalse();
        assertThat(reporte.get("diasLectivos").asInt()).isEqualTo(lectivos.size());
        assertThat(reporte.get("generadoEn").asString()).endsWith("-05:00");
        assertThat(reporte.get("filas").size()).isEqualTo(3);
        JsonNode a = fila(reporte, "Alva");
        assertThat(a.get("puntual").asInt()).isEqualTo(3);
        assertThat(a.get("tardanza").asInt()).isEqualTo(1);
        assertThat(a.get("falta").asInt()).isEqualTo(1);
        assertThat(a.get("registrados").asInt()).isEqualTo(5);
        assertThat(a.get("diasEsperados").asInt()).isEqualTo(lectivos.size());
        assertThat(a.get("noRegistrados").asInt()).isEqualTo(lectivos.size() - 5);
        assertThat(a.get("porcentaje").asString()).as("100 × (3 + 1) / 5").isEqualTo("80.00");
        assertThat(a.get("codigo").asString()).isNotBlank();
        assertThat(a.get("seccion").asString()).isEqualTo("3° A");
        assertThat(fila(reporte, "Castro").get("porcentaje").asString()).isEqualTo("100.00");
    }

    @Test
    void sinDiasRegistradosElPorcentajeNoEstaDisponibleYLosDiasQuedanComoNoRegistrados() throws Exception {
        abril();

        JsonNode bravoFila = fila(get(consulta("ALUMNOS", 3, null, null), token(rosa)).cuerpo(), "Bravo");

        assertThat(bravoFila.get("porcentaje").isNull()).as("No disponible, no 0 %").isTrue();
        assertThat(bravoFila.get("registrados").asInt()).isZero();
        assertThat(bravoFila.get("falta").asInt()).as("la ausencia de registro no es falta").isZero();
        assertThat(bravoFila.get("noRegistrados").asInt()).isEqualTo(lectivos.size());
    }

    @Test
    void lasFilasSalenEnOrdenAlfabetico() throws Exception {
        abril();

        List<String> nombres = new ArrayList<>();
        get(consulta("ALUMNOS", 3, null, null), token(rosa)).cuerpo().get("filas").forEach(f -> nombres.add(f.get("nombre").asString()));

        assertThat(nombres).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
        assertThat(nombres.getFirst()).startsWith("Alva");
    }

    @Test
    void elAdministradorVeTodasLasSeccionesOUnaSolaPorCodigo() throws Exception {
        abril();

        JsonNode todas = get(consulta("ALUMNOS", 3, null, null), admin()).cuerpo();
        JsonNode soloB = get(consulta("ALUMNOS", 3, "3B", null), admin()).cuerpo();

        assertThat(todas.get("alcance").asString()).isEqualTo("Todas las secciones");
        assertThat(todas.get("filas").size()).isGreaterThanOrEqualTo(4);
        assertThat(fila(todas, "Ramos").get("seccion").asString()).isEqualTo("3° B");
        assertThat(soloB.get("alcance").asString()).isEqualTo("3° B");
        assertThat(soloB.get("filas").size()).isEqualTo(1);
        assertThat(fila(soloB, "Ramos").get("porcentaje").asString()).as("1 puntual de 2").isEqualTo("50.00");
    }

    @Test
    void elReporteDeDocentesUsaLosDiasConClasesDeCadaUno() throws Exception {
        abril();

        JsonNode reporte = get(consulta("DOCENTES", 3, null, null), admin()).cuerpo();

        assertThat(reporte.get("alcance").asString()).isEqualTo("Personal docente");
        JsonNode r = fila(reporte, "Quispe");
        assertThat(r.get("diasEsperados").asInt()).as("lunes y miércoles").isEqualTo(lectivos.size());
        assertThat(r.get("puntual").asInt()).isEqualTo(2);
        assertThat(r.get("tardanza").asInt()).isEqualTo(1);
        assertThat(r.get("falta").asInt()).as("la falta del cierre cuenta").isEqualTo(1);
        assertThat(r.get("porcentaje").asString()).as("100 × 3 / 4").isEqualTo("75.00");
        assertThat(r.get("noRegistrados").asInt()).isEqualTo(lectivos.size() - 4);
        long lunes = lectivos.stream().filter(d -> d.getDayOfWeek() == DayOfWeek.MONDAY).count();
        JsonNode c = fila(reporte, "Villanueva");
        assertThat(c.get("diasEsperados").asInt()).as("solo los lunes").isEqualTo((int) lunes);
        assertThat(c.get("porcentaje").asString()).isEqualTo("100.00");
        assertThat(c.get("noRegistrados").asInt()).isZero();
        assertThat(c.get("codigo").isNull()).isTrue();
        assertThat(fila(reporte, "Castro")).as("Luis no tiene clases ni registros").isNull();
    }

    // ---- FA-1: periodo en curso ----

    @Test
    void fa1ElPeriodoEnCursoEsParcialConFechaDeCorteYElDiaActualSoloEntraConLaJornadaCerrada() throws Exception {
        LocalDate hoy = lectivos.get(3);
        reloj.fijar(hoy, "10:00");

        JsonNode abierta = get(consulta("ALUMNOS", 3, null, null), token(rosa)).cuerpo();

        assertThat(abierta.get("parcial").asBoolean()).isTrue();
        assertThat(abierta.get("fechaCorte").asString()).as("hasta ayer").isEqualTo(hoy.minusDays(1).toString());
        assertThat(abierta.get("diasLectivos").asInt()).isEqualTo(3);
        assertThat(fila(abierta, "Alva").get("registrados").asInt()).as("la tardanza de hoy aún no cuenta").isEqualTo(3);

        reloj.fijar(hoy, propiedades.schoolDayClosingTime().toString());
        JsonNode cerrada = get(consulta("ALUMNOS", 3, null, null), token(rosa)).cuerpo();

        assertThat(cerrada.get("parcial").asBoolean()).isTrue();
        assertThat(cerrada.get("fechaCorte").asString()).isEqualTo(hoy.toString());
        assertThat(cerrada.get("diasLectivos").asInt()).isEqualTo(4);
        assertThat(fila(cerrada, "Alva").get("registrados").asInt()).isEqualTo(4);
    }

    @Test
    void fa1NuncaEntranFechasFuturasAunqueTenganRegistros() throws Exception {
        LocalDate hoy = lectivos.get(2);
        reloj.fijar(hoy, "16:00");

        JsonNode r = get(consulta("DOCENTES", 3, null, null), admin()).cuerpo();

        assertThat(r.get("fechaCorte").asString()).isEqualTo(hoy.toString());
        assertThat(fila(r, "Quispe").get("registrados").asInt()).as("la falta del día 4 es futura").isEqualTo(3);
        assertThat(fila(r, "Quispe").get("diasEsperados").asInt()).isEqualTo(3);
    }

    // ---- FA-2: sin registros ----

    @Test
    void fa2UnPeriodoSinAsistenciasNoGeneraNingunaSalidaDeNingunFormato() throws Exception {
        // Mayo tiene días lectivos pero ningún registro
        reloj.fijar(LocalDate.of(e.anio(), 7, 10), "10:00");

        for (String formato : new String[] {null, "xlsx", "pdf"}) {
            Respuesta r = get(consulta("ALUMNOS", 5, null, formato), token(rosa));
            assertThat(r.estado()).as(String.valueOf(formato)).isEqualTo(422);
            assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("SIN_REGISTROS_ASISTENCIA");
            assertThat(r.cuerpo().get("detail").asString()).contains("mayo");
        }
    }

    // ---- FA-3: alcance no autorizado o periodo futuro ----

    @Test
    void fa3ElTutorNoPuedeVerOtraSeccionNiElReporteDeDocentes() throws Exception {
        abril();
        String rosaToken = token(rosa);

        Respuesta otra = get(consulta("ALUMNOS", 3, "3B", null), rosaToken);
        Respuesta docentes = get(consulta("DOCENTES", 3, null, null), rosaToken);
        Respuesta propia = get(consulta("ALUMNOS", 3, "3a", null), rosaToken);

        assertThat(otra.estado()).isEqualTo(403);
        assertThat(otra.cuerpo().get("codigo").asString()).isEqualTo("FUERA_DE_ALCANCE");
        assertThat(docentes.estado()).isEqualTo(403);
        assertThat(propia.estado()).as("su propia sección, en minúsculas").isEqualTo(200);
    }

    @Test
    void fa3UnDocenteSinTutoriaYLosDemasRolesNoExportan() throws Exception {
        abril();

        assertThat(get(consulta("ALUMNOS", 3, null, null), token(carmen)).estado()).isEqualTo(403);
        assertThat(get(consulta("ALUMNOS", 3, null, null), tokenDe(crearUsuario("ALUMNO", true))).estado()).isEqualTo(403);
        assertThat(get(consulta("ALUMNOS", 3, null, null), tokenDe(crearUsuario("APODERADO", true))).estado()).isEqualTo(403);
        assertThat(get(consulta("ALUMNOS", 3, null, null), null).estado()).isEqualTo(401);
    }

    @Test
    void fa3UnPeriodoFuturoSeRechazaYLosFiltrosInvalidosTambien() throws Exception {
        abril();

        Respuesta futuro = get(consulta("ALUMNOS", 5, null, null), token(rosa));
        assertThat(futuro.estado()).isEqualTo(422);
        assertThat(futuro.cuerpo().get("codigo").asString()).isEqualTo("PERIODO_INVALIDO");
        assertThat(get(consulta("ALUMNOS", 13, null, null), token(rosa)).estado()).isEqualTo(400);
        assertThat(get(consulta("ALUMNOS", 0, null, null), token(rosa)).estado()).isEqualTo(400);
        assertThat(get(consulta("ALUMNOS", 3, null, "docx"), token(rosa)).estado()).isEqualTo(400);
        assertThat(get(RUTA + "?ambito=PERSONAS&mes=3&anio=" + e.anio(), token(rosa)).estado()).isEqualTo(400);
    }

    // ---- FA-4: calendario u horario no disponible ----

    @Test
    void fa4SinAnioActivoNoSeInventanDiasEsperados() throws Exception {
        abril();
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'PLANIFICADO' WHERE id = ?", e.anioId());
        try {
            Respuesta r = get(consulta("ALUMNOS", 3, null, null), admin());

            assertThat(r.estado()).isEqualTo(422);
            assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("CALENDARIO_NO_DISPONIBLE");
        } finally {
            jdbc.update("UPDATE academico.anio_escolar SET estado = 'ACTIVO' WHERE id = ?", e.anioId());
        }
    }

    @Test
    void fa4ElReporteDeDocentesSinHorarioPublicadoNoInventaFaltasNiPorcentajes() throws Exception {
        abril();
        jdbc.update("UPDATE horarios.horario_publicado SET vigente = FALSE WHERE id = ?", horarioId);
        try {
            Respuesta docentes = get(consulta("DOCENTES", 3, null, null), admin());
            Respuesta alumnos = get(consulta("ALUMNOS", 3, null, null), admin());

            assertThat(docentes.estado()).isEqualTo(422);
            assertThat(docentes.cuerpo().get("codigo").asString()).isEqualTo("CALENDARIO_NO_DISPONIBLE");
            assertThat(alumnos.estado()).as("los alumnos no dependen del horario de los docentes").isEqualTo(200);
        } finally {
            jdbc.update("UPDATE horarios.horario_publicado SET vigente = TRUE WHERE id = ?", horarioId);
        }
    }

    // ---- UC-10: archivos ----

    @Test
    void excelTraeElResumenLaFechaDeGeneracionYLosResultados() throws Exception {
        abril();

        Descarga d = descargar(consulta("ALUMNOS", 3, null, "xlsx"), token(rosa));

        assertThat(d.estado()).isEqualTo(200);
        assertThat(d.cabeceras().firstValue("Content-Type").orElse("")).contains("spreadsheetml.sheet");
        assertThat(d.cabeceras().firstValue("Content-Disposition").orElse("")).contains("attachment").contains("asistencia-alumnos-3A-" + e.anio() + "-03.xlsx");
        Sheet hoja = new XSSFWorkbook(new ByteArrayInputStream(d.contenido())).getSheetAt(0);
        List<String> primeras = new ArrayList<>();
        Row cabecera = null;
        for (Row fila : hoja) {
            if (fila.getCell(0) != null && "Código".equals(fila.getCell(0).getStringCellValue())) {
                cabecera = fila;
                break;
            }
            primeras.add(fila.getCell(0).getStringCellValue());
        }
        assertThat(primeras).anyMatch(l -> l.equals("Alcance: 3° A"));
        assertThat(primeras).anyMatch(l -> l.startsWith("Periodo: marzo " + e.anio()));
        assertThat(primeras).anyMatch(l -> l.startsWith("Generado el 10/04/" + e.anio() + " 10:00"));
        assertThat(primeras).anyMatch(l -> l.startsWith("Información incompleta"));
        assertThat(cabecera).isNotNull();
        int primeraFila = cabecera.getRowNum() + 1;
        assertThat(hoja.getRow(primeraFila).getCell(1).getStringCellValue()).startsWith("Alva");
        assertThat(hoja.getRow(primeraFila).getCell(9).getNumericCellValue()).isEqualTo(80.0);
        assertThat(hoja.getRow(primeraFila + 1).getCell(9).getStringCellValue()).as("Bravo: sin días registrados").isEqualTo("No disponible");
    }

    @Test
    void pdfEsUnDocumentoConLaMismaInformacion() throws Exception {
        abril();

        Descarga d = descargar(consulta("DOCENTES", 3, null, "pdf"), admin());

        assertThat(d.estado()).isEqualTo(200);
        assertThat(d.cabeceras().firstValue("Content-Type").orElse("")).contains("application/pdf");
        assertThat(d.cabeceras().firstValue("Content-Disposition").orElse("")).contains("asistencia-docentes-" + e.anio() + "-03.pdf");
        PdfReader lector = new PdfReader(d.contenido());
        try {
            String texto = new PdfTextExtractor(lector).getTextFromPage(1);
            assertThat(texto).contains("Reporte de asistencia de docentes", "Alcance: Personal docente", "Quispe, Rosa", "75.00");
        } finally {
            lector.close();
        }
    }

    @Test
    void elExcelDeDocentesYElDeAlumnosSeGeneranPorSeparado() throws Exception {
        abril();

        Descarga docentes = descargar(consulta("DOCENTES", 3, null, "xlsx"), admin());
        Sheet hoja = new XSSFWorkbook(new ByteArrayInputStream(docentes.contenido())).getSheetAt(0);
        boolean tieneColumnaDeCodigo = false;
        for (Row fila : hoja) {
            if (fila.getCell(0) != null && "Docente".equals(fila.getCell(0).getStringCellValue())) {
                tieneColumnaDeCodigo = "Código".equals(fila.getCell(1) == null ? "" : fila.getCell(1).getStringCellValue());
            }
        }

        assertThat(docentes.estado()).isEqualTo(200);
        assertThat(tieneColumnaDeCodigo).as("el ámbito de docentes no mezcla columnas de alumnos").isFalse();
    }

    // ---- sin consolidados ----

    @Test
    void pedirUnReporteNoEscribeNingunRegistroDeAsistencia() throws Exception {
        abril();
        int alumnosAntes = jdbc.queryForObject("SELECT count(*) FROM asistencia.asistencia_alumno", Integer.class);
        int docentesAntes = jdbc.queryForObject("SELECT count(*) FROM asistencia.asistencia_docente", Integer.class);
        int cierresAntes = jdbc.queryForObject("SELECT count(*) FROM asistencia.cierre_diario", Integer.class);

        get(consulta("ALUMNOS", 3, null, null), admin());
        get(consulta("DOCENTES", 3, null, "xlsx"), admin());
        get(consulta("ALUMNOS", 3, "3A", "pdf"), admin());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM asistencia.asistencia_alumno", Integer.class)).isEqualTo(alumnosAntes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM asistencia.asistencia_docente", Integer.class)).isEqualTo(docentesAntes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM asistencia.cierre_diario", Integer.class)).isEqualTo(cierresAntes);
    }
}
