package mx.personas.api.integration;

import mx.personas.api.codigopostal.model.CatalogoImportacion.OrigenImportacion;
import mx.personas.api.codigopostal.repository.CatalogoImportacionRepository;
import mx.personas.api.codigopostal.service.CatalogoImportacionOrchestrator;
import mx.personas.api.common.AbstractIntegrationTest;
import mx.personas.api.common.TestJwt;
import mx.personas.api.common.TestUniqueId;
import mx.personas.api.usuario.model.Rol;
import mx.personas.api.usuario.model.Usuario;
import mx.personas.api.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US3: consulta paginada de la bitácora de corridas de importación (FR-008).
 */
@TestPropertySource(properties = {
        "app.security.admin-bootstrap-login=" + TestJwt.ADMIN_LOGIN,
        "app.security.admin-bootstrap-password=" + TestJwt.ADMIN_PASSWORD
})
class CatalogoImportacionBitacoraIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CatalogoImportacionOrchestrator orchestrator;

    @Autowired
    private CatalogoImportacionRepository catalogoImportacionRepository;

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

    @Test
    void capturistaNoPuedeConsultarLaBitacora() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(capturistaToken);
        ResponseEntity<Map> respuesta = restTemplate.exchange(
                "http://localhost:" + port + "/api/codigos-postales/importaciones",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void adminConsultaLaBitacoraYVeCorridasProgramadasYManualesConSusDatos() throws IOException {
        String cpProgramada = "0960" + TestUniqueId.homoclave().charAt(0);
        String cpManual = "0961" + TestUniqueId.homoclave().charAt(0);

        Path archivoProgramado = escribirCsv(cpProgramada);
        orchestrator.ejecutar(archivoProgramado, "corrida-programada.csv",
                "hash-" + TestUniqueId.homoclave(), OrigenImportacion.PROGRAMADA, null);

        Usuario admin = usuarioRepository.findByLogin(TestJwt.ADMIN_LOGIN).orElseThrow();
        Path archivoManual = escribirCsv(cpManual);
        orchestrator.ejecutar(archivoManual, "corrida-manual.csv",
                "hash-" + TestUniqueId.homoclave(), OrigenImportacion.MANUAL, admin.getId());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        ResponseEntity<Map> respuesta = restTemplate.exchange(
                "http://localhost:" + port + "/api/codigos-postales/importaciones?size=100",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> contenido = (List<Map<String, Object>>) respuesta.getBody().get("contenido");

        assertThat(contenido).anySatisfy(c -> {
            assertThat(c.get("archivo")).isEqualTo("corrida-programada.csv");
            assertThat(c.get("origen")).isEqualTo("PROGRAMADA");
            assertThat(c.get("estado")).isEqualTo("EXITO");
            assertThat(c.get("usuario")).isNull();
        });
        assertThat(contenido).anySatisfy(c -> {
            assertThat(c.get("archivo")).isEqualTo("corrida-manual.csv");
            assertThat(c.get("origen")).isEqualTo("MANUAL");
            assertThat(c.get("estado")).isEqualTo("EXITO");
            assertThat(c.get("usuario")).isEqualTo(TestJwt.ADMIN_LOGIN);
        });
    }

    private Path escribirCsv(String cp) throws IOException {
        String csv = """
                codigoPostal|estado|municipio|asentamiento|tipoAsentamiento|idAsentaCpcons
                %s|Ciudad de México|Iztapalapa|Barrio Bitácora|Colonia|1
                """.formatted(cp);
        Path archivo = Files.createTempFile("sepomex-bitacora-it", ".csv");
        Files.writeString(archivo, csv, StandardCharsets.UTF_8);
        return archivo;
    }
}
