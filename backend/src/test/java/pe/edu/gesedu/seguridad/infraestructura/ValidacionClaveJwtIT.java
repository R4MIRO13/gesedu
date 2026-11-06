package pe.edu.gesedu.seguridad.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Verificación de arranque de la clave del JWT (S-85): se arma el contexto con la propiedad y el perfil reales. No
 * necesita base de datos, pero se llama IT porque prueba el arranque de un contexto de Spring.
 */
class ValidacionClaveJwtIT {

    private static final String CLAVE_DESARROLLO = "cambia-esta-clave-solo-para-desarrollo-local-0123456789";
    private static final String CLAVE_PROPIA = "k7Qp2xVn9LsT4wZc8RbH5mYd3FgJ6aUe1oNi0XtS";

    private ApplicationContextRunner contexto(String clave, String perfiles) {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(Configuracion.class)
                .withPropertyValues("gesedu.seguridad.jwt-secreto=" + clave,
                        "gesedu.seguridad.jwt-minutos=15", "gesedu.seguridad.max-intentos=5",
                        "gesedu.seguridad.bloqueo-minutos=15", "gesedu.seguridad.refresh-dias=7",
                        "gesedu.seguridad.cookie-secure=true", "gesedu.seguridad.limite-auth-por-minuto=30");
        return perfiles.isEmpty() ? runner : runner.withPropertyValues("spring.profiles.active=" + perfiles);
    }

    @EnableConfigurationProperties(PropiedadesSeguridad.class)
    @org.springframework.context.annotation.Import(ValidacionClaveJwt.class)
    static class Configuracion {
    }

    @ParameterizedTest
    @ValueSource(strings = {"aws", "prod", "qa", "local-aws"})
    void fueraDeLosPerfilesDeDesarrolloLaClaveDeDesarrolloImpideArrancar(String perfil) {
        contexto(CLAVE_DESARROLLO, perfil).run(c -> {
            assertThat(c).hasFailed();
            assertThat(c.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class)
                    .rootCause().hasMessageContaining("clave de desarrollo").hasMessageContaining("GESEDU_JWT_SECRET")
                    .hasMessageContaining(perfil);
        });
    }

    @Test
    void sinNingunPerfilLaClaveDeDesarrolloTampocoArranca() {
        contexto(CLAVE_DESARROLLO, "").run(c -> {
            assertThat(c).hasFailed();
            assertThat(c.getStartupFailure()).rootCause().hasMessageContaining("ninguno");
        });
    }

    @Test
    void unaClaveDeMenosDe256BitsImpideArrancarFueraDeDesarrollo() {
        // 31 caracteres ASCII = 248 bits. La validación de la propiedad (@Size 32) también la rechazaría; el mensaje
        // propio cubre el caso de una clave con caracteres de varios bytes que sí pasa por @Size.
        contexto("a".repeat(31), "aws").run(c -> assertThat(c).hasFailed());
    }

    @Test
    void unaClavePropiaDe256BitsArrancaFueraDeDesarrollo() {
        contexto(CLAVE_PROPIA, "aws").run(c -> assertThat(c).hasNotFailed());
        contexto(CLAVE_PROPIA, "").run(c -> assertThat(c).hasNotFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "demo", "test", "local,demo", "aws,local"})
    void enLosPerfilesDeDesarrolloLaClaveDeDesarrolloSeAcepta(String perfiles) {
        contexto(CLAVE_DESARROLLO, perfiles).run(c -> assertThat(c).hasNotFailed());
    }

    @Test
    void laLogicaRechazaUnaClaveCortaConElMensajeDeBits() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> ValidacionClaveJwt.validar(java.util.List.of("aws"), "corta"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("menos de 256 bits");
    }
}
