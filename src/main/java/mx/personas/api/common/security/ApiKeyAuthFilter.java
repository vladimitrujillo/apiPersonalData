package mx.personas.api.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mx.personas.api.common.error.ApiError;
import mx.personas.api.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro de autenticacion ligero por clave de API (FR-023, research.md #6).
 * No usa Spring Security completo: es la opcion mas simple suficiente para un solo
 * cliente de confianza (Principio IV de la constitucion).
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private static final List<String> RUTAS_PUBLICAS = List.of(
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKeyEsperada;

    public ApiKeyAuthFilter(@Value("${app.security.api-key}") String apiKeyEsperada) {
        this.apiKeyEsperada = apiKeyEsperada;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return RUTAS_PUBLICAS.stream().anyMatch(patron -> pathMatcher.match(patron, request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String apiKeyRecibida = request.getHeader(API_KEY_HEADER);
        if (apiKeyRecibida == null || !apiKeyRecibida.equals(apiKeyEsperada)) {
            response.setStatus(ErrorCode.NO_AUTENTICADO.getHttpStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiError error = new ApiError(
                    ErrorCode.NO_AUTENTICADO.name(),
                    "Falta la clave de API o es inválida");
            objectMapper.writeValue(response.getWriter(), error);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
