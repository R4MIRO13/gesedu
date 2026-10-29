package pe.edu.gesedu.transversal.notificaciones;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Plantillas HTML de correo ({@code classpath:plantillas/correo/<nombre>.html}). Reemplaza {@code {{clave}}} por
 * el valor escapado como HTML; una marca sin dato o un dato que no se usa es un error de programación.
 */
@Component
public class Plantillas {

    private static final Pattern MARCA = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}");

    public String renderizar(String nombre, Map<String, Object> datos) {
        String contenido = cargar(nombre);
        Matcher marcas = MARCA.matcher(contenido);
        StringBuilder resultado = new StringBuilder();
        while (marcas.find()) {
            String clave = marcas.group(1);
            if (!datos.containsKey(clave)) {
                throw new IllegalArgumentException("La plantilla " + nombre + " usa {{" + clave + "}} sin dato");
            }
            marcas.appendReplacement(resultado,
                    Matcher.quoteReplacement(HtmlUtils.htmlEscape(String.valueOf(datos.get(clave)), "UTF-8")));
        }
        marcas.appendTail(resultado);
        return resultado.toString();
    }

    private static String cargar(String nombre) {
        if (!nombre.matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException("Nombre de plantilla no válido: " + nombre);
        }
        try (InputStream entrada = new ClassPathResource("plantillas/correo/" + nombre + ".html").getInputStream()) {
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("No existe la plantilla de correo " + nombre, e);
        }
    }
}
