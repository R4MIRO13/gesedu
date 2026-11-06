package pe.edu.gesedu.seguridad.infraestructura;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Verificación de arranque de la clave del JWT (tarea 6.5, S-85, ISO/IEC 27002:2022 8.24): fuera de los perfiles
 * {@code local}, {@code demo} y {@code test} (incluido arrancar sin perfil), la aplicación no inicia si la clave es
 * la de desarrollo del repositorio o tiene menos de 256 bits. Así una configuración olvidada no deja el sistema
 * firmando tokens con una clave que cualquiera puede leer en GitHub.
 */
@Component
public class ValidacionClaveJwt implements InitializingBean {

    /** Perfiles en los que la clave de desarrollo está permitida. */
    static final Set<String> PERFILES_DE_DESARROLLO = Set.of("local", "demo", "test");

    /** Fragmentos que delatan una clave de desarrollo del repositorio (application-local.yml y .env.example). */
    private static final List<String> CLAVES_DE_DESARROLLO = List.of("cambia-esta-clave", "solo-para-desarrollo");

    static final int MINIMO_BYTES = 32;

    private final Environment entorno;
    private final PropiedadesSeguridad propiedades;

    ValidacionClaveJwt(Environment entorno, PropiedadesSeguridad propiedades) {
        this.entorno = entorno;
        this.propiedades = propiedades;
    }

    @Override
    public void afterPropertiesSet() {
        validar(Arrays.asList(entorno.getActiveProfiles()), propiedades.jwtSecreto());
    }

    /**
     * @throws IllegalStateException si el perfil no es de desarrollo y la clave es la de desarrollo o es demasiado corta
     */
    static void validar(List<String> perfilesActivos, String clave) {
        boolean desarrollo = perfilesActivos.stream().anyMatch(PERFILES_DE_DESARROLLO::contains);
        if (desarrollo) {
            return;
        }
        String perfiles = perfilesActivos.isEmpty() ? "ninguno" : String.join(", ", perfilesActivos);
        if (clave == null || clave.getBytes(StandardCharsets.UTF_8).length < MINIMO_BYTES) {
            throw new IllegalStateException("La clave del JWT (GESEDU_JWT_SECRET) tiene menos de 256 bits (mínimo "
                    + MINIMO_BYTES + " bytes) y el perfil activo (" + perfiles + ") no es local, demo ni test. "
                    + "Define GESEDU_JWT_SECRET con una clave aleatoria de al menos 32 caracteres.");
        }
        String minuscula = clave.toLowerCase(java.util.Locale.ROOT);
        if (CLAVES_DE_DESARROLLO.stream().anyMatch(minuscula::contains)) {
            throw new IllegalStateException("GESEDU_JWT_SECRET es la clave de desarrollo del repositorio y el perfil "
                    + "activo (" + perfiles + ") no es local, demo ni test. Define una clave aleatoria propia "
                    + "(por ejemplo, de Secrets Manager) antes de arrancar.");
        }
    }
}
