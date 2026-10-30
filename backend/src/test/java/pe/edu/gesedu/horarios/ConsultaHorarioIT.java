package pe.edu.gesedu.horarios;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.edu.gesedu.apoyo.DatosHorario;
import pe.edu.gesedu.apoyo.DatosMatricula.Escenario;
import pe.edu.gesedu.matricula.BaseMatriculaIT;

/** UC-23: el horario publicado según el alcance de cada rol (RF-39, RF-45, RNF-07). */
class ConsultaHorarioIT extends BaseMatriculaIT {

    private Escenario e;
    private String admin;
    private String docenteRosa;
    private String docenteLuis;
    private String bloque1;
    private String bloque2;
    private String curso;
    private String adminId;

    @org.springframework.beans.factory.annotation.Autowired
    private pe.edu.gesedu.horarios.aplicacion.CacheHorarioVigente cache;

    @BeforeEach
    void escenario() throws Exception {
        e = datos.crearEscenario(4);
        String correoAdmin = crearUsuario("ADMINISTRADOR", true);
        admin = tokenDe(correoAdmin);
        adminId = idDe(correoAdmin);
        DatosHorario horario = new DatosHorario(jdbc);
        docenteRosa = horario.docente("Rosa", "Quispe");
        docenteLuis = horario.docente("Luis", "Castro");
        bloque1 = bloque("LUNES", "07:45", "08:30");
        bloque2 = bloque("LUNES", "08:30", "09:15");
        curso = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO academico.curso (id, anio_escolar_id, nombre, slug, horas_lectivas) VALUES (?, ?, 'Matemática', 'matematica', 5)",
                curso, e.anioId());
    }

    private String bloque(String dia, String inicio, String fin) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO horarios.bloque_horario (id, anio_escolar_id, dia_semana, hora_inicio, hora_fin) VALUES (?, ?, ?, ?::time, ?::time)",
                id, e.anioId(), dia, inicio, fin);
        return id;
    }

    /** Publica directamente una versión con Rosa en 3° A (bloque 1) y Luis en 3° B (bloque 1) y en 2° A (bloque 2). */
    private String publicar(int numero, boolean vigente) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO horarios.horario_publicado (id, anio_escolar_id, numero_version, fecha_hora_publicacion, vigente, publicado_por) "
                + "VALUES (?, ?, ?, now(), ?, ?)", id, e.anioId(), numero, vigente, adminId);
        clasePublicada(id, bloque1, e.seccion3A(), docenteRosa);
        clasePublicada(id, bloque1, e.seccion3B(), docenteLuis);
        clasePublicada(id, bloque2, e.seccion2A(), docenteLuis);
        return id;
    }

    private void clasePublicada(String publicado, String bloque, String seccion, String docente) {
        jdbc.update("INSERT INTO horarios.asignacion_publicada (id, horario_publicado_id, bloque_id, seccion_id, curso_id, docente_id) "
                + "VALUES (?, ?, ?, ?, ?, ?)", UUID.randomUUID().toString(), publicado, bloque, seccion, curso, docente);
    }

    private String rutaVigente(String consulta) {
        return "/api/v1/horarios/" + e.anio() + "/vigente" + consulta;
    }

    /** Cuenta de docente (con perfil) para el docente dado. */
    private String tokenDocente(String docenteId) throws Exception {
        String usuario = jdbc.queryForObject("SELECT usuario_id FROM seguridad.docente WHERE id = ?", String.class, docenteId);
        String correo = "docente-" + usuario.substring(0, 8) + "@test.gesedu.pe";
        jdbc.update("UPDATE seguridad.usuario SET correo = ?, hash_contrasena = ?, activo = TRUE WHERE id = ?", correo,
                codificador.encode(CONTRASENA), usuario);
        invalidarCuentas();
        return tokenDe(correo);
    }

    private static List<String> ids(tools.jackson.databind.JsonNode lista, String campo) {
        List<String> r = new java.util.ArrayList<>();
        lista.forEach(n -> r.add(n.get(campo).asString()));
        return r;
    }

    // ---- FA-1 ----

    @Test
    void fa1SinVersionPublicadaTodosLosRolesRecibenQueAunNoEstaDisponible() throws Exception {
        ApoderadoConCuenta ap = crearApoderadoConCuenta();
        datos.matriculaConObligaciones(e, ap.apoderadoId(), datos.alumnoDe(ap.apoderadoId(), "Lucía"), e.seccion3A());

        for (String token : new String[] {admin, ap.token(), tokenDocente(docenteRosa)}) {
            Respuesta r = get(rutaVigente(""), token);
            assertThat(r.estado()).isEqualTo(404);
            assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("HORARIO_NO_PUBLICADO");
        }
    }

    @Test
    void unaVersionAnteriorNoVigenteNoCuentaComoPublicada() throws Exception {
        publicar(1, false);

        assertThat(get(rutaVigente(""), admin).estado()).isEqualTo(404);
    }

    // ---- Administrador ----

    @Test
    void elAdministradorVeCualquierSeccionOCualquierDocenteYCambiarDePerspectivaNoModificaNada() throws Exception {
        publicar(1, true);
        long antes = jdbc.queryForObject("SELECT count(*) FROM horarios.asignacion_publicada", Long.class);

        Respuesta porSeccion = get(rutaVigente("?perspectiva=SECCION&seccionId=" + e.seccion3B()), admin);
        assertThat(porSeccion.estado()).isEqualTo(200);
        assertThat(porSeccion.cuerpo().get("numeroVersion").asInt()).isEqualTo(1);
        assertThat(porSeccion.cuerpo().get("perspectivas")).hasSize(2);
        assertThat(porSeccion.cuerpo().get("asignaciones")).hasSize(1);
        assertThat(porSeccion.cuerpo().get("asignaciones").get(0).get("docente").asString()).isEqualTo("Luis Castro");
        assertThat(porSeccion.cuerpo().get("asignaciones").get(0).get("seccion").asString()).isEqualTo("3° B");
        assertThat(porSeccion.cuerpo().get("secciones")).hasSize(3);
        assertThat(porSeccion.cuerpo().get("vigenteDesde").asString()).endsWith("-05:00");

        Respuesta porDocente = get(rutaVigente("?perspectiva=DOCENTE&docenteId=" + docenteLuis), admin);
        assertThat(porDocente.cuerpo().get("perspectiva").asString()).isEqualTo("DOCENTE");
        assertThat(porDocente.cuerpo().get("asignaciones")).hasSize(2);
        assertThat(ids(porDocente.cuerpo().get("asignaciones"), "seccion")).containsExactlyInAnyOrder("3° B", "2° A");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM horarios.asignacion_publicada", Long.class)).isEqualTo(antes);
    }

    // ---- Apoderado y alumno ----

    @Test
    void elApoderadoVeSoloLasSeccionesDeSusHijosYNoPuedeVerPorDocente() throws Exception {
        publicar(1, true);
        ApoderadoConCuenta ap = crearApoderadoConCuenta();
        datos.matriculaConObligaciones(e, ap.apoderadoId(), datos.alumnoDe(ap.apoderadoId(), "Lucía"), e.seccion3A());
        datos.matriculaConObligaciones(e, ap.apoderadoId(), datos.alumnoDe(ap.apoderadoId(), "Andrés"), e.seccion3B());

        Respuesta r = get(rutaVigente(""), ap.token());

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("perspectivas")).hasSize(1);
        assertThat(r.cuerpo().get("perspectivas").get(0).asString()).isEqualTo("SECCION");
        assertThat(r.cuerpo().get("secciones")).hasSize(2);
        assertThat(ids(r.cuerpo().get("secciones"), "nombre")).anyMatch(n -> n.startsWith("3° A · Lucía"))
                .anyMatch(n -> n.startsWith("3° B · Andrés"));
        assertThat(r.cuerpo().get("docentes")).isEmpty();

        // Elige la otra sección de sus hijos
        Respuesta segunda = get(rutaVigente("?seccionId=" + e.seccion3B()), ap.token());
        assertThat(segunda.estado()).isEqualTo(200);
        assertThat(segunda.cuerpo().get("asignaciones").get(0).get("seccion").asString()).isEqualTo("3° B");

        for (String prohibido : new String[] {"?seccionId=" + e.seccion2A(), "?perspectiva=DOCENTE&docenteId=" + docenteRosa, "?perspectiva=DOCENTE"}) {
            Respuesta f = get(rutaVigente(prohibido), ap.token());
            assertThat(f.estado()).as(prohibido).isEqualTo(403);
            assertThat(f.cuerpo().get("codigo").asString()).isEqualTo("FUERA_DE_ALCANCE");
        }
    }

    @Test
    void unApoderadoSinHijosMatriculadosNoVeNingunaSeccion() throws Exception {
        publicar(1, true);
        ApoderadoConCuenta ap = crearApoderadoConCuenta();

        Respuesta r = get(rutaVigente(""), ap.token());

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("secciones")).isEmpty();
        assertThat(r.cuerpo().get("asignaciones")).isEmpty();
        assertThat(get(rutaVigente("?seccionId=" + e.seccion3A()), ap.token()).estado()).isEqualTo(403);
    }

    @Test
    void elAlumnoVeSoloLaSeccionEnLaQueEstaMatriculado() throws Exception {
        publicar(1, true);
        ApoderadoConCuenta ap = crearApoderadoConCuenta();
        String alumno = datos.alumnoDe(ap.apoderadoId(), "Lucía");
        datos.matriculaConObligaciones(e, ap.apoderadoId(), alumno, e.seccion3A());
        String usuario = jdbc.queryForObject("SELECT usuario_id FROM seguridad.alumno WHERE id = ?", String.class, alumno);
        String correo = "alumno-" + usuario.substring(0, 8) + "@test.gesedu.pe";
        jdbc.update("UPDATE seguridad.usuario SET correo = ?, hash_contrasena = ?, activo = TRUE WHERE id = ?", correo,
                codificador.encode(CONTRASENA), usuario);
        invalidarCuentas();
        String token = tokenDe(correo);

        Respuesta r = get(rutaVigente(""), token);

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("secciones")).hasSize(1);
        assertThat(r.cuerpo().get("secciones").get(0).get("nombre").asString()).isEqualTo("3° A");
        assertThat(r.cuerpo().get("asignaciones")).hasSize(1);
        assertThat(r.cuerpo().get("asignaciones").get(0).get("docente").asString()).isEqualTo("Rosa Quispe");
        assertThat(get(rutaVigente("?seccionId=" + e.seccion3B()), token).estado()).isEqualTo(403);
        assertThat(get(rutaVigente("?perspectiva=DOCENTE"), token).estado()).isEqualTo(403);
    }

    // ---- Docente ----

    @Test
    void elDocenteVeSusClasesYLasSeccionesDondeDictaOEsTutorPeroNoLasDeLosDemas() throws Exception {
        publicar(1, true);
        // Rosa es tutora de 2° A, donde no tiene clases publicadas
        jdbc.update("UPDATE academico.seccion SET tutor_docente_id = ? WHERE id = ?", docenteRosa, e.seccion2A());
        String token = tokenDocente(docenteRosa);

        Respuesta propio = get(rutaVigente(""), token);
        assertThat(propio.estado()).isEqualTo(200);
        assertThat(propio.cuerpo().get("perspectiva").asString()).isEqualTo("DOCENTE");
        assertThat(propio.cuerpo().get("docenteId").asString()).isEqualTo(docenteRosa);
        assertThat(propio.cuerpo().get("asignaciones")).hasSize(1);
        assertThat(propio.cuerpo().get("docentes")).hasSize(1);
        assertThat(ids(propio.cuerpo().get("secciones"), "nombre")).containsExactly("2° A", "3° A");

        assertThat(get(rutaVigente("?perspectiva=SECCION&seccionId=" + e.seccion3A()), token).estado()).isEqualTo(200);
        assertThat(get(rutaVigente("?perspectiva=SECCION&seccionId=" + e.seccion2A()), token).cuerpo().get("asignaciones")).hasSize(1);
        Respuesta ajeno = get(rutaVigente("?perspectiva=SECCION&seccionId=" + e.seccion3B()), token);
        assertThat(ajeno.estado()).isEqualTo(403);
        assertThat(ajeno.cuerpo().get("codigo").asString()).isEqualTo("FUERA_DE_ALCANCE");
        Respuesta otroDocente = get(rutaVigente("?perspectiva=DOCENTE&docenteId=" + docenteLuis), token);
        assertThat(otroDocente.estado()).isEqualTo(403);
    }

    // ---- Caché y permisos ----

    @Test
    void laCacheServiraLaVersionVigenteHastaQueUnaPublicacionLaInvalida() throws Exception {
        publicar(1, true);
        assertThat(get(rutaVigente(""), admin).cuerpo().get("numeroVersion").asInt()).isEqualTo(1);

        // Se cambia la versión vigente directamente en la base, sin pasar por el servicio de publicación
        jdbc.update("UPDATE horarios.horario_publicado SET vigente = FALSE WHERE anio_escolar_id = ?", e.anioId());
        publicar(2, true);
        assertThat(get(rutaVigente(""), admin).cuerpo().get("numeroVersion").asInt()).as("sigue en caché").isEqualTo(1);

        cache.invalidar(e.anio());
        assertThat(get(rutaVigente(""), admin).cuerpo().get("numeroVersion").asInt()).isEqualTo(2);
    }

    @Test
    void sinSesionEsUn401YUnAnioInexistenteUn404() throws Exception {
        publicar(1, true);

        assertThat(get(rutaVigente(""), null).estado()).isEqualTo(401);
        assertThat(get("/api/v1/horarios/2999/vigente", admin).estado()).isEqualTo(404);
    }
}
