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
 * US7: eliminar y restaurar un automóvil (FR-009 a FR-011).
 */
@TestPropertySource(properties = {
        "app.security.admin-bootstrap-login=" + TestJwt.ADMIN_LOGIN,
        "app.security.admin-bootstrap-password=" + TestJwt.ADMIN_PASSWORD
})
class AutomovilEliminarRestaurarIT extends AbstractIntegrationTest {

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
        direccion.put("calle", "Calle Eliminar Auto");
        direccion.put("numero", "1");
        direccion.put("colonia", "Centro");
        direccion.put("municipio", "Municipio Test");
        direccion.put("estado", "Estado Test");
        direccion.put("codigoPostal", "00000");
        direccion.put("pais", "US");

        Map<String, Object> persona = new LinkedHashMap<>();
        persona.put("nombres", "EliminarAuto");
        persona.put("apellidos", "Test " + h);
        persona.put("fechaNacimiento", LocalDate.of(1990, 1, 1).toString());
        persona.put("sexo", "F");
        persona.put("curp", "ELAT900101MDFRZN" + h);
        persona.put("rfc", "ELAT900101AB" + h.charAt(0));
        persona.put("correo", "eliminarauto." + System.nanoTime() + "@example.com");
        persona.put("telefono", "5500011222");
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
        body.put("placas", "ELI-" + TestUniqueId.homoclave());

        return (String) restTemplate.postForEntity(url("/api/personas/" + personaId + "/automoviles"),
                new HttpEntity<>(body, TestJwt.bearerHeaders(capturistaToken)), Map.class).getBody().get("id");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> historialAuditoriaDe(String personaId) {
        ResponseEntity<Map> respuesta = restTemplate.exchange(
                url("/api/personas/" + personaId + "/historial"), HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Map.class);
        return (List<Map<String, Object>>) respuesta.getBody().get("contenido");
    }

    private void registrarMantenimiento(String automovilId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("descripcion", "Servicio");
        body.put("fecha", LocalDate.now().toString());
        body.put("kilometraje", 1000);
        body.put("costoTotal", "0");
        restTemplate.postForEntity(url("/api/automoviles/" + automovilId + "/mantenimientos"),
                new HttpEntity<>(body, TestJwt.bearerHeaders(capturistaToken)), Map.class);
    }

    @Test
    void capturistaNoPuedeEliminarPeroAdminSiYOcultaElHistorial() {
        String personaId = crearPersona();
        String automovilId = crearAutomovil(personaId);
        registrarMantenimiento(automovilId);
        int historialAntes = historialAuditoriaDe(personaId).size();

        ResponseEntity<Void> capturistaIntento = restTemplate.exchange(url("/api/automoviles/" + automovilId),
                HttpMethod.DELETE, new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), Void.class);
        assertThat(capturistaIntento.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Void> adminElimina = restTemplate.exchange(url("/api/automoviles/" + automovilId),
                HttpMethod.DELETE, new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Void.class);
        assertThat(adminElimina.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> detalle = restTemplate.exchange(url("/api/automoviles/" + automovilId), HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), Map.class);
        assertThat(detalle.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Map> historial = restTemplate.exchange(
                url("/api/automoviles/" + automovilId + "/mantenimientos"), HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), Map.class);
        assertThat(historial.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // FR-028, hallazgo F1 de /speckit-converge (fortalecido tras hallazgo F1 de una
        // segunda corrida de /speckit-converge, T061): comparar tamaño antes/después.
        assertThat(historialAuditoriaDe(personaId)).hasSizeGreaterThan(historialAntes);
    }

    @Test
    void capturistaNoPuedeRestaurarPeroAdminSiYTodoReaparece() {
        String personaId = crearPersona();
        String automovilId = crearAutomovil(personaId);
        registrarMantenimiento(automovilId);
        restTemplate.exchange(url("/api/automoviles/" + automovilId), HttpMethod.DELETE,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Void.class);
        int historialAntes = historialAuditoriaDe(personaId).size();

        ResponseEntity<Map> capturistaIntento = restTemplate.exchange(
                url("/api/automoviles/" + automovilId + "/restaurar"), HttpMethod.POST,
                new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), Map.class);
        assertThat(capturistaIntento.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> adminRestaura = restTemplate.exchange(
                url("/api/automoviles/" + automovilId + "/restaurar"), HttpMethod.POST,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Map.class);
        assertThat(adminRestaura.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(adminRestaura.getBody().get("activo")).isEqualTo(true);

        ResponseEntity<Map> detalle = restTemplate.exchange(url("/api/automoviles/" + automovilId), HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), Map.class);
        assertThat(detalle.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> historial = restTemplate.exchange(
                url("/api/automoviles/" + automovilId + "/mantenimientos"), HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), Map.class);
        assertThat(historial.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) historial.getBody().get("contenido")).hasSize(1);

        // FR-028, hallazgo F1 de /speckit-converge (fortalecido tras hallazgo F1 de una
        // segunda corrida de /speckit-converge, T061): comparar tamaño antes/después.
        assertThat(historialAuditoriaDe(personaId)).hasSizeGreaterThan(historialAntes);
    }

    @Test
    void restaurarUnoYaActivoResponde409() {
        String personaId = crearPersona();
        String automovilId = crearAutomovil(personaId);

        ResponseEntity<Map> respuesta = restTemplate.exchange(url("/api/automoviles/" + automovilId + "/restaurar"),
                HttpMethod.POST, new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody().get("codigo")).isEqualTo("AUTOMOVIL_YA_ACTIVO");
    }
}
