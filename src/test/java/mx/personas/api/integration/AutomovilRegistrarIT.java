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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US1: registrar un automóvil a una persona (FR-001 a FR-006).
 */
@TestPropertySource(properties = {
        "app.security.admin-bootstrap-login=" + TestJwt.ADMIN_LOGIN,
        "app.security.admin-bootstrap-password=" + TestJwt.ADMIN_PASSWORD
})
class AutomovilRegistrarIT extends AbstractIntegrationTest {

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
        direccion.put("calle", "Calle Auto");
        direccion.put("numero", "1");
        direccion.put("colonia", "Centro");
        direccion.put("municipio", "Municipio Test");
        direccion.put("estado", "Estado Test");
        direccion.put("codigoPostal", "00000");
        direccion.put("pais", "US");

        Map<String, Object> persona = new LinkedHashMap<>();
        persona.put("nombres", "Auto");
        persona.put("apellidos", "Test " + h);
        persona.put("fechaNacimiento", LocalDate.of(1990, 1, 1).toString());
        persona.put("sexo", "F");
        persona.put("curp", "AUTO900101MDFRZN" + h);
        persona.put("rfc", "AUTO900101AB" + h.charAt(0));
        persona.put("correo", "automovil." + System.nanoTime() + "@example.com");
        persona.put("telefono", "5500011166");
        persona.put("direccion", direccion);

