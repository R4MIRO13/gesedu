package pe.edu.gesedu.evaluacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import pe.edu.gesedu.apoyo.BaseIT;
import pe.edu.gesedu.apoyo.RelojPrueba;
import pe.edu.gesedu.apoyo.RelojPruebaConfig;

/**
 * Contenido del PDF de la libreta (UC-19, RF-36; ficha 77). Genera la libreta FINAL de 2026 de Valeria Torres, alumna de la
 * semilla de demo (V1002, V1005), extrae su texto con PDFBox y lo compara con la base: nombre, código, grado y sección,
 * periodo, cursos (áreas), competencias y los calificativos AD/A/B/C de cada bimestre y el final, fila por fila, iguales a
 * {@code evaluacion.nota_consolidada}. Caso adicional: un curso de la sección sin ninguna nota consolidada aparece con «—» en
 * cada calificativo y el PDF se genera sin error. Corre en su propia base de demo porque el segundo caso agrega un curso.
 */
@Import(RelojPruebaConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LibretaPdfContenidoIT extends BaseIT {

    private static final String BASE_DE_DATOS = "gesedu_libreta_pdf";
    private static final String SIN_NOTA = "—";
    private static final Set<String> CALIFICATIVOS = Set.of("AD", "A", "B", "C", SIN_NOTA);

    @Autowired
    private RelojPrueba reloj;

    @BeforeAll
    static void crearBaseDeDatosDeDemo() throws SQLException {
        try (Connection conexion = DriverManager.getConnection("jdbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(5432) + "/postgres", "postgres", "postgres");
             Statement sentencia = conexion.createStatement()) {
            sentencia.execute("DROP DATABASE IF EXISTS " + BASE_DE_DATOS);
            sentencia.execute("CREATE DATABASE " + BASE_DE_DATOS + " OWNER gesedu_migrador ENCODING 'UTF8'");
            sentencia.execute("GRANT CONNECT ON DATABASE " + BASE_DE_DATOS + " TO gesedu_app");
        }
        baseDeDatos = BASE_DE_DATOS;
    }

    @AfterAll
    static void volverALaBaseCompartida() {
        baseDeDatos = "gesedu";
    }

    @DynamicPropertySource
    static void perfilDemo(DynamicPropertyRegistry registro) {
        registro.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/demo");
    }

    // ---- casos ----

    @Test
    @Order(1)
    void laLibretaDeValeriaTraeSusDatosYLosCalificativosDeLaConsolidacion() throws Exception {
        Alumna valeria = valeria();

        String texto = textoPlano(libretaPdf(valeria.id()));

        assertThat(texto).contains("Libreta de notas · 2026")
                .contains("Alumno: ")
                .contains(valeria.nombres())
                .contains(valeria.apellidos())
                .contains("(" + valeria.codigo() + ")")
                .contains("Sección: " + valeria.seccion() + " · Año escolar 2026")
                .as("estado y fecha de la libreta en la base, la fecha en la zona del reloj inyectado")
                .contains(estadoYFecha(valeria))
                .contains("Libreta final, actualizada el 18/12/2026 12:00")
                .contains("AD logro destacado · A logro esperado · B en proceso · C en inicio");
        assertThat(texto).as("columnas de los cuatro periodos y la final")
                .contains("Bim. I").contains("Bim. II").contains("Bim. III").contains("Bim. IV").contains("Final");
        List<FilaEsperada> esperadas = filasEsperadas(valeria);
        assertThat(esperadas).as("la semilla deja 6 cursos con competencias").hasSizeGreaterThanOrEqualTo(6);
        for (FilaEsperada fila : esperadas) {
            assertThat(texto).as("curso").contains(fila.curso());
            assertThat(texto).as("competencia de %s", fila.curso()).contains(fila.codigo() + " · " + fila.competencia());
            assertThat(fila.calificativos()).as("libreta FINAL: %s %s sin huecos", fila.curso(), fila.codigo()).doesNotContain(SIN_NOTA);
        }
        assertThat(calificativosPorFila(libretaPdf(valeria.id())))
                .as("Bim. I a IV y Final de cada competencia, en el orden del PDF, iguales a nota_consolidada")
                .containsExactlyElementsOf(esperadas.stream().map(FilaEsperada::calificativos).toList());
    }

    @Test
    @Order(2)
    void unCursoSinNotasApareceConGuionesYNoConUnError() throws Exception {
        Alumna valeria = valeria();
        // Un área nueva en 2° A de 2026 con dos competencias y su docente, pero sin ningún logro ni nota consolidada
        String anio = jdbc.queryForObject("SELECT id FROM academico.anio_escolar WHERE anio = 2026", String.class);
        String curso = jdbc.queryForObject("INSERT INTO academico.curso (id, anio_escolar_id, nombre, slug, horas_lectivas) "
                + "VALUES (uuidv7()::text, ?, 'Taller de Robótica', 'taller-robotica', 2) RETURNING id", String.class, anio);
        jdbc.update("INSERT INTO academico.competencia (id, curso_id, codigo, nombre, nivel_esperado, orden) VALUES "
                + "(uuidv7()::text, ?, 'C1', 'Diseña prototipos', 'Diseña prototipos sencillos.', 1), "
                + "(uuidv7()::text, ?, 'C2', 'Programa secuencias', 'Programa secuencias con bucles.', 2)", curso, curso);
        jdbc.update("INSERT INTO evaluacion.asignacion_docente (id, docente_id, curso_id, seccion_id) "
                + "SELECT uuidv7()::text, d.id, ?, ? FROM seguridad.docente d WHERE d.codigo_docente = 'DOC-001'", curso, valeria.seccionId());

        byte[] pdf = libretaPdf(valeria.id());
        String texto = textoPlano(pdf);

        assertThat(texto).contains("Taller de Robótica").contains("C1 · Diseña prototipos").contains("C2 · Programa secuencias")
                .as("un curso sin notas no cambia el estado ni la fecha de la libreta").contains(estadoYFecha(valeria));
        List<FilaEsperada> esperadas = filasEsperadas(valeria);
        List<List<String>> deRobotica = esperadas.stream().filter(f -> f.curso().equals("Taller de Robótica"))
                .map(FilaEsperada::calificativos).toList();
        assertThat(deRobotica).as("sin notas: «—» en cada bimestre y en la final")
                .containsExactly(List.of(SIN_NOTA, SIN_NOTA, SIN_NOTA, SIN_NOTA, SIN_NOTA), List.of(SIN_NOTA, SIN_NOTA, SIN_NOTA, SIN_NOTA, SIN_NOTA));
        assertThat(calificativosPorFila(pdf)).as("las demás áreas no cambian y la nueva va en su lugar alfabético")
                .containsExactlyElementsOf(esperadas.stream().map(FilaEsperada::calificativos).toList());
    }

    // ---- datos esperados (de la base) ----

    private record Alumna(String id, String nombres, String apellidos, String codigo, String seccionId, String seccion) {
    }

    private record FilaEsperada(String curso, String codigo, String competencia, List<String> calificativos) {
    }

    private Alumna valeria() {
        return jdbc.queryForObject("SELECT a.id, u.nombres, u.apellidos, a.codigo_alumno, s.id AS seccion_id, g.numero_nivel, s.letra "
                + "FROM seguridad.alumno a JOIN seguridad.usuario u ON u.id = a.usuario_id "
                + "JOIN matricula.matricula m ON m.alumno_id = a.id AND m.estado = 'CONFIRMADA' "
                + "JOIN academico.anio_escolar ae ON ae.id = m.anio_escolar_id AND ae.anio = 2026 "
                + "JOIN academico.seccion s ON s.id = m.seccion_id JOIN academico.grado g ON g.id = s.grado_id "
                + "WHERE u.nombres = 'Valeria' AND u.apellidos = 'Torres Gil'",
                (rs, i) -> new Alumna(rs.getString("id"), rs.getString("nombres"), rs.getString("apellidos"), rs.getString("codigo_alumno"),
                        rs.getString("seccion_id"), rs.getInt("numero_nivel") + "° " + rs.getString("letra")));
    }

    /**
     * «Libreta final|parcial, actualizada el dd/MM/yyyy HH:mm» con el estado y la fecha de actualización de
     * {@code evaluacion.libreta} para 2026. La fecha se escribe en la zona del reloj inyectado (America/Lima), no en la del
     * sistema, igual que el servicio. El PDF de la libreta no imprime una fecha de emisión aparte.
     */
    private String estadoYFecha(Alumna alumna) {
        return jdbc.queryForObject("SELECT l.estado, l.fecha_actualizacion FROM evaluacion.libreta l "
                + "JOIN academico.anio_escolar ae ON ae.id = l.anio_escolar_id WHERE l.alumno_id = ? AND ae.anio = 2026",
                (rs, i) -> "Libreta " + ("FINAL".equals(rs.getString("estado")) ? "final" : "parcial") + ", actualizada el "
                        + rs.getObject("fecha_actualizacion", OffsetDateTime.class).atZoneSameInstant(reloj.getZone())
                                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                alumna.id());
    }

    /**
     * Una fila por competencia de cada curso de la sección, en el orden del servicio (curso por nombre sin tildes, competencia
     * por su orden), con los calificativos de nota_consolidada: bimestres I a IV y el anual; «—» donde no hay nota.
     */
    private List<FilaEsperada> filasEsperadas(Alumna alumna) {
        Map<String, String[]> notas = new HashMap<>();
        jdbc.query("SELECT n.competencia_id, n.tipo, b.numero, n.nivel_logro FROM evaluacion.nota_consolidada n "
                + "LEFT JOIN academico.bimestre b ON b.id = n.bimestre_id JOIN academico.anio_escolar ae ON ae.id = n.anio_escolar_id "
                + "WHERE n.alumno_id = ? AND ae.anio = 2026", rs -> {
            String[] niveles = notas.computeIfAbsent(rs.getString("competencia_id"), k -> new String[] {SIN_NOTA, SIN_NOTA, SIN_NOTA, SIN_NOTA, SIN_NOTA});
            int columna = "ANUAL".equals(rs.getString("tipo")) ? 4 : rs.getInt("numero") - 1;
            niveles[columna] = rs.getString("nivel_logro");
        }, alumna.id());
        record Competencia(String curso, String id, String codigo, String nombre, int orden) {
        }
        List<Competencia> competencias = jdbc.query("SELECT c.nombre AS curso, k.id, k.codigo, k.nombre, k.orden "
                + "FROM evaluacion.asignacion_docente a JOIN academico.curso c ON c.id = a.curso_id "
                + "JOIN academico.competencia k ON k.curso_id = c.id WHERE a.seccion_id = ?",
                (rs, i) -> new Competencia(rs.getString("curso"), rs.getString("id"), rs.getString("codigo"), rs.getString("nombre"), rs.getInt("orden")),
                alumna.seccionId());
        return competencias.stream()
                .sorted(Comparator.comparing((Competencia k) -> sinTildes(k.curso())).thenComparingInt(Competencia::orden))
                .map(k -> new FilaEsperada(k.curso(), k.codigo(), k.nombre(),
                        List.of(notas.getOrDefault(k.id(), new String[] {SIN_NOTA, SIN_NOTA, SIN_NOTA, SIN_NOTA, SIN_NOTA}))))
                .toList();
    }

    private static String sinTildes(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    // ---- el PDF ----

    private byte[] libretaPdf(String alumnoId) throws Exception {
        reloj.fijar(LocalDate.of(2027, 3, 15), "07:40");
        Descarga d = descargar("/api/v1/alumnos/" + alumnoId + "/libreta?formato=pdf&anio=2026", tokenDe("familia.torres@demo.gesedu.pe"));
        assertThat(d.estado()).isEqualTo(200);
        assertThat(d.cabeceras().firstValue("Content-Type")).hasValueSatisfying(t -> assertThat(t).startsWith("application/pdf"));
        return d.contenido();
    }

    /** Todo el texto con los espacios y saltos de línea reducidos a un espacio (las celdas largas se parten en varias líneas). */
    private static String textoPlano(byte[] pdf) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(documento).replaceAll("\\s+", " ");
        }
    }

    /**
     * Los calificativos de cada fila de la tabla, leídos por posición: las palabras que caen en las columnas «Bim. I» a «Final»
     * (a la derecha del primer «Bim.»), agrupadas por página y línea y ordenadas de izquierda a derecha. Así el texto largo del
     * nivel esperado, que también puede contener una «A» suelta, no se confunde con un calificativo.
     */
    private static List<List<String>> calificativosPorFila(byte[] pdf) throws IOException {
        record Palabra(int pagina, float x, float y, String texto) {
        }
        List<Palabra> palabras = new ArrayList<>();
        PDFTextStripper lector = new PDFTextStripper() {
            @Override
            protected void writeString(String texto, List<TextPosition> posiciones) throws IOException {
                // Una llamada por línea: se separa en palabras conservando la posición de la primera letra de cada una
                StringBuilder actual = new StringBuilder();
                TextPosition inicio = null;
                for (TextPosition p : posiciones) {
                    String c = p.getUnicode();
                    if (c.isBlank()) {
                        if (inicio != null) {
                            palabras.add(new Palabra(getCurrentPageNo(), inicio.getXDirAdj(), inicio.getYDirAdj(), actual.toString()));
                        }
                        actual.setLength(0);
                        inicio = null;
                    } else {
                        if (inicio == null) {
                            inicio = p;
                        }
                        actual.append(c);
                    }
                }
                if (inicio != null) {
                    palabras.add(new Palabra(getCurrentPageNo(), inicio.getXDirAdj(), inicio.getYDirAdj(), actual.toString()));
                }
            }
        };
        lector.setSortByPosition(true);
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            lector.getText(documento);
        }
        float inicioCalificativos = (float) palabras.stream().filter(p -> p.texto().equals("Bim.")).mapToDouble(Palabra::x).min()
                .orElseThrow(() -> new AssertionError("El PDF no tiene columnas de bimestre")) - 2f;
        Map<String, List<Palabra>> porLinea = new LinkedHashMap<>();
        palabras.stream()
                .filter(p -> p.x() >= inicioCalificativos && CALIFICATIVOS.contains(p.texto()))
                .sorted(Comparator.comparingInt(Palabra::pagina).thenComparingDouble(Palabra::y).thenComparingDouble(Palabra::x))
                .forEach(p -> porLinea.computeIfAbsent(p.pagina() + ":" + Math.round(p.y()), k -> new ArrayList<>()).add(p));
        return porLinea.values().stream().map(linea -> linea.stream().map(Palabra::texto).toList()).toList();
    }
}
