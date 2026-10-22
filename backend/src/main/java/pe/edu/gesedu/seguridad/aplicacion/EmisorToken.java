package pe.edu.gesedu.seguridad.aplicacion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import pe.edu.gesedu.seguridad.infraestructura.PropiedadesSeguridad;

/** Emite el token de acceso JWT (HS256). El token solo identifica al usuario; los permisos se leen en cada petición. */
@Component
public class EmisorToken {

    static final String EMISOR = "gesedu";

    public record TokenEmitido(String valor, Instant expiraEn) {
    }

    private final JwtEncoder codificador;
    private final PropiedadesSeguridad propiedades;
    private final Clock reloj;

    EmisorToken(JwtEncoder codificador, PropiedadesSeguridad propiedades, Clock reloj) {
        this.codificador = codificador;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    public TokenEmitido emitir(String usuarioId) {
        Instant emitidoEn = reloj.instant();
        Instant expiraEn = emitidoEn.plus(Duration.ofMinutes(propiedades.jwtMinutos()));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(EMISOR)
                .subject(usuarioId)
                .issuedAt(emitidoEn)
                .expiresAt(expiraEn)
                .build();
        String valor = codificador
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new TokenEmitido(valor, expiraEn);
    }
}
