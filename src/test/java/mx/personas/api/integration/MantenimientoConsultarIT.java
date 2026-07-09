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
 * US4: consultar el historial y el detalle de mantenimientos (FR-022, FR-023, FR-021/SC-005).
 */
@TestPropertySource(properties = {
        "app.security.admin-bootstrap-login=" + TestJwt.ADMIN_LOGIN,
        "app.security.admin-bootstrap-password=" + TestJwt.ADMIN_PASSWORD
})
class MantenimientoConsultarIT extends AbstractIntegrationTest {

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
        direccion.put("calle", "Calle Consulta Mtto");
        direccion.put("numero", "1");
        direccion.put("colonia", "Centro");
        direccion.put("municipio", "Municipio Test");
        direccion.put("estado", "Estado Test");
        direccion.put("codigoPostal", "00000");
        direccion.put("pais", "US");

        Map<String, Object> persona = new LinkedHashMap<>();
        persona.put("nombres", "ConsultaMtto");
        persona.put("apellidos", "Test " + h);
        persona.put("fechaNacimiento", LocalDate.of(1990, 1, 1).toString());
        persona.put("sexo", "F");
        persona.put("curp", "CMTT900101MDFRZN" + h);
        persona.put("rfc", "CMTT900101AB" + h.charAt(0));
        persona.put("correo", "consultamtto." + System.nanoTime() + "@example.com");
        persona.put("telefono", "5500011199");
        persona.put("direccion", direccion);

