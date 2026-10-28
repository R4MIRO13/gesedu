package pe.edu.gesedu.seguridad.aplicacion;

import java.time.Duration;

/** Valor en claro del refresh token (solo viaja en la cookie) y su vigencia. */
public record TokenRefresco(String valor, Duration vigencia) {
}
