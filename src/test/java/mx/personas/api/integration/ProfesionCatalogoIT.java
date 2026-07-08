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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US1: administrar el catálogo de profesiones (FR-001 a FR-010).
 */
@TestPropertySource(properties = {
        "app.security.admin-bootstrap-login=" + TestJwt.ADMIN_LOGIN,
        "app.security.admin-bootstrap-password=" + TestJwt.ADMIN_PASSWORD
})
class ProfesionCatalogoIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;

    @BeforeEach
    void preparar() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        adminToken = TestJwt.loginAdmin(restTemplate, port);
        TestJwt.sembrarUsuario(usuarioRepository, passwordEncoder,
                TestJwt.CAPTURISTA_LOGIN, TestJwt.CAPTURISTA_PASSWORD, Rol.CAPTURISTA);
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void adminCreaProfesionYRechazaDuplicadosInsensiblesAMayusculasYAcentos() {
        ResponseEntity<Map> creada = restTemplate.postForEntity(url("/api/profesiones"),
                new HttpEntity<>(Map.of("nombre", "Electricista"), headers(adminToken)), Map.class);
        assertThat(creada.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(creada.getBody().get("activo")).isEqualTo(true);

        ResponseEntity<Map> duplicadaSemilla = restTemplate.postForEntity(url("/api/profesiones"),
                new HttpEntity<>(Map.of("nombre", "mecanico"), headers(adminToken)), Map.class);
        assertThat(duplicadaSemilla.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicadaSemilla.getBody().get("codigo")).isEqualTo("PROFESION_NOMBRE_DUPLICADO");
    }

    @Test
    void desactivarYCrearDeNuevoIndicaQuePuedeReactivarseYReactivarLaVuelveADisponible() {
        ResponseEntity<Map> creada = restTemplate.postForEntity(url("/api/profesiones"),
                new HttpEntity<>(Map.of("nombre", "Carpintero"), headers(adminToken)), Map.class);
        Integer id = (Integer) creada.getBody().get("id");

        restTemplate.exchange(url("/api/profesiones/" + id + "/desactivar"),
                org.springframework.http.HttpMethod.PATCH, new HttpEntity<>(headers(adminToken)), Map.class);

        ResponseEntity<Map> reintento = restTemplate.postForEntity(url("/api/profesiones"),
                new HttpEntity<>(Map.of("nombre", "Carpintero"), headers(adminToken)), Map.class);
        assertThat(reintento.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reintento.getBody().get("codigo")).isEqualTo("PROFESION_NOMBRE_DESACTIVADA");

        ResponseEntity<Map> reactivada = restTemplate.exchange(url("/api/profesiones/" + id + "/reactivar"),
                org.springframework.http.HttpMethod.PATCH, new HttpEntity<>(headers(adminToken)), Map.class);
        assertThat(reactivada.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reactivada.getBody().get("activo")).isEqualTo(true);
    }

    @Test
    void capturistaRegresa403AlCrearEditarDesactivarReactivar() {
        String capToken = TestJwt.login(restTemplate, port, TestJwt.CAPTURISTA_LOGIN, TestJwt.CAPTURISTA_PASSWORD);

        ResponseEntity<Map> respuesta = restTemplate.postForEntity(url("/api/profesiones"),
                new HttpEntity<>(Map.of("nombre", "Yesero"), headers(capToken)), Map.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
