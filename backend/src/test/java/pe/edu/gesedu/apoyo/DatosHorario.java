package pe.edu.gesedu.apoyo;

import java.sql.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Datos de prueba para el horario: un año propio (para que las pruebas no se pisen), sus bloques, secciones,
 * cursos y docentes con asociaciones y disponibilidad. Reproduce el caso de la demo: Rosa Quispe ya podría tener
 * clase el martes a las 9:15 y Carlos Ríos no está disponible a esa hora.
 */
public final class DatosHorario {

    private static final AtomicInteger SIGUIENTE_ANIO = new AtomicInteger(2200);

    /** Identificadores del escenario. {@code anio} es el número del año escolar. */
    public record Escenario(int anio, String anioId, String martes915, String martes1015, String lunes745,
                            String seccion3A, String seccion4B, String matematica, String ingles,
                            String rosa, String carlos, String fernando, String carmen) {
    }

    private final JdbcTemplate jdbc;

    public DatosHorario(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }

    public Escenario crearEscenario() {
        int anio = SIGUIENTE_ANIO.getAndIncrement();
        String anioId = id();
        jdbc.update("INSERT INTO academico.anio_escolar (id, anio, fecha_inicio, fecha_fin, estado) "
                + "VALUES (?, ?, ?, ?, 'PLANIFICADO')", anioId, anio, Date.valueOf(anio + "-02-15"),
                Date.valueOf(anio + "-12-20"));

        String martes915 = bloque(anioId, "MARTES", "09:15", "10:00");
        String martes1015 = bloque(anioId, "MARTES", "10:15", "11:00");
        String lunes745 = bloque(anioId, "LUNES", "07:45", "08:30");

        String grado3 = grado(anioId, 3);
        String grado4 = grado(anioId, 4);
        String seccion3A = seccion(anioId, grado3, "A");
        String seccion4B = seccion(anioId, grado4, "B");

        String matematica = curso(anioId, "Matemática", "matematica", 5);
        String ingles = curso(anioId, "Inglés", "ingles", 3);

        String rosa = docente("Rosa", "Quispe Mendoza");
        String carlos = docente("Carlos", "Ríos Vega");
        String fernando = docente("Fernando", "Medina Soto");
        String carmen = docente("Carmen", "Villanueva Paz");

        String vRosa = vinculacion(anioId, rosa);
        String vCarlos = vinculacion(anioId, carlos);
        String vFernando = vinculacion(anioId, fernando);
        String vCarmen = vinculacion(anioId, carmen);
        for (String v : new String[] {vRosa, vCarlos, vFernando}) {
            asociar(v, matematica);
        }
        asociar(vCarmen, ingles);

        // Rosa, Fernando y Carmen están disponibles el martes de 9:15 y el lunes de 7:45; Rosa también a las 10:15.
        // Carlos solo el lunes: el martes a las 9:15 no está disponible.
        disponibilidad(vRosa, martes915, martes1015, lunes745);
        disponibilidad(vFernando, martes915, lunes745);
        disponibilidad(vCarmen, martes915, lunes745);
        disponibilidad(vCarlos, lunes745);

        return new Escenario(anio, anioId, martes915, martes1015, lunes745, seccion3A, seccion4B, matematica, ingles,
                rosa, carlos, fernando, carmen);
    }

    private String bloque(String anioId, String dia, String inicio, String fin) {
        String id = id();
        jdbc.update("INSERT INTO horarios.bloque_horario (id, anio_escolar_id, dia_semana, hora_inicio, hora_fin) "
                + "VALUES (?, ?, ?, ?::time, ?::time)", id, anioId, dia, inicio, fin);
        return id;
    }

    private String grado(String anioId, int nivel) {
        String id = id();
        jdbc.update("INSERT INTO academico.grado (id, anio_escolar_id, numero_nivel) VALUES (?, ?, ?)", id, anioId, nivel);
        return id;
    }

    private String seccion(String anioId, String gradoId, String letra) {
        String id = id();
        jdbc.update("INSERT INTO academico.seccion (id, grado_id, anio_escolar_id, letra, capacidad, vacantes_disponibles) "
                + "VALUES (?, ?, ?, ?, 30, 30)", id, gradoId, anioId, letra);
        return id;
    }

    private String curso(String anioId, String nombre, String slug, int horas) {
        String id = id();
        jdbc.update("INSERT INTO academico.curso (id, anio_escolar_id, nombre, slug, horas_lectivas) VALUES (?, ?, ?, ?, ?)",
                id, anioId, nombre, slug, horas);
        return id;
    }

    /** Docente con su cuenta (sin acceso: inactiva y sin credenciales). */
    public String docente(String nombres, String apellidos) {
        String usuarioId = id();
        jdbc.update("INSERT INTO seguridad.usuario (id, nombres, apellidos, documento_identidad, activo, rol_acceso_id) "
                + "SELECT ?, ?, ?, ?, FALSE, r.id FROM seguridad.rol_acceso r WHERE r.codigo = 'DOCENTE'",
                usuarioId, nombres, apellidos, usuarioId.substring(0, 20));
        String id = id();
        jdbc.update("INSERT INTO seguridad.docente (id, usuario_id, codigo_docente) VALUES (?, ?, ?)",
                id, usuarioId, "D" + id.substring(0, 8));
        return id;
    }

    private String vinculacion(String anioId, String docenteId) {
        String id = id();
        jdbc.update("INSERT INTO horarios.vinculacion_docente (id, docente_id, anio_escolar_id, habilitado) "
                + "VALUES (?, ?, ?, TRUE)", id, docenteId, anioId);
        return id;
    }

    private void asociar(String vinculacionId, String cursoId) {
        jdbc.update("INSERT INTO horarios.asociacion_docente_curso (id, vinculacion_id, curso_id) VALUES (?, ?, ?)",
                id(), vinculacionId, cursoId);
    }

    private void disponibilidad(String vinculacionId, String... bloques) {
        String disponibilidad = id();
        jdbc.update("INSERT INTO horarios.disponibilidad_docente (id, vinculacion_id, fecha_hora_confirmacion) "
                + "VALUES (?, ?, now())", disponibilidad, vinculacionId);
        for (String bloque : bloques) {
            jdbc.update("INSERT INTO horarios.bloque_disponible (id, disponibilidad_id, bloque_id) VALUES (?, ?, ?)",
                    id(), disponibilidad, bloque);
        }
    }

    /** Agrega a un docente de otro año al borrador no hace falta: este helper deja un docente sin vinculación. */
    public String docenteSinVinculacion() {
        return docente("Sin", "Vinculo");
    }
}
