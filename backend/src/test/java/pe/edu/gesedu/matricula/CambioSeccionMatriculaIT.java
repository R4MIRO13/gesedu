package pe.edu.gesedu.matricula;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import pe.edu.gesedu.apoyo.DatosMatricula.Escenario;

/** RF-05 (C-43): cambio de grado o sección de una matrícula confirmada, con vacantes consistentes y auditoría. */
class CambioSeccionMatriculaIT extends BaseMatriculaIT {

    private Respuesta cambiar(String matriculaId, String seccionId, String token) throws Exception {
        return enviar("PATCH", "/api/v1/matriculas/" + matriculaId + "/seccion",
                json.writeValueAsString(Map.of("seccionId", seccionId)), token, null);
    }

    private int vacantes(String seccionId) {
        return jdbc.queryForObject("SELECT vacantes_disponibles FROM academico.seccion WHERE id = ?", Integer.class, seccionId);
    }

    private String seccionDe(String matriculaId) {
        return jdbc.queryForObject("SELECT seccion_id FROM matricula.matricula WHERE id = ?", String.class, matriculaId);
    }

    private void fijarVacantes(String seccionId, int vacantes) {
        jdbc.update("UPDATE academico.seccion SET vacantes_disponibles = ? WHERE id = ?", vacantes, seccionId);
    }

    /** Alumno con su matrícula confirmada en la sección indicada. */
    private String matricular(Escenario e, String apoderadoId, String nombre, String seccionId) {
        String alumno = datos.alumnoDe(apoderadoId, nombre);
        return datos.matriculaConObligaciones(e, apoderadoId, alumno, seccionId);
    }

