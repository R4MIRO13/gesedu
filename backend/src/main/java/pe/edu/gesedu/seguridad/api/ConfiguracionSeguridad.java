package pe.edu.gesedu.seguridad.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

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
                                          ManejadoresSeguridad manejadores) throws Exception {
        http
                // Sin cookies de sesión no hay CSRF que explotar: el token va en la cabecera Authorization
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/inicio-sesion").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
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