        ResponseEntity<Map> creado = restTemplate.exchange(url("/api/personas"), HttpMethod.POST,
                new HttpEntity<>(persona, TestJwt.bearerHeaders(adminToken)), Map.class);
        return (String) creado.getBody().get("id");
    }

    private String crearAutomovil(String personaId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("marca", "Nissan");
        body.put("modelo", "Versa");
        body.put("anio", 2022);
        body.put("color", "Rojo");
        body.put("placas", "CMT-" + TestUniqueId.homoclave());

        ResponseEntity<Map> creado = restTemplate.postForEntity(url("/api/personas/" + personaId + "/automoviles"),
                new HttpEntity<>(body, TestJwt.bearerHeaders(capturistaToken)), Map.class);
        return (String) creado.getBody().get("id");
    }

    @SuppressWarnings("unchecked")
    private String crearMecanicoElegible() {
        String personaId = crearPersona();
        ResponseEntity<Map> catalogo = restTemplate.exchange(
                url("/api/profesiones?incluirInactivas=true&size=100"), HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Map.class);
        List<Map<String, Object>> contenido = (List<Map<String, Object>>) catalogo.getBody().get("contenido");
        Long mecanicoProfesionId = contenido.stream()
                .filter(p -> "Mecánico".equals(p.get("nombre")))
                .findFirst()
                .map(p -> ((Number) p.get("id")).longValue())
                .orElseThrow();

        String asignacionId = (String) restTemplate.postForEntity(
                url("/api/personas/" + personaId + "/profesiones"),
                new HttpEntity<>(Map.of("profesionId", mecanicoProfesionId), TestJwt.bearerHeaders(adminToken)),
                Map.class).getBody().get("id");
        ultimaAsignacionMecanico = asignacionId;
        ultimoMecanicoId = personaId;
        return personaId;
    }

    private String ultimaAsignacionMecanico;
    private String ultimoMecanicoId;

    private Map<String, Object> mantenimientoBody(String fecha, int kilometraje, String mecanicoId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("descripcion", "Servicio");
        body.put("fecha", fecha);
        body.put("kilometraje", kilometraje);
        body.put("costoTotal", "0");
        if (mecanicoId != null) {
            body.put("mecanicoId", mecanicoId);
        }
        return body;
    }

    private Map<String, Object> registrarMantenimiento(String automovilId, String fecha, int km, String mecanicoId) {
        return restTemplate.postForEntity(url("/api/automoviles/" + automovilId + "/mantenimientos"),
                new HttpEntity<>(mantenimientoBody(fecha, km, mecanicoId), TestJwt.bearerHeaders(capturistaToken)),
                Map.class).getBody();
    }

    @Test
    void elHistorialSeOrdenaDeLaFechaMasRecienteALaMasAntigua() {
        String personaId = crearPersona();
        String automovilId = crearAutomovil(personaId);
        registrarMantenimiento(automovilId, LocalDate.now().minusDays(20).toString(), 1000, null);
        registrarMantenimiento(automovilId, LocalDate.now().minusDays(10).toString(), 2000, null);
        Map<String, Object> masReciente = registrarMantenimiento(automovilId, LocalDate.now().toString(), 3000, null);

        ResponseEntity<Map> respuesta = restTemplate.exchange(
                url("/api/automoviles/" + automovilId + "/mantenimientos"), HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> contenido = (List<Map<String, Object>>) respuesta.getBody().get("contenido");
        assertThat(contenido).hasSize(3);
        assertThat(contenido.get(0).get("id")).isEqualTo(masReciente.get("id"));
    }

    @Test
    void elDetalleIncluyePiezasYMecanicoSinDatosPersonalesAdicionales() {
        String personaId = crearPersona();
        String automovilId = crearAutomovil(personaId);
        String mecanicoId = crearMecanicoElegible();

        Map<String, Object> body = mantenimientoBody(LocalDate.now().toString(), 1000, mecanicoId);
        body.put("piezas", List.of(Map.of("nombre", "Filtro", "costo", "50.00")));
        Map<String, Object> creado = restTemplate.postForEntity(
                url("/api/automoviles/" + automovilId + "/mantenimientos"),
                new HttpEntity<>(body, TestJwt.bearerHeaders(capturistaToken)), Map.class).getBody();

        ResponseEntity<Map> respuesta = restTemplate.exchange(
                url("/api/mantenimientos/" + creado.get("id")), HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) respuesta.getBody().get("piezas")).hasSize(1);
        Map<String, Object> mecanico = (Map<String, Object>) respuesta.getBody().get("mecanico");
        assertThat(mecanico).containsOnlyKeys("id", "nombreCompleto");
    }

    @Test
    void retirarLaProfesionAlMecanicoNoAlteraElMantenimientoYaRegistrado() {
        String personaId = crearPersona();
        String automovilId = crearAutomovil(personaId);
        String mecanicoId = crearMecanicoElegible();
        Map<String, Object> mantenimiento = registrarMantenimiento(automovilId, LocalDate.now().toString(), 1000,
                mecanicoId);

        restTemplate.exchange(
                url("/api/personas/" + mecanicoId + "/profesiones/" + ultimaAsignacionMecanico + "/retirar"),
                HttpMethod.PATCH, new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Map.class);

        ResponseEntity<Map> respuesta = restTemplate.exchange(
                url("/api/mantenimientos/" + mantenimiento.get("id")), HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> mecanico = (Map<String, Object>) respuesta.getBody().get("mecanico");
        assertThat(mecanico.get("id")).isEqualTo(mecanicoId);
    }

    @Test
    void obtenerMantenimientoCuyoAutomovilFueEliminadoResponde404() {
        // hallazgo F1 CRITICAL de /speckit-analyze
        String personaId = crearPersona();
        String automovilId = crearAutomovil(personaId);
        Map<String, Object> mantenimiento = registrarMantenimiento(automovilId, LocalDate.now().toString(), 1000,
                null);

        var automovil = automovilRepository.findById(java.util.UUID.fromString(automovilId)).orElseThrow();
        automovil.desactivar();
        automovilRepository.saveAndFlush(automovil);

        ResponseEntity<Map> respuesta = restTemplate.exchange(
                url("/api/mantenimientos/" + mantenimiento.get("id")), HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void automovilSinMantenimientosRegresaPaginaVacia() {
        String personaId = crearPersona();
        String automovilId = crearAutomovil(personaId);

        ResponseEntity<Map> respuesta = restTemplate.exchange(
                url("/api/automoviles/" + automovilId + "/mantenimientos"), HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), Map.class);

        assertThat((List<?>) respuesta.getBody().get("contenido")).isEmpty();
    }
}
