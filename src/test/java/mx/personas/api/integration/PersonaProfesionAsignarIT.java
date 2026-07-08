package mx.personas.api.integration;

import mx.personas.api.common.AbstractIntegrationTest;
import mx.personas.api.common.TestJwt;
import mx.personas.api.common.TestUniqueId;
import mx.personas.api.profesion.repository.PersonaProfesionRepository;
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
 * US2: asignar una profesión a una persona (FR-011 a FR-014, FR-024).
 */
@TestPropertySource(properties = {
        "app.security.admin-bootstrap-login=" + TestJwt.ADMIN_LOGIN,
        "app.security.admin-bootstrap-password=" + TestJwt.ADMIN_PASSWORD
})
class PersonaProfesionAsignarIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PersonaProfesionRepository personaProfesionRepository;

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

    private String crearPersona(String token) {
        String h = TestUniqueId.homoclave();
        Map<String, Object> direccion = new LinkedHashMap<>();
        direccion.put("calle", "Calle Asignar");
        direccion.put("numero", "1");
        direccion.put("colonia", "Centro");
        direccion.put("municipio", "Municipio Test");
        direccion.put("estado", "Estado Test");
        direccion.put("codigoPostal", "00000");
        direccion.put("pais", "US");

        Map<String, Object> persona = new LinkedHashMap<>();
        persona.put("nombres", "Test");
        persona.put("apellidos", "Asignar " + h);
        persona.put("fechaNacimiento", LocalDate.of(1990, 1, 1).toString());
        persona.put("sexo", "F");
        persona.put("curp", "ASIG900101MDFRZN" + h);
        persona.put("rfc", "ASIG900101AB" + h.charAt(0));
        persona.put("correo", "asignar." + System.nanoTime() + "@example.com");
        persona.put("telefono", "5500011122");
        persona.put("direccion", direccion);

        ResponseEntity<Map> creado = restTemplate.exchange(url("/api/personas"), HttpMethod.POST,
                new HttpEntity<>(persona, TestJwt.bearerHeaders(token)), Map.class);
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) creado.getBody().get("id");
    }

    private Long crearProfesion(String nombre) {
        ResponseEntity<Map> creada = restTemplate.postForEntity(url("/api/profesiones"),
                new HttpEntity<>(Map.of("nombre", nombre), TestJwt.bearerHeaders(adminToken)), Map.class);
        return ((Number) creada.getBody().get("id")).longValue();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> historialDe(String personaId) {
        ResponseEntity<Map> respuesta = restTemplate.exchange(
                url("/api/personas/" + personaId + "/historial"), HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Map.class);
        return (List<Map<String, Object>>) respuesta.getBody().get("contenido");
    }

    @Test
    void asignarUnaProfesionActivaAUnaPersonaActivaRegistraLaAsignacionYElHistorial() {
        String personaId = crearPersona(capturistaToken);
        Long profesionId = crearProfesion("Electricista " + TestUniqueId.homoclave());

        ResponseEntity<Map> respuesta = restTemplate.postForEntity(
                url("/api/personas/" + personaId + "/profesiones"),
                new HttpEntity<>(Map.of("profesionId", profesionId), TestJwt.bearerHeaders(capturistaToken)),
                Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(respuesta.getBody().get("activo")).isEqualTo(true);
        assertThat(respuesta.getBody().get("fechaDesde")).isEqualTo(LocalDate.now().toString());

        // FR-024, hallazgo E4: la asignación queda en el historial de la persona.
        List<Map<String, Object>> historial = historialDe(personaId);
        assertThat(historial).anySatisfy(entrada -> assertThat(entrada.get("operacion")).isEqualTo("MODIFICACION"));
    }

    @Test
    void asignarLaMismaProfesionDosVecesActivaResponde409() {
        String personaId = crearPersona(capturistaToken);
        Long profesionId = crearProfesion("Plomero " + TestUniqueId.homoclave());

        restTemplate.postForEntity(url("/api/personas/" + personaId + "/profesiones"),
                new HttpEntity<>(Map.of("profesionId", profesionId), TestJwt.bearerHeaders(capturistaToken)),
                Map.class);

        ResponseEntity<Map> segunda = restTemplate.postForEntity(url("/api/personas/" + personaId + "/profesiones"),
                new HttpEntity<>(Map.of("profesionId", profesionId), TestJwt.bearerHeaders(capturistaToken)),
                Map.class);

        assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(segunda.getBody().get("codigo")).isEqualTo("PERSONA_PROFESION_YA_ASIGNADA");
    }

    @Test
    void asignarUnaProfesionDesactivadaResponde409() {
        String personaId = crearPersona(capturistaToken);
        Long profesionId = crearProfesion("Herrero " + TestUniqueId.homoclave());
        restTemplate.exchange(url("/api/profesiones/" + profesionId + "/desactivar"), HttpMethod.PATCH,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Map.class);

        ResponseEntity<Map> respuesta = restTemplate.postForEntity(url("/api/personas/" + personaId + "/profesiones"),
                new HttpEntity<>(Map.of("profesionId", profesionId), TestJwt.bearerHeaders(capturistaToken)),
                Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody().get("codigo")).isEqualTo("PROFESION_DESACTIVADA");
    }

    @Test
    void asignarAUnaPersonaEliminadaLogicamenteResponde409() {
        String personaId = crearPersona(capturistaToken);
        Long profesionId = crearProfesion("Pintor " + TestUniqueId.homoclave());
        restTemplate.exchange(url("/api/personas/" + personaId), HttpMethod.DELETE,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Void.class);

        ResponseEntity<Map> respuesta = restTemplate.postForEntity(url("/api/personas/" + personaId + "/profesiones"),
                new HttpEntity<>(Map.of("profesionId", profesionId), TestJwt.bearerHeaders(capturistaToken)),
                Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody().get("codigo")).isEqualTo("PERSONA_ELIMINADA");
    }

    @Test
    void desactivarUnaProfesionNoAfectaLasAsignacionesYaExistentes() {
        // FR-008, hallazgo E2 de /speckit-analyze: vive aquí (no en US1) para no romper
        // la independencia de esa historia.
        String personaId = crearPersona(capturistaToken);
        Long profesionId = crearProfesion("Soldador " + TestUniqueId.homoclave());
        restTemplate.postForEntity(url("/api/personas/" + personaId + "/profesiones"),
                new HttpEntity<>(Map.of("profesionId", profesionId), TestJwt.bearerHeaders(capturistaToken)),
                Map.class);

        restTemplate.exchange(url("/api/profesiones/" + profesionId + "/desactivar"), HttpMethod.PATCH,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Map.class);

        // Verificado directo por repositorio: el endpoint de consulta (GET
        // /api/personas/{id}/profesiones) es de US3, no de esta historia (US2) — no se
        // depende de él aquí para mantener US2 independientemente probable.
        boolean sigueAsignada = personaProfesionRepository
                .existsByPersonaIdAndProfesionIdAndActivoTrue(java.util.UUID.fromString(personaId), profesionId);
        assertThat(sigueAsignada).isTrue();
    }
}