    @Test
    void elCambioMueveLaMatriculaYAjustaLasVacantesDeAmbasSecciones() throws Exception {
        Familia f = crearFamilia();
        Escenario e = f.escenario();
        fijarVacantes(e.seccion3A(), 5);
        fijarVacantes(e.seccion3B(), 8);

        Respuesta r = cambiar(f.matriculaId(), e.seccion3B(), tokenAdministrador());

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("seccionAnterior").asString()).isEqualTo("3° A");
        assertThat(r.cuerpo().get("seccionNueva").asString()).isEqualTo("3° B");
        assertThat(r.cuerpo().get("vacantesOrigen").asInt()).isEqualTo(6);
        assertThat(r.cuerpo().get("vacantesDestino").asInt()).isEqualTo(7);
        assertThat(r.cuerpo().get("sinCambios").asBoolean()).isFalse();
        assertThat(seccionDe(f.matriculaId())).isEqualTo(e.seccion3B());
        assertThat(vacantes(e.seccion3A())).isEqualTo(6);
        assertThat(vacantes(e.seccion3B())).isEqualTo(7);
        // La consulta del apoderado ya muestra la sección nueva
        assertThat(get("/api/v1/matriculas/mias", f.apoderado().token()).cuerpo().get(0).get("matricula").get("seccion").asString())
                .isEqualTo("3° B");
        // Auditoría con la sección anterior y la nueva
        assertThat(contar("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = 'CAMBIAR_SECCION_MATRICULA' "
                + "AND id_entidad_afectada = ?", f.matriculaId())).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM auditoria.cambio_auditoria c JOIN auditoria.registro_auditoria r "
                + "ON r.id = c.registro_auditoria_id WHERE r.id_entidad_afectada = ? AND c.campo = 'seccion' "
                + "AND c.valor_anterior = '3° A' AND c.valor_nuevo = '3° B'", f.matriculaId())).isEqualTo(1);
    }

    @Test
    void tambienSePuedeCambiarDeGradoDentroDelMismoAnio() throws Exception {
        Familia f = crearFamilia();
        Escenario e = f.escenario();
        fijarVacantes(e.seccion3A(), 5);
        fijarVacantes(e.seccion2A(), 2);

        Respuesta r = cambiar(f.matriculaId(), e.seccion2A(), tokenAdministrador());

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("seccionNueva").asString()).isEqualTo("2° A");
        assertThat(vacantes(e.seccion3A())).isEqualTo(6);
        assertThat(vacantes(e.seccion2A())).isEqualTo(1);
        assertThat(get("/api/v1/matriculas/mias", f.apoderado().token()).cuerpo().get(0).get("matricula").get("grado").asString())
                .isEqualTo("2° de secundaria");
    }

    @Test
    void unDestinoLlenoRespondeSinVacanteYNoLiberaNadaDelOrigen() throws Exception {
        Familia f = crearFamilia();
        Escenario e = f.escenario();
        fijarVacantes(e.seccion3A(), 5);
        fijarVacantes(e.seccion3B(), 0);

        Respuesta r = cambiar(f.matriculaId(), e.seccion3B(), tokenAdministrador());

        assertThat(r.estado()).isEqualTo(409);
        assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("SIN_VACANTE");
        assertThat(seccionDe(f.matriculaId())).isEqualTo(e.seccion3A());
        assertThat(vacantes(e.seccion3A())).as("el origen no recibe nada").isEqualTo(5);
        assertThat(vacantes(e.seccion3B())).isZero();
        assertThat(contar("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = 'CAMBIAR_SECCION_MATRICULA' "
                + "AND id_entidad_afectada = ?", f.matriculaId())).isZero();
    }

    @Test
    void cambiarALaMismaSeccionNoHaceNadaNiSeAudita() throws Exception {
        Familia f = crearFamilia();
        Escenario e = f.escenario();
        fijarVacantes(e.seccion3A(), 5);

        Respuesta r = cambiar(f.matriculaId(), e.seccion3A(), tokenAdministrador());

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("sinCambios").asBoolean()).isTrue();
        assertThat(vacantes(e.seccion3A())).isEqualTo(5);
        assertThat(contar("SELECT count(*) FROM auditoria.registro_auditoria WHERE accion = 'CAMBIAR_SECCION_MATRICULA' "
                + "AND id_entidad_afectada = ?", f.matriculaId())).isZero();
    }

    @Test
    void unaMatriculaAnuladaOdeUnAnioCerradoNoSeCambia() throws Exception {
        Familia anulada = crearFamilia();
        Familia cerrada = crearFamilia();
        jdbc.update("UPDATE matricula.matricula SET estado = 'ANULADA' WHERE id = ?", anulada.matriculaId());
        jdbc.update("UPDATE academico.anio_escolar SET estado = 'CERRADO' WHERE id = ?", cerrada.escenario().anioId());
        String admin = tokenAdministrador();

        Respuesta a = cambiar(anulada.matriculaId(), anulada.escenario().seccion3B(), admin);
        Respuesta c = cambiar(cerrada.matriculaId(), cerrada.escenario().seccion3B(), admin);

        for (Respuesta r : List.of(a, c)) {
            assertThat(r.estado()).isEqualTo(422);
            assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("MATRICULA_NO_VIGENTE");
        }
        assertThat(seccionDe(anulada.matriculaId())).isEqualTo(anulada.escenario().seccion3A());
        assertThat(seccionDe(cerrada.matriculaId())).isEqualTo(cerrada.escenario().seccion3A());
    }

    @Test
    void unaSeccionDeOtroAnioOInexistenteSeRechaza() throws Exception {
        Familia f = crearFamilia();
        Familia otra = crearFamilia();
        String admin = tokenAdministrador();

        Respuesta otroAnio = cambiar(f.matriculaId(), otra.escenario().seccion3B(), admin);
        Respuesta inexistente = cambiar(f.matriculaId(), "00000000-0000-0000-0000-000000000000", admin);
        Respuesta sinMatricula = cambiar("00000000-0000-0000-0000-000000000000", f.escenario().seccion3B(), admin);

        assertThat(otroAnio.estado()).isEqualTo(400);
        assertThat(otroAnio.cuerpo().get("detail").asString()).contains("no pertenece al año");
        assertThat(inexistente.estado()).isEqualTo(404);
        assertThat(sinMatricula.estado()).isEqualTo(404);
        assertThat(seccionDe(f.matriculaId())).isEqualTo(f.escenario().seccion3A());
    }

    @Test
    void veinteCambiosSobreUnaSolaVacanteDejanSoloUnExitoYVacantesCoherentes() throws Exception {
        Familia f = crearFamilia();
        Escenario e = f.escenario();
        fijarVacantes(e.seccion3A(), 10);
        fijarVacantes(e.seccion3B(), 1);
        List<String> matriculas = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            matriculas.add(matricular(e, f.apoderado().apoderadoId(), "Alumno" + i, e.seccion3A()));
        }
        String admin = tokenAdministrador();
        CountDownLatch salida = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(20);
        try {
            List<Future<Respuesta>> futuros = new ArrayList<>();
            for (String m : matriculas) {
                futuros.add(pool.submit(() -> {
                    salida.await();
                    return cambiar(m, e.seccion3B(), admin);
                }));
            }
            salida.countDown();
            int exitos = 0;
            int llenas = 0;
            for (Future<Respuesta> futuro : futuros) {
                Respuesta r = futuro.get();
                if (r.estado() == 200) {
                    exitos++;
                } else {
                    assertThat(r.estado()).isEqualTo(409);
                    assertThat(r.cuerpo().get("codigo").asString()).isEqualTo("SIN_VACANTE");
                    llenas++;
                }
            }
            assertThat(exitos).isEqualTo(1);
            assertThat(llenas).isEqualTo(19);
        } finally {
            pool.shutdownNow();
        }
        assertThat(vacantes(e.seccion3B())).as("nunca por debajo de cero").isZero();
        assertThat(vacantes(e.seccion3A())).as("solo el traslado exitoso devolvió su vacante").isEqualTo(11);
        assertThat(contar("SELECT count(*) FROM matricula.matricula WHERE seccion_id = ?", e.seccion3B())).isEqualTo(1);
    }

    @Test
    void doTrasladosCruzadosSimultaneosNoSeInterbloqueanYDejanLasVacantesIgual() throws Exception {
        Familia f = crearFamilia();
        Escenario e = f.escenario();
        fijarVacantes(e.seccion3A(), 5);
        fijarVacantes(e.seccion3B(), 5);
        String enA = f.matriculaId();
        String enB = matricular(e, f.apoderado().apoderadoId(), "EnB", e.seccion3B());
        String admin = tokenAdministrador();

        for (int ronda = 0; ronda < 3; ronda++) {
            String desdeA = ronda % 2 == 0 ? enA : enB;
            String desdeB = ronda % 2 == 0 ? enB : enA;
            String destinoDeA = seccionDe(desdeA).equals(e.seccion3A()) ? e.seccion3B() : e.seccion3A();
            String destinoDeB = seccionDe(desdeB).equals(e.seccion3A()) ? e.seccion3B() : e.seccion3A();
            CountDownLatch salida = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<Respuesta> uno = pool.submit(() -> {
                    salida.await();
                    return cambiar(desdeA, destinoDeA, admin);
                });
                Future<Respuesta> otro = pool.submit(() -> {
                    salida.await();
                    return cambiar(desdeB, destinoDeB, admin);
                });
                salida.countDown();
                assertThat(uno.get().estado()).isEqualTo(200);
                assertThat(otro.get().estado()).isEqualTo(200);
            } finally {
                pool.shutdownNow();
            }
            assertThat(vacantes(e.seccion3A())).isEqualTo(5);
            assertThat(vacantes(e.seccion3B())).isEqualTo(5);
        }
    }

    @Test
    void lasVacantesNuncaSuperanLaCapacidadAunqueElOrigenEstuvieraInconsistente() throws Exception {
        Familia f = crearFamilia();
        Escenario e = f.escenario();
        // El origen figura con todas sus vacantes libres aunque el alumno está matriculado: dato inconsistente
        fijarVacantes(e.seccion3A(), 30);
        fijarVacantes(e.seccion3B(), 5);

        Respuesta r = cambiar(f.matriculaId(), e.seccion3B(), tokenAdministrador());

        assertThat(r.estado()).isEqualTo(500);
        assertThat(vacantes(e.seccion3A())).isEqualTo(30);
        assertThat(vacantes(e.seccion3B())).as("el destino no se toca").isEqualTo(5);
        assertThat(seccionDe(f.matriculaId())).isEqualTo(e.seccion3A());
    }

    @Test
    void elAdministradorVeLaMatriculaVigenteConLasSeccionesDeSuAnio() throws Exception {
        Familia f = crearFamilia();
        Escenario e = f.escenario();
        fijarVacantes(e.seccion3B(), 7);

        Respuesta r = get("/api/v1/matriculas/alumno/" + f.alumnoId(), tokenAdministrador());

        assertThat(r.estado()).isEqualTo(200);
        assertThat(r.cuerpo().get("matriculaId").asString()).isEqualTo(f.matriculaId());
        assertThat(r.cuerpo().get("seccion").asString()).isEqualTo("3° A");
        assertThat(r.cuerpo().get("grado").asString()).isEqualTo("3° de secundaria");
        assertThat(r.cuerpo().get("secciones")).hasSize(3);
        assertThat(r.cuerpo().get("secciones").get(0).get("nombre").asString()).isEqualTo("2° A");
        var tresB = r.cuerpo().get("secciones").get(2);
        assertThat(tresB.get("nombre").asString()).isEqualTo("3° B");
        assertThat(tresB.get("vacantes").asInt()).isEqualTo(7);
        assertThat(tresB.get("capacidad").asInt()).isEqualTo(30);
        // Un estudiante sin matrícula vigente no tiene qué trasladar
        ApoderadoConCuenta ap = crearApoderadoConCuenta();
        assertThat(get("/api/v1/matriculas/alumno/" + datos.alumnoDe(ap.apoderadoId(), "Sin"), tokenAdministrador()).estado()).isEqualTo(404);
    }

    @Test
    void soloQuienPuedeRegistrarMatriculasCambiaSecciones() throws Exception {
        Familia f = crearFamilia();
        String destino = f.escenario().seccion3B();

        for (String token : List.of(f.apoderado().token(), tokenDe(crearUsuario("DOCENTE", true)), tokenDe(crearUsuario("ALUMNO", true)))) {
            assertThat(cambiar(f.matriculaId(), destino, token).estado()).isEqualTo(403);
            assertThat(get("/api/v1/matriculas/alumno/" + f.alumnoId(), token).estado()).isEqualTo(403);
        }
        assertThat(cambiar(f.matriculaId(), destino, null).estado()).isEqualTo(401);
        assertThat(seccionDe(f.matriculaId())).isEqualTo(f.escenario().seccion3A());
    }
}
