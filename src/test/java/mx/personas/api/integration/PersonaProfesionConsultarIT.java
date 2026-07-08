package mx.personas.api.integration;

import mx.personas.api.common.AbstractIntegrationTest;
import mx.personas.api.common.TestJwt;
import mx.personas.api.common.TestUniqueId;
import mx.personas.api.usuario.model.Rol;
import mx.personas.api.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US3: consultar las profesiones de una persona (FR-016/FR-017).
 */
@TestPropertySource(properties = {
        "app.security.admin-bootstrap-login=" + TestJwt.ADMIN_LOGIN,
        "app.security.admin-bootstrap-password=" + TestJwt.ADMIN_PASSWORD
})
class PersonaProfesionConsultarIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String capturistaToken;

    @BeforeEach
    void preparar() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        adminToken = TestJwt.loginAdmin(restTemplate, port);
        TestJwt.sembrarUsuario(usuarioRepository, passwordEncoder,
                TestJwt.CAPTURISTA_LOGIN, TestJwt.CAPTURISTA_PASSWORD, Rol.CAPTURISTA);
        capturistaToken = TestJwt.login(restTemplate, port, TestJwt.CAPTURISTA_LOGIN, TestJwt.CAPTURISTA_PASSWORD);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String crearPersona() {
        String h = TestUniqueId.homoclave();
        Map<String, Object> direccion = new LinkedHashMap<>();
        direccion.put("calle", "Calle Consultar");
        direccion.put("numero", "1");
        direccion.put("colonia", "Centro");
        direccion.put("municipio", "Municipio Test");
        direccion.put("estado", "Estado Test");
        direccion.put("codigoPostal", "00000");
        direccion.put("pais", "US");

        Map<String, Object> persona = new LinkedHashMap<>();
        persona.put("nombres", "Test");
        persona.put("apellidos", "Consultar " + h);
        persona.put("fechaNacimiento", LocalDate.of(1990, 1, 1).toString());
        persona.put("sexo", "F");
        persona.put("curp", "CNSL900101MDFRZN" + h);
        persona.put("rfc", "CNSL900101AB" + h.charAt(0));
        persona.put("correo", "consultar." + System.nanoTime() + "@example.com");
        persona.put("telefono", "5500011133");
        persona.put("direccion", direccion);

        ResponseEntity<Map> creado = restTemplate.exchange(url("/api/personas"), HttpMethod.POST,
                new HttpEntity<>(persona, TestJwt.bearerHeaders(adminToken)), Map.class);
        return (String) creado.getBody().get("id");
    }

    private Long crearProfesion(String nombre) {
        ResponseEntity<Map> creada = restTemplate.postForEntity(url("/api/profesiones"),
                new HttpEntity<>(Map.of("nombre", nombre), TestJwt.bearerHeaders(adminToken)), Map.class);
        return ((Number) creada.getBody().get("id")).longValue();
    }

    private void asignar(String personaId, Long profesionId) {
        restTemplate.postForEntity(url("/api/personas/" + personaId + "/profesiones"),
                new HttpEntity<>(Map.of("profesionId", profesionId), TestJwt.bearerHeaders(capturistaToken)),
                Map.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    void consultarLasProfesionesDeUnaPersonaConDosActivasRegresaAmbas() {
        String personaId = crearPersona();
        Long mecanico = crearProfesion("Mecánico Consulta " + TestUniqueId.homoclave());
        Long electricista = crearProfesion("Electricista Consulta " + TestUniqueId.homoclave());
        asignar(personaId, mecanico);
        asignar(personaId, electricista);

        ResponseEntity<List> respuesta = restTemplate.exchange(
                url("/api/personas/" + personaId + "/profesiones"), HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), List.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> cuerpo = respuesta.getBody();
        assertThat(cuerpo).hasSize(2);
        assertThat(cuerpo).allSatisfy(item -> assertThat(item.get("fechaDesde")).isNotNull());
    }

    @Test
    void consultarLasProfesionesDeUnaPersonaSinNingunaRegresaListaVacia() {
        String personaId = crearPersona();

        ResponseEntity<List> respuesta = restTemplate.exchange(
                url("/api/personas/" + personaId + "/profesiones"), HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), List.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isEmpty();
    }
}
