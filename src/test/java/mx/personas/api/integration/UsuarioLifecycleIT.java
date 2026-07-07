package mx.personas.api.integration;

import mx.personas.api.common.AbstractIntegrationTest;
import mx.personas.api.common.TestJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US4: alta, listado, desactivacion y restablecimiento de contrasena de usuarios del
 * sistema, y permanencia del login desactivado (FR-010 a FR-012, FR-016, FR-021).
 */
@TestPropertySource(properties = {
        "app.security.admin-bootstrap-login=" + TestJwt.ADMIN_LOGIN,
        "app.security.admin-bootstrap-password=" + TestJwt.ADMIN_PASSWORD
})
class UsuarioLifecycleIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String adminToken;

    @BeforeEach
    void autenticar() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        adminToken = TestJwt.loginAdmin(restTemplate, port);
    }

    private String usuariosUrl() {
        return "http://localhost:" + port + "/api/usuarios";
    }

    private String loginUrl() {
        return "http://localhost:" + port + "/login";
    }

    @Test
    void cicloCompletoCrearListarLoginDesactivarYRechazarLoginDuplicado() {
        String login = "it-lifecycle-" + System.nanoTime();
        Map<String, Object> cuerpoCrear = Map.of(
                "login", login, "password", "clave-temporal-123", "nombre", "Usuario Lifecycle", "rol", "CAPTURISTA");

        ResponseEntity<Map> creado = restTemplate.exchange(usuariosUrl(), HttpMethod.POST,
                new HttpEntity<>(cuerpoCrear, TestJwt.bearerHeaders(adminToken)), Map.class);
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(creado.getBody()).doesNotContainKey("password");
        assertThat(creado.getBody()).doesNotContainKey("passwordHash");
        String id = (String) creado.getBody().get("id");

        ResponseEntity<List> listado = restTemplate.exchange(usuariosUrl(), HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), List.class);
        assertThat(listado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listado.getBody()).extracting(u -> ((Map<?, ?>) u).get("login")).contains(login);

        Map<String, String> credencialesNuevoUsuario = Map.of("login", login, "password", "clave-temporal-123");
        var headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> loginExitoso = restTemplate.exchange(loginUrl(), HttpMethod.POST,
                new HttpEntity<>(credencialesNuevoUsuario, headers), Map.class);
        assertThat(loginExitoso.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> desactivado = restTemplate.exchange(usuariosUrl() + "/" + id + "/desactivar",
                HttpMethod.PATCH, new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Map.class);
        assertThat(desactivado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(desactivado.getBody().get("activo")).isEqualTo(false);

        ResponseEntity<Map> loginTrasDesactivar = restTemplate.exchange(loginUrl(), HttpMethod.POST,
                new HttpEntity<>(credencialesNuevoUsuario, headers), Map.class);
        assertThat(loginTrasDesactivar.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        Map<String, Object> cuerpoDuplicado = Map.of(
                "login", login, "password", "otra-clave-123", "nombre", "Otro Nombre", "rol", "CAPTURISTA");
        ResponseEntity<Map> intentoDuplicado = restTemplate.exchange(usuariosUrl(), HttpMethod.POST,
                new HttpEntity<>(cuerpoDuplicado, TestJwt.bearerHeaders(adminToken)), Map.class);
        assertThat(intentoDuplicado.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(intentoDuplicado.getBody().get("codigo")).isEqualTo("USUARIO_LOGIN_DUPLICADO");
    }

    @Test
    void restablecerContrasenaInvalidaLaAnteriorDeInmediato() {
        String login = "it-reset-" + System.nanoTime();
        Map<String, Object> cuerpoCrear = Map.of(
                "login", login, "password", "clave-original-123", "nombre", "Usuario Reset", "rol", "CAPTURISTA");
        ResponseEntity<Map> creado = restTemplate.exchange(usuariosUrl(), HttpMethod.POST,
                new HttpEntity<>(cuerpoCrear, TestJwt.bearerHeaders(adminToken)), Map.class);
        String id = (String) creado.getBody().get("id");

        ResponseEntity<Void> reset = restTemplate.exchange(usuariosUrl() + "/" + id + "/contrasena",
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("nuevaContrasena", "clave-nueva-456"), TestJwt.bearerHeaders(adminToken)),
                Void.class);
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        var headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> loginConClaveAnterior = restTemplate.exchange(loginUrl(), HttpMethod.POST,
                new HttpEntity<>(Map.of("login", login, "password", "clave-original-123"), headers), Map.class);
        assertThat(loginConClaveAnterior.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Map> loginConClaveNueva = restTemplate.exchange(loginUrl(), HttpMethod.POST,
                new HttpEntity<>(Map.of("login", login, "password", "clave-nueva-456"), headers), Map.class);
        assertThat(loginConClaveNueva.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
