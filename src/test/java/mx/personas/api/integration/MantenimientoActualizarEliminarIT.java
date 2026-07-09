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
 * US6: actualizar, eliminar y restaurar un mantenimiento (FR-024, FR-025, FR-025a).
 */
@TestPropertySource(properties = {
        "app.security.admin-bootstrap-login=" + TestJwt.ADMIN_LOGIN,
        "app.security.admin-bootstrap-password=" + TestJwt.ADMIN_PASSWORD
})
class MantenimientoActualizarEliminarIT extends AbstractIntegrationTest {

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
        direccion.put("calle", "Calle Editar Mtto");
        direccion.put("numero", "1");
        direccion.put("colonia", "Centro");
        direccion.put("municipio", "Municipio Test");
        direccion.put("estado", "Estado Test");
        direccion.put("codigoPostal", "00000");
        direccion.put("pais", "US");

        Map<String, Object> persona = new LinkedHashMap<>();
        persona.put("nombres", "EditarMtto");
        persona.put("apellidos", "Test " + h);
        persona.put("fechaNacimiento", LocalDate.of(1990, 1, 1).toString());
        persona.put("sexo", "F");
        persona.put("curp", "EDMT900101MDFRZN" + h);
        persona.put("rfc", "EDMT900101AB" + h.charAt(0));
        persona.put("correo", "editarmtto." + System.nanoTime() + "@example.com");
        persona.put("telefono", "5500011211");
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
        body.put("placas", "EDM-" + TestUniqueId.homoclave());

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

    private Map<String, Object> registrarMantenimiento(String automovilId, int km) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("descripcion", "Servicio original");
        body.put("fecha", LocalDate.now().toString());
        body.put("kilometraje", km);
        body.put("costoTotal", "100.00");
        return restTemplate.postForEntity(url("/api/automoviles/" + automovilId + "/mantenimientos"),
                new HttpEntity<>(body, TestJwt.bearerHeaders(capturistaToken)), Map.class).getBody();
    }

    @Test
    void editarDescripcionCostoYPiezasConLasMismasValidaciones() {
        String personaId = crearPersona();
        String automovilId = crearAutomovil(personaId);
        Map<String, Object> creado = registrarMantenimiento(automovilId, 1000);
        int historialAntes = historialAuditoriaDe(personaId).size();

        Map<String, Object> update = new LinkedHashMap<>();
        update.put("descripcion", "Servicio corregido");
        update.put("costoTotal", "250.00");
        update.put("piezas", List.of(Map.of("nombre", "Bujías", "costo", "80.00")));

        ResponseEntity<Map> respuesta = restTemplate.exchange(url("/api/mantenimientos/" + creado.get("id")),
                HttpMethod.PATCH, new HttpEntity<>(update, TestJwt.bearerHeaders(capturistaToken)), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody().get("descripcion")).isEqualTo("Servicio corregido");
        assertThat((List<?>) respuesta.getBody().get("piezas")).hasSize(1);

        // FR-028, hallazgo F1 de /speckit-converge (fortalecido tras hallazgo F1 de una
        // segunda corrida de /speckit-converge, T061): comparar tamaño antes/después.
        assertThat(historialAuditoriaDe(personaId)).hasSizeGreaterThan(historialAntes);
    }

    @Test
    void capturistaNoPuedeEliminarPeroAdminSiYReaparaceAlRestaurar() {
        String personaId = crearPersona();
        String automovilId = crearAutomovil(personaId);
        Map<String, Object> creado = registrarMantenimiento(automovilId, 1000);
        String mantenimientoId = (String) creado.get("id");
        int historialAntesBaja = historialAuditoriaDe(personaId).size();

        ResponseEntity<Void> capturistaIntento = restTemplate.exchange(
                url("/api/mantenimientos/" + mantenimientoId), HttpMethod.DELETE,
                new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), Void.class);
        assertThat(capturistaIntento.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Void> adminElimina = restTemplate.exchange(url("/api/mantenimientos/" + mantenimientoId),
                HttpMethod.DELETE, new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Void.class);
        assertThat(adminElimina.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> historial = restTemplate.exchange(
                url("/api/automoviles/" + automovilId + "/mantenimientos"), HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Map.class);
        assertThat((List<?>) historial.getBody().get("contenido")).isEmpty();

        // FR-028, hallazgo F1 de /speckit-converge (fortalecido tras hallazgo F1 de una
        // segunda corrida de /speckit-converge, T061): comparar tamaño antes/después.
        assertThat(historialAuditoriaDe(personaId)).hasSizeGreaterThan(historialAntesBaja);
        int historialAntesRestaurar = historialAuditoriaDe(personaId).size();

        ResponseEntity<Map> capturistaRestaurar = restTemplate.exchange(
                url("/api/mantenimientos/" + mantenimientoId + "/restaurar"), HttpMethod.POST,
                new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), Map.class);
        assertThat(capturistaRestaurar.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> adminRestaura = restTemplate.exchange(
                url("/api/mantenimientos/" + mantenimientoId + "/restaurar"), HttpMethod.POST,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Map.class);
        assertThat(adminRestaura.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(adminRestaura.getBody().get("activo")).isEqualTo(true);

        ResponseEntity<Map> historialTrasRestaurar = restTemplate.exchange(
                url("/api/automoviles/" + automovilId + "/mantenimientos"), HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Map.class);
        assertThat((List<?>) historialTrasRestaurar.getBody().get("contenido")).hasSize(1);

        // FR-028, hallazgo F1 de /speckit-converge (fortalecido tras hallazgo F1 de una
        // segunda corrida de /speckit-converge, T061): comparar tamaño antes/después.
        assertThat(historialAuditoriaDe(personaId)).hasSizeGreaterThan(historialAntesRestaurar);

        ResponseEntity<Map> restaurarDeNuevo = restTemplate.exchange(
                url("/api/mantenimientos/" + mantenimientoId + "/restaurar"), HttpMethod.POST,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Map.class);
        assertThat(restaurarDeNuevo.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(restaurarDeNuevo.getBody().get("codigo")).isEqualTo("MANTENIMIENTO_YA_ACTIVO");
    }

    @Test
    void editarUnMantenimientoCuyoAutomovilFueEliminadoResponde409() {
        // FR-024, hallazgo F3 de /speckit-analyze
        String personaId = crearPersona();
        String automovilId = crearAutomovil(personaId);
        Map<String, Object> creado = registrarMantenimiento(automovilId, 1000);

        // DELETE /api/automoviles/{id} es de US7 (aun no implementado); se desactiva
        // directo por repositorio para no depender de esa historia todavia no construida.
        var automovil = automovilRepository.findById(java.util.UUID.fromString(automovilId)).orElseThrow();
        automovil.desactivar();
        automovilRepository.saveAndFlush(automovil);

        ResponseEntity<Map> respuesta = restTemplate.exchange(url("/api/mantenimientos/" + creado.get("id")),
                HttpMethod.PATCH, new HttpEntity<>(Map.of("descripcion", "Nueva"),
                        TestJwt.bearerHeaders(capturistaToken)), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody().get("codigo")).isEqualTo("AUTOMOVIL_ELIMINADO");
    }
}
