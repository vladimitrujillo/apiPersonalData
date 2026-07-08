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
 * US4: directorio de personas por profesión (FR-018 a FR-020).
 */
@TestPropertySource(properties = {
        "app.security.admin-bootstrap-login=" + TestJwt.ADMIN_LOGIN,
        "app.security.admin-bootstrap-password=" + TestJwt.ADMIN_PASSWORD
})
class ProfesionDirectorioIT extends AbstractIntegrationTest {

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
        direccion.put("calle", "Calle Directorio");
        direccion.put("numero", "1");
        direccion.put("colonia", "Centro");
        direccion.put("municipio", "Municipio Test");
        direccion.put("estado", "Estado Test");
        direccion.put("codigoPostal", "00000");
        direccion.put("pais", "US");

        Map<String, Object> persona = new LinkedHashMap<>();
        persona.put("nombres", "Directorio");
        persona.put("apellidos", "Test " + h);
        persona.put("fechaNacimiento", LocalDate.of(1990, 1, 1).toString());
        persona.put("sexo", "F");
        persona.put("curp", "DIRT900101MDFRZN" + h);
        persona.put("rfc", "DIRT900101AB" + h.charAt(0));
        persona.put("correo", "directorio." + System.nanoTime() + "@example.com");
        persona.put("telefono", "5500011144");
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
    private List<Map<String, Object>> directorioDe(Long profesionId) {
        ResponseEntity<Map> respuesta = restTemplate.exchange(
                url("/api/profesiones/" + profesionId + "/personas"), HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), Map.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (List<Map<String, Object>>) respuesta.getBody().get("contenido");
    }

    @Test
    void personaActivaConAsignacionActivaApareceEnElDirectorioConDatosReducidos() {
        String personaId = crearPersona();
        Long profesionId = crearProfesion("Mecánico Directorio " + TestUniqueId.homoclave());
        asignar(personaId, profesionId);

        List<Map<String, Object>> contenido = directorioDe(profesionId);

        assertThat(contenido).anySatisfy(item -> {
            assertThat(item.get("id")).isEqualTo(personaId);
            assertThat(item).containsKeys("nombreCompleto", "fechaDesde");
            assertThat(item).doesNotContainKeys("correo", "telefono", "curp", "rfc", "direccion");
        });
    }

    @Test
    void personaEliminadaLogicamenteNoApareceEnElDirectorioYReaparaceAlRestaurarla() {
        // FR-019/SC-006, hallazgo E3 de /speckit-analyze: cubre tanto la desaparición
        // como la reaparición (no solo la primera mitad).
        String personaId = crearPersona();
        Long profesionId = crearProfesion("Mecánico Restaurar " + TestUniqueId.homoclave());
        asignar(personaId, profesionId);
        assertThat(directorioDe(profesionId)).anySatisfy(item -> assertThat(item.get("id")).isEqualTo(personaId));

        restTemplate.exchange(url("/api/personas/" + personaId), HttpMethod.DELETE,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Void.class);
        assertThat(directorioDe(profesionId)).noneSatisfy(item -> assertThat(item.get("id")).isEqualTo(personaId));

        restTemplate.postForEntity(url("/api/personas/" + personaId + "/restaurar"),
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Map.class);
        assertThat(directorioDe(profesionId)).anySatisfy(item -> assertThat(item.get("id")).isEqualTo(personaId));
    }

    @Test
    void elDirectorioSeEntregaPaginado() {
        Long profesionId = crearProfesion("Mecánico Paginado " + TestUniqueId.homoclave());
        for (int i = 0; i < 3; i++) {
            asignar(crearPersona(), profesionId);
        }

        ResponseEntity<Map> respuesta = restTemplate.exchange(
                url("/api/profesiones/" + profesionId + "/personas?size=2"), HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) respuesta.getBody().get("contenido")).hasSize(2);
        assertThat(respuesta.getBody().get("totalElementos")).isEqualTo(3);
    }
}
