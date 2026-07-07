package mx.personas.api.integration;

import mx.personas.api.common.AbstractIntegrationTest;
import mx.personas.api.common.TestJwt;
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
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US1: acceso autenticado al API existente (FR-001, FR-002, FR-003, Edge Case "token con
 * firma alterada").
 */
@TestPropertySource(properties = {
        "app.security.admin-bootstrap-login=" + TestJwt.ADMIN_LOGIN,
        "app.security.admin-bootstrap-password=" + TestJwt.ADMIN_PASSWORD
})
class AuthIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void usarClienteJdk() {
        // HttpURLConnection (factory por defecto) reintenta automaticamente con
        // autenticacion ante cualquier 401/407 tras un POST con cuerpo en modo streaming, y
        // falla con HttpRetryException al no poder reenviar ese cuerpo - independientemente
        // de si el 401 trae un header WWW-Authenticate. JdkClientHttpRequestFactory
        // (java.net.http.HttpClient) no tiene esa limitacion.
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    private String personasUrl() {
        return "http://localhost:" + port + "/api/personas";
    }

    private String loginUrl() {
        return "http://localhost:" + port + "/login";
    }

    @Test
    void llamarEndpointExistenteSinTokenRegresa401() {
        ResponseEntity<Map> respuesta = restTemplate.exchange(
                personasUrl(), HttpMethod.GET, HttpEntity.EMPTY, Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(respuesta.getBody().get("codigo")).isEqualTo("NO_AUTENTICADO");
    }

    @Test
    void loginValidoYUsoDelTokenFuncionanDePuntaAPunta() {
        String accessToken = TestJwt.loginAdmin(restTemplate, port);
        assertThat(accessToken).isNotBlank();

        ResponseEntity<Map> respuesta = restTemplate.exchange(
                personasUrl(), HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(accessToken)), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void tokenConFirmaAlteradaRegresa401() {
        String accessToken = TestJwt.loginAdmin(restTemplate, port);
        String tokenAlterado = accessToken.substring(0, accessToken.length() - 2) + "xx";

        ResponseEntity<Map> respuesta = restTemplate.exchange(
                personasUrl(), HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(tokenAlterado)), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void loginConCredencialesIncorrectasRegresa401Generico() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of("login", TestJwt.ADMIN_LOGIN, "password", "clave-incorrecta");

        ResponseEntity<Map> respuesta = restTemplate.exchange(
                loginUrl(), HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(respuesta.getBody().get("codigo")).isEqualTo("NO_AUTENTICADO");
    }

    @Test
    void encabezadoXApiKeyHeredadoSinJwtValidoRegresa401IgualQueSinAutenticacion() {
        // La clave de API ya no es un mecanismo de autenticacion valido (FR-006a): el
        // filtro JWT ni siquiera lee ese encabezado, asi que una llamada que solo lo
        // presente se trata igual que una sin ningun encabezado de autenticacion.
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", "cualquier-valor-de-la-clave-heredada");

        ResponseEntity<Map> respuesta = restTemplate.exchange(
                personasUrl(), HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(respuesta.getBody().get("codigo")).isEqualTo("NO_AUTENTICADO");
    }
}
