package pe.edu.gesedu.seguridad.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

import pe.edu.gesedu.seguridad.aplicacion.ConvertidorJwt;

/**
 * API sin sesión de servidor: cada petición lleva su JWT. Todo requiere autenticación salvo el inicio de
 * sesión y la salud del servicio; el permiso de cada endpoint se declara con {@code @PreAuthorize}
 * (DA-05) y el alcance (mi sección, mis hijos) se verifica en el servicio.
 */
@Configuration
@EnableMethodSecurity
class ConfiguracionSeguridad {

    @Bean
    SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, ConvertidorJwt convertidor,
                                          ManejadoresSeguridad manejadores,
                                          @Value("${gesedu.seguridad.limite-auth-por-minuto}") int limiteAuth,
                                          JsonMapper json) throws Exception {
        http
                .addFilterBefore(new LimitadorPeticionesAuth(limiteAuth, json),
                        BearerTokenAuthenticationFilter.class)
                // Sin cookies de sesión no hay CSRF que explotar: el token va en la cabecera Authorization
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/inicio-sesion", "/api/v1/auth/refresco")
                        .permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        // URL prefirmadas del desarrollo local: las autoriza el token firmado, no la sesión (S-20)
                        .requestMatchers("/api/v1/archivos-locales/**").permitAll()
                        // Documentación de la API (Swagger UI): pública en la demo
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(convertidor))
                        .authenticationEntryPoint(manejadores)
                        .accessDeniedHandler(manejadores))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(manejadores)
                        .accessDeniedHandler(manejadores));
        return http.build();
    }
}
