package mx.personas.api.integration;

import mx.personas.api.codigopostal.importer.SepomexImportService;
import mx.personas.api.common.AbstractIntegrationTest;
import mx.personas.api.common.TestJwt;
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
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consulta de codigo postal exacto contra datos sembrados en PostgreSQL real (US3, FR-013).
 */
@TestPropertySource(properties = {
        "app.security.admin-bootstrap-login=" + TestJwt.ADMIN_LOGIN,
        "app.security.admin-bootstrap-password=" + TestJwt.ADMIN_PASSWORD
})
class CodigoPostalIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SepomexImportService sepomexImportService;

    private String accessToken;

    @BeforeEach
    void autenticar() {
        accessToken = TestJwt.loginAdmin(restTemplate, port);
    }

    private HttpHeaders headers() {
        return TestJwt.bearerHeaders(accessToken);
    }

    private void sembrarCatalogo(String cp) throws IOException {
        String csv = """
                codigoPostal|estado|municipio|asentamiento|tipoAsentamiento|idAsentaCpcons
                %s|Ciudad de México|Cuauhtémoc|Roma Norte|Colonia|1
                %s|Ciudad de México|Cuauhtémoc|Roma Sur|Colonia|2
                """.formatted(cp, cp);
        Path archivo = Files.createTempFile("sepomex-cp-it", ".csv");
        Files.writeString(archivo, csv, StandardCharsets.UTF_8);
        sepomexImportService.importar(archivo);
    }

    @Test
    void consultaCpSembradoRegresaEstadoMunicipioYColonias() throws IOException {
        String cp = "0670" + (System.nanoTime() % 10);
        sembrarCatalogo(cp);

        ResponseEntity<Map> respuesta = restTemplate.exchange(
                "http://localhost:" + port + "/api/codigos-postales/" + cp,
                HttpMethod.GET, new HttpEntity<>(headers()), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody().get("estado")).isEqualTo("Ciudad de México");
        assertThat(respuesta.getBody().get("municipio")).isEqualTo("Cuauhtémoc");
        assertThat((List<?>) respuesta.getBody().get("colonias")).hasSize(2);
    }

    @Test
    void consultaCpInexistenteRegresa404() {
        ResponseEntity<Map> respuesta = restTemplate.exchange(
                "http://localhost:" + port + "/api/codigos-postales/00000",
                HttpMethod.GET, new HttpEntity<>(headers()), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(respuesta.getBody().get("codigo")).isEqualTo("CP_NO_ENCONTRADO");
    }
}
