package mx.personas.api.common.audit;

import mx.personas.api.usuario.model.Rol;
import mx.personas.api.usuario.model.Usuario;
import mx.personas.api.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Resuelve "quien" para cada fila de auditoria/historial (FR-001, FR-002). No requiere
 * contexto de Spring: fija el SecurityContext directamente, igual que el filtro real lo
 * hace en cada request.
 */
@ExtendWith(MockitoExtension.class)
class SecurityAuditorAwareTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @AfterEach
    void limpiarContextoDeSeguridad() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginAutenticadoYExistenteResuelveElUuidDelUsuario() {
        UUID idEsperado = UUID.randomUUID();
        Usuario usuario = new Usuario("admin", "hash", "Admin", Rol.ADMIN);
        setId(usuario, idEsperado);
        when(usuarioRepository.findByLogin("admin")).thenReturn(Optional.of(usuario));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        Optional<UUID> resultado = new SecurityAuditorAware(usuarioRepository).getCurrentAuditor();

        assertThat(resultado).contains(idEsperado);
    }

    @Test
    void sinAutenticacionRegresaVacio() {
        SecurityContextHolder.clearContext();

        Optional<UUID> resultado = new SecurityAuditorAware(usuarioRepository).getCurrentAuditor();

        assertThat(resultado).isEmpty();
    }

    @Test
    void autenticacionNoAutenticadaRegresaVacio() {
        var authentication = new TestingAuthenticationToken("admin", null, List.of());
        authentication.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        Optional<UUID> resultado = new SecurityAuditorAware(usuarioRepository).getCurrentAuditor();

        assertThat(resultado).isEmpty();
    }

    @Test
    void autenticacionAnonimaRegresaVacio() {
        List<GrantedAuthority> autoridades = List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"));
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("clave", "anonymousUser", autoridades));

        Optional<UUID> resultado = new SecurityAuditorAware(usuarioRepository).getCurrentAuditor();

        assertThat(resultado).isEmpty();
    }

    @Test
    void loginQueNoCorrespondeANingunUsuarioRegresaVacio() {
        when(usuarioRepository.findByLogin("fantasma")).thenReturn(Optional.empty());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("fantasma", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        Optional<UUID> resultado = new SecurityAuditorAware(usuarioRepository).getCurrentAuditor();

        assertThat(resultado).isEmpty();
    }

    private void setId(Usuario usuario, UUID id) {
        org.springframework.test.util.ReflectionTestUtils.setField(usuario, "id", id);
    }
}
