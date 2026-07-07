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
 * US2: historial completo de cambios de una persona, solo ADMIN (FR-005 a FR-012).
 */
@TestPropertySource(properties = {
        "app.security.admin-bootstrap-login=" + TestJwt.ADMIN_LOGIN,
        "app.security.admin-bootstrap-password=" + TestJwt.ADMIN_PASSWORD
})
class PersonaHistorialIT extends AbstractIntegrationTest {

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

        cp = "0910" + (System.nanoTime() % 10);
        String csv = """
                codigoPostal|estado|municipio|asentamiento|tipoAsentamiento|idAsentaCpcons
                %s|Ciudad de México|Cuauhtémoc|Roma Norte|Colonia|1
                """.formatted(cp);
        Path archivo = Files.createTempFile("sepomex-historial-it", ".csv");
        Files.writeString(archivo, csv, StandardCharsets.UTF_8);
        sepomexImportService.importar(archivo);
    }

    private Map<String, Object> cuerpoPersonaValida(String correo) {
        Map<String, Object> direccion = new LinkedHashMap<>();
        direccion.put("calle", "Calle Original");
        direccion.put("numero", "1");
        direccion.put("colonia", "Roma Norte");
        direccion.put("codigoPostal", cp);
        direccion.put("pais", "MX");

        Map<String, Object> persona = new LinkedHashMap<>();
        persona.put("nombres", "Historial");
        persona.put("apellidos", "Prueba");
        persona.put("fechaNacimiento", "1990-01-01");
        persona.put("sexo", "F");
        persona.put("curp", "HIPR900101MDFRZN" + TestUniqueId.homoclave());
        persona.put("rfc", "HIPR900101AB1");
        persona.put("correo", correo);
        persona.put("telefono", "5500011122");
        persona.put("direccion", direccion);
        return persona;
    }

    @SuppressWarnings("unchecked")
    @Test
    void historialCompletoRegistraCreacionModificacionYEliminacionEnOrden() {
        String correo = "historial." + System.nanoTime() + "@example.com";
        ResponseEntity<Map> creado = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas", HttpMethod.POST,
                new HttpEntity<>(cuerpoPersonaValida(correo), TestJwt.bearerHeaders(adminToken)), Map.class);
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = (String) creado.getBody().get("id");

        Map<String, Object> cambio = new LinkedHashMap<>();
        cambio.put("telefono", "5599988877");
        Map<String, Object> nuevaDireccion = new LinkedHashMap<>();
        nuevaDireccion.put("calle", "Calle Nueva");
        cambio.put("direccion", nuevaDireccion);
        ResponseEntity<Map> modificado = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas/" + id, HttpMethod.PATCH,
                new HttpEntity<>(cambio, TestJwt.bearerHeaders(capturistaToken)), Map.class);
        assertThat(modificado.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Void> eliminado = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas/" + id, HttpMethod.DELETE,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Void.class);
        assertThat(eliminado.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> historial = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas/" + id + "/historial", HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Map.class);
        assertThat(historial.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> contenido = (List<Map<String, Object>>) historial.getBody().get("contenido");
        assertThat(contenido).extracting(e -> e.get("operacion"))
                .containsExactly("ELIMINACION", "MODIFICACION", "CREACION");

        Map<String, Object> entradaCreacion = contenido.get(2);
        assertThat(entradaCreacion.get("usuario")).isEqualTo(TestJwt.ADMIN_LOGIN);

        Map<String, Object> entradaModificacion = contenido.get(1);
        assertThat(entradaModificacion.get("usuario")).isEqualTo(TestJwt.CAPTURISTA_LOGIN);
        List<Map<String, Object>> cambiosModificacion = (List<Map<String, Object>>) entradaModificacion.get("cambios");
        assertThat(cambiosModificacion).extracting(c -> c.get("campo"))
                .contains("telefono", "direccion.calle");
        Map<String, Object> cambioTelefono = cambiosModificacion.stream()
                .filter(c -> "telefono".equals(c.get("campo"))).findFirst().orElseThrow();
        assertThat((String) cambioTelefono.get("valorNuevo")).doesNotContain("5599988877");
        assertThat((String) cambioTelefono.get("valorNuevo")).contains("*");

        Map<String, Object> entradaEliminacion = contenido.get(0);
        assertThat(entradaEliminacion.get("usuario")).isEqualTo(TestJwt.ADMIN_LOGIN);
    }

    @Test
    void capturistaConsultandoHistorialRegresa403() {
        String correo = "historial.403." + System.nanoTime() + "@example.com";
        ResponseEntity<Map> creado = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas", HttpMethod.POST,
                new HttpEntity<>(cuerpoPersonaValida(correo), TestJwt.bearerHeaders(adminToken)), Map.class);
        String id = (String) creado.getBody().get("id");

        ResponseEntity<Map> respuesta = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas/" + id + "/historial", HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(capturistaToken)), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(respuesta.getBody().get("codigo")).isEqualTo("ACCESO_DENEGADO");
    }
}
