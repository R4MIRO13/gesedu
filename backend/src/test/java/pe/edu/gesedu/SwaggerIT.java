package pe.edu.gesedu;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import pe.edu.gesedu.apoyo.BaseIT;

/** Swagger UI y OpenAPI 3 agrupados por módulo, con bearer y ejemplos (docs/PLAN.md § 7, fase 1, tarea 4). */
class SwaggerIT extends BaseIT {

    private JsonNode grupo(String nombre) throws Exception {
        Respuesta r = get("/v3/api-docs/" + nombre, null);
        assertThat(r.estado()).as(nombre).isEqualTo(200);
        return r.cuerpo();
    }

    @Test
    void laDocumentacionEsOpenApi3ConBearer() throws Exception {
        JsonNode api = get("/v3/api-docs", null).cuerpo();

        assertThat(api.get("openapi").asString()).startsWith("3.");
        assertThat(api.get("components").get("securitySchemes").get("bearer").get("scheme").asString()).isEqualTo("bearer");
        assertThat(get("/swagger-ui.html", null).estado()).isIn(200, 302);
    }

    @Test
    void hayUnGrupoPorModuloConSusEndpoints() throws Exception {
        assertThat(paths(grupo("1-seguridad"))).contains("/api/v1/auth/inicio-sesion", "/api/v1/auth/refresco",
                "/api/v1/auth/cierre-sesion", "/api/v1/auth/yo");
        assertThat(paths(grupo("2-horarios"))).contains("/api/v1/anios-escolares/{anio}/bloques",
                "/api/v1/horarios/{anio}/borrador", "/api/v1/horarios/{anio}/borrador/opciones",
                "/api/v1/horarios/{anio}/borrador/validaciones", "/api/v1/horarios/{anio}/borrador/asignaciones",
                "/api/v1/horarios/{anio}/borrador/asignaciones/{id}");
        assertThat(paths(grupo("3-auditoria"))).contains("/api/v1/auditoria/registros",
                "/api/v1/auditoria/registros/{id}", "/api/v1/auditoria/registros/exportacion",
                "/api/v1/auditoria/catalogos");
        assertThat(paths(grupo("8-evaluacion"))).contains("/api/v1/asignaciones-docentes/mias", "/api/v1/asignaciones-docentes/{id}/configuracion",
                "/api/v1/asignaciones-docentes/{id}/registros", "/api/v1/asignaciones-docentes/{id}/notas-finales",
                "/api/v1/asignaciones-docentes/{id}/notas-finales/calculo", "/api/v1/anios-escolares/{anio}/bimestres/{numero}/avance",
                "/api/v1/anios-escolares/{anio}/bimestres/{numero}/consolidacion", "/api/v1/apoderados/yo/alumnos",
                "/api/v1/alumnos/{alumnoId}/cursos", "/api/v1/alumnos/{alumnoId}/libreta", "/api/v1/alumnos/{alumnoId}/libretas");
    }

    @Test
    void losEndpointsDeEdicionDeclaranIfMatchYLosCuerposTienenEjemplos() throws Exception {
        JsonNode horarios = grupo("2-horarios");
        JsonNode crear = horarios.get("paths").get("/api/v1/horarios/{anio}/borrador/asignaciones").get("post");
        assertThat(crear.get("summary").asString()).contains("If-Match");
        assertThat(crear.get("parameters").valueStream().map(p -> p.get("name").asString()).toList())
                .contains("If-Match", "anio");

        JsonNode inicio = grupo("1-seguridad").get("components").get("schemas").get("InicioSesionRequest");
        assertThat(inicio.get("properties").get("correo").get("example").asString()).isEqualTo("admin@demo.gesedu.pe");
        JsonNode asignacion = horarios.get("components").get("schemas").get("AsignacionRequest");
        assertThat(asignacion.get("properties").get("cursoId").get("description").asString()).contains("Matemática");
    }

    private static List<String> paths(JsonNode api) {
        return api.get("paths").propertyNames().stream().toList();
    }
}