        ResponseEntity<Map> creado = restTemplate.exchange(url("/api/personas"), HttpMethod.POST,
                new HttpEntity<>(persona, TestJwt.bearerHeaders(adminToken)), Map.class);
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) creado.getBody().get("id");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> historialDe(String personaId) {
        ResponseEntity<Map> respuesta = restTemplate.exchange(
                url("/api/personas/" + personaId + "/historial"), HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Map.class);
        return (List<Map<String, Object>>) respuesta.getBody().get("contenido");
    }

    private Map<String, Object> automovilBody(String placas, String vin) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("marca", "Nissan");
        body.put("modelo", "Versa");
        body.put("anio", 2022);
        body.put("color", "Rojo");
        body.put("placas", placas);
        if (vin != null) {
            body.put("vin", vin);
        }
        return body;
    }

    @Test
    void registrarUnAutomovilConVinYSinVin() {
        String personaId = crearPersona();
        // VIN unico por ejecucion (no un literal compartido con otras clases de test:
        // el VIN tiene unicidad GLOBAL en la BD compartida entre clases IT).
        String vin = "1HGCM8263" + TestUniqueId.homoclave() + "004352";

        ResponseEntity<Map> conVin = restTemplate.postForEntity(url("/api/personas/" + personaId + "/automoviles"),
                new HttpEntity<>(automovilBody("REG-" + TestUniqueId.homoclave(), vin),
                        TestJwt.bearerHeaders(capturistaToken)), Map.class);
        assertThat(conVin.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(conVin.getBody().get("activo")).isEqualTo(true);
        assertThat(conVin.getBody().get("vin")).isEqualTo(vin);

        // FR-028, hallazgo F1 de /speckit-converge: el alta de un automóvil debe
        // quedar en el historial de auditoría de la persona dueña.
        assertThat(historialDe(personaId)).anySatisfy(entrada ->
                assertThat(entrada.get("operacion")).isEqualTo("MODIFICACION"));

        ResponseEntity<Map> sinVin = restTemplate.postForEntity(url("/api/personas/" + personaId + "/automoviles"),
                new HttpEntity<>(automovilBody("REG-" + TestUniqueId.homoclave(), null),
                        TestJwt.bearerHeaders(capturistaToken)), Map.class);
        assertThat(sinVin.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(sinVin.getBody().get("vin")).isNull();
    }

    @Test
    void placasDuplicadasDeUnAutomovilActivoResponde409YReutilizablesTrasBaja() {
        String personaId = crearPersona();
        String placas = "DUP-" + TestUniqueId.homoclave();

        ResponseEntity<Map> primero = restTemplate.postForEntity(url("/api/personas/" + personaId + "/automoviles"),
                new HttpEntity<>(automovilBody(placas, null), TestJwt.bearerHeaders(capturistaToken)), Map.class);
        assertThat(primero.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> duplicado = restTemplate.postForEntity(
                url("/api/personas/" + personaId + "/automoviles"),
                new HttpEntity<>(automovilBody(placas, null), TestJwt.bearerHeaders(capturistaToken)), Map.class);
        assertThat(duplicado.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicado.getBody().get("codigo")).isEqualTo("AUTOMOVIL_PLACAS_DUPLICADAS");

        String automovilId = (String) primero.getBody().get("id");
        // DELETE /api/automoviles/{id} es de US7 (aun no implementado); se desactiva
        // directo por repositorio para no depender de esa historia todavia no construida.
        var automovil = automovilRepository.findById(java.util.UUID.fromString(automovilId)).orElseThrow();
        automovil.desactivar();
        automovilRepository.saveAndFlush(automovil);

        ResponseEntity<Map> reutilizado = restTemplate.postForEntity(
                url("/api/personas/" + personaId + "/automoviles"),
                new HttpEntity<>(automovilBody(placas, null), TestJwt.bearerHeaders(capturistaToken)), Map.class);
        assertThat(reutilizado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void vinDuplicadoContraUnoActivoOEliminadoResponde409() {
        String personaId = crearPersona();
        String vin = "1HGCM82633A00" + TestUniqueId.homoclave() + "0";

        ResponseEntity<Map> primero = restTemplate.postForEntity(url("/api/personas/" + personaId + "/automoviles"),
                new HttpEntity<>(automovilBody("VIN1-" + TestUniqueId.homoclave(), vin),
                        TestJwt.bearerHeaders(capturistaToken)), Map.class);
        assertThat(primero.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String automovilId = (String) primero.getBody().get("id");

        var automovil = automovilRepository.findById(java.util.UUID.fromString(automovilId)).orElseThrow();
        automovil.desactivar();
        automovilRepository.saveAndFlush(automovil);

        ResponseEntity<Map> duplicado = restTemplate.postForEntity(
                url("/api/personas/" + personaId + "/automoviles"),
                new HttpEntity<>(automovilBody("VIN2-" + TestUniqueId.homoclave(), vin),
                        TestJwt.bearerHeaders(capturistaToken)), Map.class);
        assertThat(duplicado.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicado.getBody().get("codigo")).isEqualTo("AUTOMOVIL_VIN_DUPLICADO");
    }

    @Test
    void registrarUnAutomovilParaUnaPersonaEliminadaLogicamenteResponde409() {
        String personaId = crearPersona();
        restTemplate.exchange(url("/api/personas/" + personaId), HttpMethod.DELETE,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Void.class);

        ResponseEntity<Map> respuesta = restTemplate.postForEntity(
                url("/api/personas/" + personaId + "/automoviles"),
                new HttpEntity<>(automovilBody("ELI-" + TestUniqueId.homoclave(), null),
                        TestJwt.bearerHeaders(capturistaToken)), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody().get("codigo")).isEqualTo("PERSONA_ELIMINADA");
    }

    @Test
    void anioFueraDeRangoResponde400() {
        String personaId = crearPersona();

        Map<String, Object> anioViejo = automovilBody("ANT-" + TestUniqueId.homoclave(), null);
        anioViejo.put("anio", 1899);
        ResponseEntity<Map> respuestaViejo = restTemplate.postForEntity(
                url("/api/personas/" + personaId + "/automoviles"),
                new HttpEntity<>(anioViejo, TestJwt.bearerHeaders(capturistaToken)), Map.class);
        assertThat(respuestaViejo.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> anioFuturo = automovilBody("FUT-" + TestUniqueId.homoclave(), null);
        anioFuturo.put("anio", LocalDate.now().getYear() + 2);
        ResponseEntity<Map> respuestaFuturo = restTemplate.postForEntity(
                url("/api/personas/" + personaId + "/automoviles"),
                new HttpEntity<>(anioFuturo, TestJwt.bearerHeaders(capturistaToken)), Map.class);
        assertThat(respuestaFuturo.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
