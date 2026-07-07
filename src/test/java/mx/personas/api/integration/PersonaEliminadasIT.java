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
 * US4: vista dedicada de personas eliminadas logicamente, solo ADMIN (FR-012, FR-013).
 */
@TestPropertySource(properties = {
        "app.security.admin-bootstrap-login=" + TestJwt.ADMIN_LOGIN,
        "app.security.admin-bootstrap-password=" + TestJwt.ADMIN_PASSWORD
})
class PersonaEliminadasIT extends AbstractIntegrationTest {

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

        cp = "0950" + (System.nanoTime() % 10);
        String csv = """
                codigoPostal|estado|municipio|asentamiento|tipoAsentamiento|idAsentaCpcons
                %s|Ciudad de México|Cuauhtémoc|Roma Norte|Colonia|1
                """.formatted(cp);
        Path archivo = Files.createTempFile("sepomex-eliminadas-it", ".csv");
        Files.writeString(archivo, csv, StandardCharsets.UTF_8);
        sepomexImportService.importar(archivo);
    }

    private String crearPersona(String correo) {
        Map<String, Object> direccion = new LinkedHashMap<>();
        direccion.put("calle", "Calle Eliminadas");
        direccion.put("numero", "1");
        direccion.put("colonia", "Roma Norte");
        direccion.put("codigoPostal", cp);
        direccion.put("pais", "MX");

        Map<String, Object> persona = new LinkedHashMap<>();
        persona.put("nombres", "Eliminadas");
        persona.put("apellidos", "Prueba");
        persona.put("fechaNacimiento", "1990-01-01");
        persona.put("sexo", "F");
        persona.put("curp", "ELIM900101MDFRZN" + TestUniqueId.homoclave());
        persona.put("rfc", "ELIM900101AB1");
        persona.put("correo", correo);
        persona.put("telefono", "5500055566");
        persona.put("direccion", direccion);

        ResponseEntity<Map> creado = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas", HttpMethod.POST,
                new HttpEntity<>(persona, TestJwt.bearerHeaders(adminToken)), Map.class);
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) creado.getBody().get("id");
    }

    @SuppressWarnings("unchecked")
    @Test
    void vistaDeEliminadasSoloIncluyeLasEliminadasNoLasActivas() {
        String correoActiva = "activa.eliminadas." + System.nanoTime() + "@example.com";
        String correoEliminada = "eliminada.eliminadas." + System.nanoTime() + "@example.com";
        crearPersona(correoActiva);
        String idEliminada = crearPersona(correoEliminada);

        restTemplate.exchange("http://localhost:" + port + "/api/personas/" + idEliminada, HttpMethod.DELETE,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Void.class);

        ResponseEntity<Map> respuesta = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas/eliminadas", HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> contenido = (List<Map<String, Object>>) respuesta.getBody().get("contenido");
        assertThat(contenido).extracting(p -> p.get("correo")).contains(correoEliminada);
        assertThat(contenido).extracting(p -> p.get("correo")).doesNotContain(correoActiva);
    }

    @Test
    void capturistaConsultandoEliminadasRegresa403() {
        ResponseEntity<Map> respuesta = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas/eliminadas", HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
