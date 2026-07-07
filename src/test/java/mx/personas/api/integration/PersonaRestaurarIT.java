package mx.personas.api.integration;

import mx.personas.api.codigopostal.importer.SepomexImportService;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US3: restaurar una persona eliminada logicamente, solo ADMIN (FR-013 a FR-015).
 */
@TestPropertySource(properties = {
        "app.security.admin-bootstrap-login=" + TestJwt.ADMIN_LOGIN,
        "app.security.admin-bootstrap-password=" + TestJwt.ADMIN_PASSWORD
})
class PersonaRestaurarIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SepomexImportService sepomexImportService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String capturistaToken;
    private String cp;

    @BeforeEach
    void preparar() throws IOException {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        adminToken = TestJwt.loginAdmin(restTemplate, port);
        TestJwt.sembrarUsuario(usuarioRepository, passwordEncoder,
                TestJwt.CAPTURISTA_LOGIN, TestJwt.CAPTURISTA_PASSWORD, Rol.CAPTURISTA);
        capturistaToken = TestJwt.login(restTemplate, port, TestJwt.CAPTURISTA_LOGIN, TestJwt.CAPTURISTA_PASSWORD);

        cp = "0920" + (System.nanoTime() % 10);
        String csv = """
                codigoPostal|estado|municipio|asentamiento|tipoAsentamiento|idAsentaCpcons
                %s|Ciudad de México|Cuauhtémoc|Roma Norte|Colonia|1
                """.formatted(cp);
        Path archivo = Files.createTempFile("sepomex-restaurar-it", ".csv");
        Files.writeString(archivo, csv, StandardCharsets.UTF_8);
        sepomexImportService.importar(archivo);
    }

    private Map<String, Object> cuerpoPersonaValida(String correo, String curp) {
        Map<String, Object> direccion = new LinkedHashMap<>();
        direccion.put("calle", "Calle Restaurar");
        direccion.put("numero", "1");
        direccion.put("colonia", "Roma Norte");
        direccion.put("codigoPostal", cp);
        direccion.put("pais", "MX");

        Map<String, Object> persona = new LinkedHashMap<>();
        persona.put("nombres", "Restaurar");
        persona.put("apellidos", "Prueba");
        persona.put("fechaNacimiento", "1990-01-01");
        persona.put("sexo", "F");
        persona.put("curp", curp);
        persona.put("rfc", "RSPR900101AB1");
        persona.put("correo", correo);
        persona.put("telefono", "5500033344");
        persona.put("direccion", direccion);
        return persona;
    }

    private String crearPersona(String correo, String curp) {
        ResponseEntity<Map> creado = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas", HttpMethod.POST,
                new HttpEntity<>(cuerpoPersonaValida(correo, curp), TestJwt.bearerHeaders(adminToken)), Map.class);
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) creado.getBody().get("id");
    }

    @SuppressWarnings("unchecked")
    @Test
    void eliminarYRestaurarCicloCompleto() {
        String id = crearPersona("restaurar." + System.nanoTime() + "@example.com",
                "RSPR900101MDFRZN" + TestUniqueId.homoclave());

        ResponseEntity<Void> eliminado = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas/" + id, HttpMethod.DELETE,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Void.class);
        assertThat(eliminado.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> consultaTrasEliminar = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas/" + id, HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Map.class);
        assertThat(consultaTrasEliminar.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Map> restaurado = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas/" + id + "/restaurar", HttpMethod.PATCH,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Map.class);
        assertThat(restaurado.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> consultaTrasRestaurar = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas/" + id, HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Map.class);
        assertThat(consultaTrasRestaurar.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> historial = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas/" + id + "/historial", HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Map.class);
        List<Map<String, Object>> contenido = (List<Map<String, Object>>) historial.getBody().get("contenido");
        assertThat(contenido).extracting(e -> e.get("operacion")).contains("RESTAURACION");
    }

    @Test
    void capturistaNoPuedeRestaurarRegresa403() {
        String id = crearPersona("restaurar403." + System.nanoTime() + "@example.com",
                "RSPR900101MDFRZN" + TestUniqueId.homoclave());
        restTemplate.exchange("http://localhost:" + port + "/api/personas/" + id, HttpMethod.DELETE,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Void.class);

        ResponseEntity<Map> respuesta = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas/" + id + "/restaurar", HttpMethod.PATCH,
                new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void restaurarPersonaYaActivaRegresa409() {
        String id = crearPersona("restaurar.activa." + System.nanoTime() + "@example.com",
                "RSPR900101MDFRZN" + TestUniqueId.homoclave());

        ResponseEntity<Map> respuesta = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas/" + id + "/restaurar", HttpMethod.PATCH,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody().get("codigo")).isEqualTo("PERSONA_YA_ACTIVA");
    }

    @Test
    void restaurarConCorreoYaTomadoPorOtraPersonaActivaRegresa409() {
        String correoCompartido = "restaurar.conflicto." + System.nanoTime() + "@example.com";
        String idA = crearPersona(correoCompartido, "RSPR900101MDFRZN" + TestUniqueId.homoclave());
        restTemplate.exchange("http://localhost:" + port + "/api/personas/" + idA, HttpMethod.DELETE,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Void.class);

        crearPersona(correoCompartido, "RSPR900101MDFRZN" + TestUniqueId.homoclave());

        ResponseEntity<Map> respuesta = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas/" + idA + "/restaurar", HttpMethod.PATCH,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody().get("codigo")).isEqualTo("PERSONA_CORREO_DUPLICADO");
    }
}
