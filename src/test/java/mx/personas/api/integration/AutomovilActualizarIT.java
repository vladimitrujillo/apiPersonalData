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
 * US5: actualizar los datos de un automóvil (FR-008).
 */
@TestPropertySource(properties = {
        "app.security.admin-bootstrap-login=" + TestJwt.ADMIN_LOGIN,
        "app.security.admin-bootstrap-password=" + TestJwt.ADMIN_PASSWORD
})
class AutomovilActualizarIT extends AbstractIntegrationTest {

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
        direccion.put("calle", "Calle Editar Auto");
        direccion.put("numero", "1");
        direccion.put("colonia", "Centro");
        direccion.put("municipio", "Municipio Test");
        direccion.put("estado", "Estado Test");
        direccion.put("codigoPostal", "00000");
        direccion.put("pais", "US");

        Map<String, Object> persona = new LinkedHashMap<>();
        persona.put("nombres", "EditarAuto");
        persona.put("apellidos", "Test " + h);
        persona.put("fechaNacimiento", LocalDate.of(1990, 1, 1).toString());
        persona.put("sexo", "F");
        persona.put("curp", "EDAT900101MDFRZN" + h);
        persona.put("rfc", "EDAT900101AB" + h.charAt(0));
        persona.put("correo", "editarauto." + System.nanoTime() + "@example.com");
        persona.put("telefono", "5500011200");
        persona.put("direccion", direccion);

        ResponseEntity<Map> creado = restTemplate.exchange(url("/api/personas"), HttpMethod.POST,
                new HttpEntity<>(persona, TestJwt.bearerHeaders(adminToken)), Map.class);
        return (String) creado.getBody().get("id");
    }

    private Map<String, Object> crearAutomovil(String personaId, String placas, String vin) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("marca", "Nissan");
        body.put("modelo", "Versa");
        body.put("anio", 2022);
        body.put("color", "Rojo");
        body.put("placas", placas);
        if (vin != null) {
            body.put("vin", vin);
        }
        return restTemplate.postForEntity(url("/api/personas/" + personaId + "/automoviles"),
                new HttpEntity<>(body, TestJwt.bearerHeaders(capturistaToken)), Map.class).getBody();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> historialDe(String personaId) {
        ResponseEntity<Map> respuesta = restTemplate.exchange(
                url("/api/personas/" + personaId + "/historial"), HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Map.class);
        return (List<Map<String, Object>>) respuesta.getBody().get("contenido");
    }

    @Test
    void editarMarcaModeloAnioColorPlacas() {
        String personaId = crearPersona();
        Map<String, Object> creado = crearAutomovil(personaId, "EDT-" + TestUniqueId.homoclave(), null);
        String automovilId = (String) creado.get("id");
        int historialAntes = historialDe(personaId).size();

        Map<String, Object> update = new LinkedHashMap<>();
        update.put("marca", "Toyota");
        update.put("modelo", "Corolla");
        update.put("anio", 2023);
        update.put("color", "Azul");
        update.put("placas", "NEW-" + TestUniqueId.homoclave());

        ResponseEntity<Map> respuesta = restTemplate.exchange(url("/api/automoviles/" + automovilId),
                HttpMethod.PATCH, new HttpEntity<>(update, TestJwt.bearerHeaders(capturistaToken)), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody().get("marca")).isEqualTo("Toyota");
        assertThat(respuesta.getBody().get("modelo")).isEqualTo("Corolla");
        assertThat(respuesta.getBody().get("color")).isEqualTo("Azul");

        // FR-028, hallazgo F1 de /speckit-converge (fortalecido tras hallazgo F1 de una
        // segunda corrida de /speckit-converge, T061): comparar tamaño antes/después,
        // no solo que exista una entrada MODIFICACION (el alta previa ya deja una).
        assertThat(historialDe(personaId)).hasSizeGreaterThan(historialAntes);
    }

    @Test
    void elVinNoSePuedeEditar() {
        String personaId = crearPersona();
        // VIN unico por ejecucion (no un literal compartido con otras clases de test:
        // el VIN tiene unicidad GLOBAL en la BD compartida entre clases IT).
        String vin = "1HGCM8263" + TestUniqueId.homoclave() + "004353";
        Map<String, Object> creado = crearAutomovil(personaId, "VNE-" + TestUniqueId.homoclave(), vin);
        String automovilId = (String) creado.get("id");

        restTemplate.exchange(url("/api/automoviles/" + automovilId), HttpMethod.PATCH,
                new HttpEntity<>(Map.of("marca", "Toyota"), TestJwt.bearerHeaders(capturistaToken)), Map.class);

        ResponseEntity<Map> detalle = restTemplate.exchange(url("/api/automoviles/" + automovilId), HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), Map.class);
        assertThat(detalle.getBody().get("vin")).isEqualTo(vin);
    }

    @Test
    void editarPlacasAUnasYaActivasEnOtroAutomovilResponde409() {
        String personaId = crearPersona();
        String placasOcupadas = "OCU-" + TestUniqueId.homoclave();
        crearAutomovil(personaId, placasOcupadas, null);
        Map<String, Object> segundo = crearAutomovil(personaId, "SEG-" + TestUniqueId.homoclave(), null);

        ResponseEntity<Map> respuesta = restTemplate.exchange(url("/api/automoviles/" + segundo.get("id")),
                HttpMethod.PATCH, new HttpEntity<>(Map.of("placas", placasOcupadas),
                        TestJwt.bearerHeaders(capturistaToken)), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody().get("codigo")).isEqualTo("AUTOMOVIL_PLACAS_DUPLICADAS");
    }

    @Test
    void anioFueraDeRangoResponde400() {
        String personaId = crearPersona();
        Map<String, Object> creado = crearAutomovil(personaId, "ANR-" + TestUniqueId.homoclave(), null);

        ResponseEntity<Map> respuesta = restTemplate.exchange(url("/api/automoviles/" + creado.get("id")),
                HttpMethod.PATCH, new HttpEntity<>(Map.of("anio", 1899), TestJwt.bearerHeaders(capturistaToken)),
                Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void editarUnAutomovilDadoDeBajaResponde409() {
        // FR-008, hallazgo F2 de /speckit-analyze
        String personaId = crearPersona();
        Map<String, Object> creado = crearAutomovil(personaId, "BJE-" + TestUniqueId.homoclave(), null);
        var automovil = automovilRepository.findById(java.util.UUID.fromString((String) creado.get("id")))
                .orElseThrow();
        automovil.desactivar();
        automovilRepository.saveAndFlush(automovil);

        ResponseEntity<Map> respuesta = restTemplate.exchange(url("/api/automoviles/" + creado.get("id")),
                HttpMethod.PATCH, new HttpEntity<>(Map.of("marca", "Toyota"),
                        TestJwt.bearerHeaders(capturistaToken)), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody().get("codigo")).isEqualTo("AUTOMOVIL_ELIMINADO");
    }
}
