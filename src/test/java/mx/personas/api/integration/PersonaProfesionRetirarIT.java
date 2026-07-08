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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US5: retirar una asignación de profesión (FR-013, FR-015, FR-024).
 */
@TestPropertySource(properties = {
        "app.security.admin-bootstrap-login=" + TestJwt.ADMIN_LOGIN,
        "app.security.admin-bootstrap-password=" + TestJwt.ADMIN_PASSWORD
})
class PersonaProfesionRetirarIT extends AbstractIntegrationTest {

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
        direccion.put("calle", "Calle Retirar");
        direccion.put("numero", "1");
        direccion.put("colonia", "Centro");
        direccion.put("municipio", "Municipio Test");
        direccion.put("estado", "Estado Test");
        direccion.put("codigoPostal", "00000");
        direccion.put("pais", "US");

        Map<String, Object> persona = new LinkedHashMap<>();
        persona.put("nombres", "Retirar");
        persona.put("apellidos", "Test " + h);
        persona.put("fechaNacimiento", LocalDate.of(1990, 1, 1).toString());
        persona.put("sexo", "F");
        persona.put("curp", "RETI900101MDFRZN" + h);
        persona.put("rfc", "RETI900101AB" + h.charAt(0));
        persona.put("correo", "retirar." + System.nanoTime() + "@example.com");
        persona.put("telefono", "5500011155");
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

    private ResponseEntity<Map> asignar(String token, String personaId, Long profesionId) {
        return restTemplate.postForEntity(url("/api/personas/" + personaId + "/profesiones"),
                new HttpEntity<>(Map.of("profesionId", profesionId), TestJwt.bearerHeaders(token)), Map.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> profesionesDe(String personaId, boolean incluirRetiradas, String token) {
        ResponseEntity<List> respuesta = restTemplate.exchange(
                url("/api/personas/" + personaId + "/profesiones?incluirRetiradas=" + incluirRetiradas),
                HttpMethod.GET, new HttpEntity<>(TestJwt.bearerHeaders(token)), List.class);
        return respuesta.getBody();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> historialDe(String personaId) {
        ResponseEntity<Map> respuesta = restTemplate.exchange(
                url("/api/personas/" + personaId + "/historial"), HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Map.class);
        return (List<Map<String, Object>>) respuesta.getBody().get("contenido");
    }

    @Test
    void capturistaPuedeRetirarUnaAsignacionActivaYQuedaEnElHistorialYElDirectorio() {
        // FR-022, hallazgo E6 de /speckit-analyze: retirar también está permitido a
        // CAPTURISTA, no solo a ADMIN — todo este test corre autenticado como tal.
        String personaId = crearPersona();
        Long profesionId = crearProfesion("Mecánico Retiro " + TestUniqueId.homoclave());
        Map<String, Object> asignacion = asignar(capturistaToken, personaId, profesionId).getBody();
        String asignacionId = (String) asignacion.get("id");

        ResponseEntity<Map> retirada = restTemplate.exchange(
                url("/api/personas/" + personaId + "/profesiones/" + asignacionId + "/retirar"),
                HttpMethod.PATCH, new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), Map.class);

        assertThat(retirada.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retirada.getBody().get("activo")).isEqualTo(false);

        // Deja de aparecer en el directorio de esa profesión.
        ResponseEntity<Map> directorio = restTemplate.exchange(url("/api/profesiones/" + profesionId + "/personas"),
                HttpMethod.GET, new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), Map.class);
        List<Map<String, Object>> contenido = (List<Map<String, Object>>) directorio.getBody().get("contenido");
        assertThat(contenido).noneSatisfy(item -> assertThat(item.get("id")).isEqualTo(personaId));

        // FR-024, hallazgo E4: el retiro también queda en el historial.
        assertThat(historialDe(personaId)).hasSizeGreaterThanOrEqualTo(2);

        // ADMIN sigue viendo la asignación retirada como histórico.
        assertThat(profesionesDe(personaId, true, adminToken)).anySatisfy(
                a -> assertThat(a.get("id")).isEqualTo(asignacionId));
        assertThat(profesionesDe(personaId, false, adminToken)).isEmpty();
    }

    @Test
    void retirarUnaAsignacionYaRetiradaResponde409() {
        String personaId = crearPersona();
        Long profesionId = crearProfesion("Electricista Retiro " + TestUniqueId.homoclave());
        String asignacionId = (String) asignar(capturistaToken, personaId, profesionId).getBody().get("id");

        restTemplate.exchange(url("/api/personas/" + personaId + "/profesiones/" + asignacionId + "/retirar"),
                HttpMethod.PATCH, new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), Map.class);

        ResponseEntity<Map> segundoRetiro = restTemplate.exchange(
                url("/api/personas/" + personaId + "/profesiones/" + asignacionId + "/retirar"),
                HttpMethod.PATCH, new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), Map.class);

        assertThat(segundoRetiro.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(segundoRetiro.getBody().get("codigo")).isEqualTo("PERSONA_PROFESION_YA_RETIRADA");
    }

    @Test
    void retirarUnaAsignacionInexistenteResponde404() {
        String personaId = crearPersona();

        ResponseEntity<Map> respuesta = restTemplate.exchange(
                url("/api/personas/" + personaId + "/profesiones/" + UUID.randomUUID() + "/retirar"),
                HttpMethod.PATCH, new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(respuesta.getBody().get("codigo")).isEqualTo("PERSONA_PROFESION_NO_ENCONTRADA");
    }

    @Test
    void reasignarUnaProfesionPreviamenteRetiradaCreaUnaAsignacionNuevaSinTocarLaAnterior() {
        // FR-013, research.md §4, hallazgo E1 de /speckit-analyze: la decisión central
        // de /speckit-clarify para este feature.
        String personaId = crearPersona();
        Long profesionId = crearProfesion("Mecánico Reasignar " + TestUniqueId.homoclave());

        String primeraAsignacionId = (String) asignar(capturistaToken, personaId, profesionId).getBody().get("id");
        restTemplate.exchange(url("/api/personas/" + personaId + "/profesiones/" + primeraAsignacionId + "/retirar"),
                HttpMethod.PATCH, new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), Map.class);

        ResponseEntity<Map> segundaAsignacion = asignar(capturistaToken, personaId, profesionId);
        assertThat(segundaAsignacion.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String segundaAsignacionId = (String) segundaAsignacion.getBody().get("id");

        assertThat(segundaAsignacionId).isNotEqualTo(primeraAsignacionId);

        List<Map<String, Object>> historicoCompleto = profesionesDe(personaId, true, adminToken);
        assertThat(historicoCompleto).hasSize(2);
        assertThat(historicoCompleto).anySatisfy(a -> {
            assertThat(a.get("id")).isEqualTo(primeraAsignacionId);
            assertThat(a.get("activo")).isEqualTo(false);
        });
        assertThat(historicoCompleto).anySatisfy(a -> {
            assertThat(a.get("id")).isEqualTo(segundaAsignacionId);
            assertThat(a.get("activo")).isEqualTo(true);
        });
    }
}
