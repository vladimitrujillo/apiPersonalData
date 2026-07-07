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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US2: separacion de permisos por rol sobre personas y codigos postales (Acceptance
 * Scenarios US2 #1, #2 y #4). La cobertura de gestion de usuarios (US2 AC #3 / FR-008)
 * se agrega en UsuarioControllerTest (US4), una vez que ese endpoint existe.
 */
@TestPropertySource(properties = {
        "app.security.admin-bootstrap-login=" + TestJwt.ADMIN_LOGIN,
        "app.security.admin-bootstrap-password=" + TestJwt.ADMIN_PASSWORD
})
class RoleAuthorizationIT extends AbstractIntegrationTest {

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

        cp = "0900" + (System.nanoTime() % 10);
        String csv = """
                codigoPostal|estado|municipio|asentamiento|tipoAsentamiento|idAsentaCpcons
                %s|Ciudad de México|Cuauhtémoc|Roma Norte|Colonia|1
                """.formatted(cp);
        Path archivo = Files.createTempFile("sepomex-role-it", ".csv");
        Files.writeString(archivo, csv, StandardCharsets.UTF_8);
        sepomexImportService.importar(archivo);
    }

    private Map<String, Object> cuerpoPersonaValida(String correo) {
        Map<String, Object> direccion = new LinkedHashMap<>();
        direccion.put("calle", "Calle Rol");
        direccion.put("numero", "1");
        direccion.put("colonia", "Roma Norte");
        direccion.put("codigoPostal", cp);
        direccion.put("pais", "MX");

        Map<String, Object> persona = new LinkedHashMap<>();
        persona.put("nombres", "Rol");
        persona.put("apellidos", "Prueba");
        persona.put("fechaNacimiento", "1990-01-01");
        persona.put("sexo", "F");
        persona.put("curp", "ROPR900101MDFRZN" + TestUniqueId.homoclave());
        persona.put("rfc", "ROPR900101AB1");
        persona.put("correo", correo);
        persona.put("telefono", "5500011122");
        persona.put("direccion", direccion);
        return persona;
    }

    @Test
    void capturistaPuedeCrearConsultarListarYActualizarPersonasYConsultarCp() {
        String correo = "rol.capturista." + System.nanoTime() + "@example.com";

        ResponseEntity<Map> creado = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas", HttpMethod.POST,
                new HttpEntity<>(cuerpoPersonaValida(correo), TestJwt.bearerHeaders(capturistaToken)), Map.class);
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = (String) creado.getBody().get("id");

        ResponseEntity<Map> consultado = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas/" + id, HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), Map.class);
        assertThat(consultado.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> listado = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas", HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), Map.class);
        assertThat(listado.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> actualizado = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas/" + id, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("telefono", "5599988877"), TestJwt.bearerHeaders(capturistaToken)),
                Map.class);
        assertThat(actualizado.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> cpConsultado = restTemplate.exchange(
                "http://localhost:" + port + "/api/codigos-postales/" + cp, HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), Map.class);
        assertThat(cpConsultado.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void capturistaNoPuedeEliminarPersonaRegresa403() {
        String correo = "rol.capturista.delete." + System.nanoTime() + "@example.com";
        ResponseEntity<Map> creado = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas", HttpMethod.POST,
                new HttpEntity<>(cuerpoPersonaValida(correo), TestJwt.bearerHeaders(capturistaToken)), Map.class);
        String id = (String) creado.getBody().get("id");

        ResponseEntity<Map> eliminado = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas/" + id, HttpMethod.DELETE,
                new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), Map.class);

        assertThat(eliminado.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(eliminado.getBody().get("codigo")).isEqualTo("ACCESO_DENEGADO");
    }

    @Test
    void adminPuedeRealizarCualquierOperacionIncluidaEliminar() {
        String correo = "rol.admin." + System.nanoTime() + "@example.com";
        ResponseEntity<Map> creado = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas", HttpMethod.POST,
                new HttpEntity<>(cuerpoPersonaValida(correo), TestJwt.bearerHeaders(adminToken)), Map.class);
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = (String) creado.getBody().get("id");

        ResponseEntity<Void> eliminado = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas/" + id, HttpMethod.DELETE,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Void.class);
        assertThat(eliminado.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void llamadaSinNingunTokenAUnEndpointDePersonasRegresa401() {
        ResponseEntity<Map> respuesta = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas/" + UUID.randomUUID(), HttpMethod.GET,
                HttpEntity.EMPTY, Map.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
