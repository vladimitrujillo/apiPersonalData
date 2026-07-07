package mx.personas.api.common.audit;

import mx.personas.api.usuario.model.Usuario;
import mx.personas.api.usuario.repository.UsuarioRepository;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Resuelve el autor de cada escritura auditada a partir del usuario autenticado.
 *
 * El JwtAuthenticationFilter de 002-autenticacion-autorizacion (ya implementado) coloca
 * como principal un String (el login), no un objeto enriquecido con el id del usuario.
 * Este es el fallback explicitamente documentado y pre-aprobado en
 * specs/003-auditoria-personas/research.md §1 para ese caso: una consulta a
 * UsuarioRepository por login, en vez de modificar 002 (ya implementado y probado).
 */
@Component("securityAuditorAware")
public class SecurityAuditorAware implements AuditorAware<UUID> {

    private final UsuarioRepository usuarioRepository;

    public SecurityAuditorAware(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public Optional<UUID> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return usuarioRepository.findByLogin(authentication.getName()).map(Usuario::getId);
    }
}
