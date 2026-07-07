package mx.personas.api.auth;

import mx.personas.api.auth.dto.TokenResponseDTO;
import mx.personas.api.auth.service.AuthService;
import mx.personas.api.common.error.ApiException;
import mx.personas.api.common.error.CredencialesInvalidasException;
import mx.personas.api.common.error.ErrorCode;
import mx.personas.api.common.security.JwtService;
import mx.personas.api.usuario.model.RefreshToken;
import mx.personas.api.usuario.model.Rol;
import mx.personas.api.usuario.model.Usuario;
import mx.personas.api.usuario.repository.RefreshTokenRepository;
import mx.personas.api.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService authService() {
        return new AuthService(usuarioRepository, refreshTokenRepository, passwordEncoder, jwtService);
    }

    private void mockearEmisionDeTokens() {
        lenient().when(jwtService.generarAccessToken(any(), any())).thenReturn("un-jwt");
        lenient().when(jwtService.expiracionAccessToken()).thenReturn(Instant.now().plusSeconds(900));
        lenient().when(jwtService.generarTokenOpacoRefresh()).thenReturn("un-refresh-opaco");
        lenient().when(jwtService.hashSha256(any())).thenReturn("un-hash");
        lenient().when(jwtService.expiracionRefreshToken()).thenReturn(Instant.now().plusSeconds(86400));
    }

    @Test
    void loginConCredencialesValidasEmiteTokens() {
        Usuario usuario = new Usuario("admin", "hash-almacenado", "Admin", Rol.ADMIN);
        when(usuarioRepository.findByLogin("admin")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("clave-correcta", "hash-almacenado")).thenReturn(true);
        mockearEmisionDeTokens();

        TokenResponseDTO respuesta = authService().login("admin", "clave-correcta");

        assertThat(respuesta.accessToken()).isEqualTo("un-jwt");
        assertThat(respuesta.refreshToken()).isEqualTo("un-refresh-opaco");
    }

    @Test
    void loginConUsuarioInexistenteRegresa401Generico() {
        when(usuarioRepository.findByLogin("no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService().login("no-existe", "lo-que-sea"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NO_AUTENTICADO);
    }

    @Test
    void loginConContrasenaIncorrectaRegresaElMismo401Generico() {
        Usuario usuario = new Usuario("admin", "hash-almacenado", "Admin", Rol.ADMIN);
        when(usuarioRepository.findByLogin("admin")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("clave-incorrecta", "hash-almacenado")).thenReturn(false);

        assertThatThrownBy(() -> authService().login("admin", "clave-incorrecta"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NO_AUTENTICADO);
    }

    @Test
    void loginConUsuarioDesactivadoRegresaElMismo401GenericoSinIntentarVerificarContrasena() {
        Usuario usuario = new Usuario("jperez", "hash-almacenado", "Juan Pérez", Rol.CAPTURISTA);
        usuario.desactivar();
        when(usuarioRepository.findByLogin("jperez")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService().login("jperez", "cualquier-clave"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NO_AUTENTICADO);
    }

    @Test
    void refreshConTokenVigenteEmiteNuevosTokensYRevocaElAnterior() {
        Usuario usuario = new Usuario("admin", "hash-almacenado", "Admin", Rol.ADMIN);
        RefreshToken tokenVigente = new RefreshToken(usuario, "hash-almacenado", OffsetDateTime.now().plusDays(1));
        when(refreshTokenRepository.findByTokenHash("un-hash")).thenReturn(Optional.of(tokenVigente));
        mockearEmisionDeTokens();

        TokenResponseDTO respuesta = authService().refresh("un-refresh-en-claro");

        assertThat(respuesta.accessToken()).isEqualTo("un-jwt");
        assertThat(tokenVigente.isRevocado()).isTrue();
    }

    @Test
    void refreshConHashInexistenteRegresa401() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());
        lenient().when(jwtService.hashSha256(any())).thenReturn("un-hash");

        assertThatThrownBy(() -> authService().refresh("token-desconocido"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NO_AUTENTICADO);
    }

    @Test
    void refreshConTokenExpiradoRegresa401() {
        Usuario usuario = new Usuario("admin", "hash-almacenado", "Admin", Rol.ADMIN);
        RefreshToken tokenExpirado = new RefreshToken(usuario, "hash-almacenado", OffsetDateTime.now().minusDays(1));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(tokenExpirado));
        lenient().when(jwtService.hashSha256(any())).thenReturn("un-hash");

        assertThatThrownBy(() -> authService().refresh("token-expirado"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NO_AUTENTICADO);
    }

    @Test
    void refreshConTokenYaUsadoRevocadoRegresa401() {
        Usuario usuario = new Usuario("admin", "hash-almacenado", "Admin", Rol.ADMIN);
        RefreshToken tokenRevocado = new RefreshToken(usuario, "hash-almacenado", OffsetDateTime.now().plusDays(1));
        tokenRevocado.revocar();
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(tokenRevocado));
        lenient().when(jwtService.hashSha256(any())).thenReturn("un-hash");

        assertThatThrownBy(() -> authService().refresh("token-ya-rotado"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NO_AUTENTICADO);
    }

    @Test
    void refreshDeUsuarioDesactivadoRegresa401AunqueElTokenSigaVigente() {
        Usuario usuario = new Usuario("jperez", "hash-almacenado", "Juan Pérez", Rol.CAPTURISTA);
        usuario.desactivar();
        RefreshToken tokenVigente = new RefreshToken(usuario, "hash-almacenado", OffsetDateTime.now().plusDays(1));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(tokenVigente));
        lenient().when(jwtService.hashSha256(any())).thenReturn("un-hash");

        assertThatThrownBy(() -> authService().refresh("token-de-usuario-desactivado"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NO_AUTENTICADO);
    }
}
