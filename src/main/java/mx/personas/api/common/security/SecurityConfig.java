package mx.personas.api.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.personas.api.common.error.ApiError;
import mx.personas.api.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

/**
 * Reemplaza al filtro de API key (FR-006a). JWT sin estado, autorizacion por rol via
 * @PreAuthorize en los controllers (research.md #4), Swagger publico condicionado por
 * perfil (research.md #5).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final boolean swaggerPublico;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                           @Value("${app.security.swagger-publico}") boolean swaggerPublico) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.swaggerPublico = swaggerPublico;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/login", "/refresh").permitAll();
                    if (swaggerPublico) {
                        auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(ErrorCode.NO_AUTENTICADO.getHttpStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiError error = new ApiError(
                    ErrorCode.NO_AUTENTICADO.name(),
                    "Falta autenticacion o el token de acceso es invalido o expiro");
            writeJson(response, error);
        };
    }

    private void writeJson(jakarta.servlet.http.HttpServletResponse response, ApiError error) throws IOException {
        objectMapper.writeValue(response.getWriter(), error);
    }
}
