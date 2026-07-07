package mx.personas.api.integration;

import mx.personas.api.common.AbstractIntegrationTest;
import mx.personas.api.common.TestJwt;
import mx.personas.api.usuario.model.Rol;
import mx.personas.api.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US3: continuidad de sesion via refresh token, con rotacion de un solo uso y bloqueo
 * inmediato al desactivar el usuario (FR-004, FR-005, FR-014, FR-022).
 */
@TestPropertySource(properties = {
        "app.security.admin-bootstrap-login=" + TestJwt.ADMIN_LOGIN,
        "app.security.admin-bootstrap-password=" + TestJwt.ADMIN_PASSWORD
})
class RefreshTokenIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @BeforeEach
    void asegurarAdmin() {
        TestJwt.loginAdmin(restTemplate, port);
    }

    private String loginUrl() {
        return "http://localhost:" + port + "/login";
    }

    private String refreshUrl() {
        return "http://localhost:" + port + "/refresh";
    }

    private ResponseEntity<Map> refresh(String refreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(refreshUrl(), HttpMethod.POST,
                new HttpEntity<>(Map.of("refreshToken", refreshToken), headers), Map.class);
    }

    @Test
    void loginRefreshYReintentoDelTokenOriginalYaRotado() {
        Map<String, Object> loginBody = Map.of("login", TestJwt.ADMIN_LOGIN, "password", TestJwt.ADMIN_PASSWORD);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> login = restTemplate.exchange(
                loginUrl(), HttpMethod.POST, new HttpEntity<>(loginBody, headers), Map.class);
        String refreshTokenOriginal = (String) login.getBody().get("refreshToken");

        ResponseEntity<Map> renovado = refresh(refreshTokenOriginal);
        assertThat(renovado.getStatusCode()).isEqualTo(HttpStatus.OK);
        String nuevoAccessToken = (String) renovado.getBody().get("accessToken");
        assertThat(nuevoAccessToken).isNotBlank();

        ResponseEntity<Map> usoDelNuevoToken = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas", HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(nuevoAccessToken)), Map.class);
        assertThat(usoDelNuevoToken.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> reintentoConTokenRotado = refresh(refreshTokenOriginal);
        assertThat(reintentoConTokenRotado.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshConTokenInvalidoRegresa401() {
        ResponseEntity<Map> respuesta = refresh("un-token-que-nunca-existio");
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(respuesta.getBody().get("codigo")).isEqualTo("NO_AUTENTICADO");
    }

    @Test
    void refreshDeUsuarioDesactivadoRegresa401MientrasElAccessTokenVigenteSigueFuncionando() {
        String login = "it-refresh-desactivado-" + System.nanoTime();
        TestJwt.sembrarUsuario(usuarioRepository, passwordEncoder, login, "clave-temporal-123", Rol.CAPTURISTA);

        Map<String, Object> loginBody = Map.of("login", login, "password", "clave-temporal-123");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> sesion = restTemplate.exchange(
                loginUrl(), HttpMethod.POST, new HttpEntity<>(loginBody, headers), Map.class);
        String accessToken = (String) sesion.getBody().get("accessToken");
        String refreshToken = (String) sesion.getBody().get("refreshToken");

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                usuarioRepository.findByLogin(login).ifPresent(u -> {
                    u.desactivar();
                    usuarioRepository.save(u);
                }));

        // El access token ya emitido sigue vigente hasta su expiracion natural (FR-014).
        ResponseEntity<Map> usoDeAccessTokenVigente = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas", HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(accessToken)), Map.class);
        assertThat(usoDeAccessTokenVigente.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Pero el refresh queda bloqueado de inmediato.
        ResponseEntity<Map> intentoDeRefresh = refresh(refreshToken);
        assertThat(intentoDeRefresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
