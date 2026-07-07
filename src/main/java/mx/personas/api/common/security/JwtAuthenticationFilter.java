package mx.personas.api.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mx.personas.api.usuario.model.Rol;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reemplaza a ApiKeyAuthFilter (FR-006a). Si el header Authorization trae un JWT valido,
 * establece el Authentication con la autoridad ROLE_<rol>; si falta o es invalido/expirado,
 * NO rechaza aqui: deja la peticion sin autenticar y continua la cadena, para que
 * authorizeHttpRequests().anyRequest().authenticated() y el AuthenticationEntryPoint
 * configurado en SecurityConfig produzcan el 401 uniforme (FR-002).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Claims claims = jwtService.parsearYValidar(token);
                String login = jwtService.extraerLogin(claims);
                Rol rol = jwtService.extraerRol(claims);
                List<GrantedAuthority> autoridades = List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()));
                var authentication = new UsernamePasswordAuthenticationToken(login, null, autoridades);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
