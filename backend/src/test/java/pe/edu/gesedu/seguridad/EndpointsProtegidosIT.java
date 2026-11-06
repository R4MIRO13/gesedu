package pe.edu.gesedu.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import pe.edu.gesedu.apoyo.BaseIT;

/**
 * Revisión de seguridad (tarea 6.5, RNF-07): todo endpoint de la aplicación declara {@code @PreAuthorize}, salvo una
 * lista explícita de públicos. Un controlador nuevo sin anotación hace fallar esta prueba.
 */
class EndpointsProtegidosIT extends BaseIT {

    /** Públicos por diseño (coinciden con los {@code permitAll} de ConfiguracionSeguridad). */
    private static final Set<String> PUBLICOS = Set.of(
            "POST /api/v1/auth/inicio-sesion",
            "POST /api/v1/auth/refresco",
            "POST /api/v1/auth/recuperacion",
            "POST /api/v1/auth/recuperacion/confirmacion",
            // Las URL prefirmadas del disco local las autoriza el token firmado (S-20); solo existen con tipo=disco
            "PUT /api/v1/archivos-locales/{token}",
            "GET /api/v1/archivos-locales/{token}");

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapeo;

    @Test
    void todoEndpointDeNegocioExigePreAuthorizeSalvoLosPublicosDeLaLista() {
        List<String> sinProteger = new ArrayList<>();
        List<String> encontrados = new ArrayList<>();
        mapeo.getHandlerMethods().forEach((info, metodo) -> {
            if (!metodo.getBeanType().getPackageName().startsWith("pe.edu.gesedu")) {
                return; // Springdoc, actuator, error: tienen su propia regla en la cadena de filtros
            }
            for (String clave : claves(info)) {
                encontrados.add(clave);
                if (!PUBLICOS.contains(clave) && !tienePreAuthorize(metodo)) {
                    sinProteger.add(clave + " (" + metodo.getShortLogMessage() + ")");
                }
            }
        });

        assertThat(encontrados).as("se encontraron endpoints de la aplicación").hasSizeGreaterThan(100);
        assertThat(sinProteger).as("endpoints sin @PreAuthorize").isEmpty();
    }

    @Test
    void laListaDePublicosNoTieneEntradasQueYaNoExisten() {
        List<String> encontrados = new ArrayList<>();
        mapeo.getHandlerMethods().keySet().forEach(info -> encontrados.addAll(claves(info)));
        // Los de archivos locales solo existen con gesedu.archivos.tipo=disco (el perfil de las pruebas)
        assertThat(encontrados).containsAll(PUBLICOS);
    }

    @Test
    void losPublicosDeAuthNoSonMasQueLosDeLaListaYSeProtegeElResto() {
        // Cualquier endpoint de /auth que no sea de la lista debe exigir sesión
        mapeo.getHandlerMethods().forEach((info, metodo) -> {
            for (String clave : claves(info)) {
                if (clave.contains("/api/v1/auth/") && !PUBLICOS.contains(clave)) {
                    assertThat(tienePreAuthorize(metodo)).as(clave).isTrue();
                }
            }
        });
    }

    private static boolean tienePreAuthorize(HandlerMethod metodo) {
        return AnnotatedElementUtils.hasAnnotation(metodo.getMethod(), PreAuthorize.class)
                || AnnotatedElementUtils.hasAnnotation(metodo.getBeanType(), PreAuthorize.class);
    }

    private static List<String> claves(RequestMappingInfo info) {
        List<String> claves = new ArrayList<>();
        Set<String> rutas = info.getPathPatternsCondition() == null ? Set.of()
                : info.getPathPatternsCondition().getPatternValues();
        Set<org.springframework.web.bind.annotation.RequestMethod> metodos = info.getMethodsCondition().getMethods();
        for (String ruta : rutas) {
            if (metodos.isEmpty()) {
                claves.add("ANY " + ruta);
            }
            metodos.forEach(m -> claves.add(m.name() + " " + ruta));
        }
        return claves;
    }
}
