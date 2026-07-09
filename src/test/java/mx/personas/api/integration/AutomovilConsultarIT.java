package mx.personas.api.integration;

import mx.personas.api.automovil.repository.AutomovilRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US2: consultar los automóviles de una persona y el detalle de uno (FR-007).
 */
@TestPropertySource(properties = {
        "app.security.admin-bootstrap-login=" + TestJwt.ADMIN_LOGIN,
        "app.security.admin-bootstrap-password=" + TestJwt.ADMIN_PASSWORD
})
class AutomovilConsultarIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AutomovilRepository automovilRepository;

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
        direccion.put("calle", "Calle Consultar Auto");
        direccion.put("numero", "1");
        direccion.put("colonia", "Centro");
        direccion.put("municipio", "Municipio Test");
        direccion.put("estado", "Estado Test");
        direccion.put("codigoPostal", "00000");
        direccion.put("pais", "US");

        Map<String, Object> persona = new LinkedHashMap<>();
        persona.put("nombres", "ConsultarAuto");
        persona.put("apellidos", "Test " + h);
        persona.put("fechaNacimiento", LocalDate.of(1990, 1, 1).toString());
        persona.put("sexo", "F");
        persona.put("curp", "CAUT900101MDFRZN" + h);
        persona.put("rfc", "CAUT900101AB" + h.charAt(0));
        persona.put("correo", "consultarauto." + System.nanoTime() + "@example.com");
        persona.put("telefono", "5500011177");
        persona.put("direccion", direccion);

        ResponseEntity<Map> creado = restTemplate.exchange(url("/api/personas"), HttpMethod.POST,
                new HttpEntity<>(persona, TestJwt.bearerHeaders(adminToken)), Map.class);
        return (String) creado.getBody().get("id");
    }

    private Map<String, Object> crearAutomovil(String personaId, String placas) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("marca", "Nissan");
        body.put("modelo", "Versa");
        body.put("anio", 2022);
        body.put("color", "Rojo");
        body.put("placas", placas);

        ResponseEntity<Map> creado = restTemplate.postForEntity(url("/api/personas/" + personaId + "/automoviles"),
                new HttpEntity<>(body, TestJwt.bearerHeaders(capturistaToken)), Map.class);
        return creado.getBody();
    }

    @SuppressWarnings("unchecked")
    @Test
    void listarLosAutomovilesDeUnaPersonaConDosActivos() {
        String personaId = crearPersona();
        crearAutomovil(personaId, "CON1-" + TestUniqueId.homoclave());
        crearAutomovil(personaId, "CON2-" + TestUniqueId.homoclave());

        ResponseEntity<List> respuesta = restTemplate.exchange(
                url("/api/personas/" + personaId + "/automoviles"), HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), List.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).hasSize(2);
    }

    @Test
    void detalleDeUnAutomovilRegresaTodosLosCampos() {
        String personaId = crearPersona();
        Map<String, Object> creado = crearAutomovil(personaId, "DET-" + TestUniqueId.homoclave());
        String automovilId = (String) creado.get("id");

        ResponseEntity<Map> respuesta = restTemplate.exchange(url("/api/automoviles/" + automovilId),
                HttpMethod.GET, new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).containsKeys("marca", "modelo", "anio", "color", "placas", "vin", "activo");
    }

    @Test
    void personaSinAutomovilesRegresaListaVacia() {
        String personaId = crearPersona();

        ResponseEntity<List> respuesta = restTemplate.exchange(
                url("/api/personas/" + personaId + "/automoviles"), HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), List.class);

        assertThat(respuesta.getBody()).isEmpty();
    }

    @Test
    void automovilInexistenteResponde404() {
        ResponseEntity<Map> respuesta = restTemplate.exchange(url("/api/automoviles/" + UUID.randomUUID()),
                HttpMethod.GET, new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @SuppressWarnings("unchecked")
    @Test
    void personaConUnAutomovilActivoYUnoDadoDeBajaSoloRegresaElActivo() {
        // hallazgo F4 de /speckit-analyze
        String personaId = crearPersona();
        crearAutomovil(personaId, "ACT-" + TestUniqueId.homoclave());
        Map<String, Object> deBaja = crearAutomovil(personaId, "BAJA-" + TestUniqueId.homoclave());
        var automovil = automovilRepository.findById(UUID.fromString((String) deBaja.get("id"))).orElseThrow();
        automovil.desactivar();
        automovilRepository.saveAndFlush(automovil);

        ResponseEntity<List> respuesta = restTemplate.exchange(
                url("/api/personas/" + personaId + "/automoviles"), HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), List.class);

        assertThat(respuesta.getBody()).hasSize(1);
    }
}
