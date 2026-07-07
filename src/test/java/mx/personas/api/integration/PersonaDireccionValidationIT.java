package mx.personas.api.integration;

import mx.personas.api.codigopostal.importer.SepomexImportService;
import mx.personas.api.common.AbstractIntegrationTest;
import mx.personas.api.common.TestJwt;
import mx.personas.api.common.TestUniqueId;
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
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US5: casos de error de la integracion direccion <-> catalogo — CP mexicano inexistente
 * (404 CP_NO_ENCONTRADO), colonia fuera de la lista del CP (400 COLONIA_NO_VALIDA_PARA_CP),
 * y direccion de un pais distinto de Mexico que no se valida contra el catalogo (FR-022).
 */
@TestPropertySource(properties = {
        "app.security.admin-bootstrap-login=" + TestJwt.ADMIN_LOGIN,
        "app.security.admin-bootstrap-password=" + TestJwt.ADMIN_PASSWORD
})
class PersonaDireccionValidationIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SepomexImportService sepomexImportService;

    private String cp;
    private String accessToken;

    @BeforeEach
    void sembrarCatalogo() throws IOException {
        accessToken = TestJwt.loginAdmin(restTemplate, port);
        cp = "0860" + (System.nanoTime() % 10);
        String csv = """
                codigoPostal|estado|municipio|asentamiento|tipoAsentamiento|idAsentaCpcons
                %s|Jalisco|Guadalajara|Americana|Colonia|1
                """.formatted(cp);
        Path archivo = Files.createTempFile("sepomex-direccion-validation-it", ".csv");
        Files.writeString(archivo, csv, StandardCharsets.UTF_8);
        sepomexImportService.importar(archivo);
    }

    private HttpHeaders headers() {
        return TestJwt.bearerHeaders(accessToken);
    }

    private Map<String, Object> cuerpoPersona(String correo, String colonia, String codigoPostal, String pais) {
        Map<String, Object> direccion = new LinkedHashMap<>();
        direccion.put("calle", "Calle X");
        direccion.put("numero", "1");
        direccion.put("colonia", colonia);
        // Para direcciones no-MX no hay catálogo del cual autocompletar (FR-022), por lo
        // que el cliente debe proporcionar municipio/estado directamente.
        direccion.put("municipio", "Beverly Hills");
        direccion.put("estado", "California");
        direccion.put("codigoPostal", codigoPostal);
        direccion.put("pais", pais);

        Map<String, Object> persona = new LinkedHashMap<>();
        persona.put("nombres", "Validacion");
        persona.put("apellidos", "Test");
        persona.put("fechaNacimiento", "1990-01-01");
        persona.put("sexo", "F");
        persona.put("curp", "VATE900101MDFRZN" + TestUniqueId.homoclave());
        persona.put("rfc", "VATE900101AB1");
        persona.put("correo", correo);
        persona.put("telefono", "5522233344");
        persona.put("direccion", direccion);
        return persona;
    }

    @Test
    void cpMexicanoInexistenteRegresa404() {
        String correo = "validacion.cp." + System.nanoTime() + "@example.com";
        ResponseEntity<Map> respuesta = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas", HttpMethod.POST,
                new HttpEntity<>(cuerpoPersona(correo, "Americana", "00000", "MX"), headers()), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(respuesta.getBody().get("codigo")).isEqualTo("CP_NO_ENCONTRADO");
    }

    @Test
    void coloniaFueraDeLaListaDelCpRegresa400() {
        String correo = "validacion.colonia." + System.nanoTime() + "@example.com";
        ResponseEntity<Map> respuesta = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas", HttpMethod.POST,
                new HttpEntity<>(cuerpoPersona(correo, "Colonia Inexistente En Ese CP", cp, "MX"), headers()),
                Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody().get("codigo")).isEqualTo("COLONIA_NO_VALIDA_PARA_CP");
    }

    @Test
    void direccionDePaisDistintoDeMexicoNoValidaContraElCatalogo() {
        String correo = "validacion.pais." + System.nanoTime() + "@example.com";
        ResponseEntity<Map> respuesta = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas", HttpMethod.POST,
                new HttpEntity<>(cuerpoPersona(correo, "Cualquier Colonia", "90210", "US"), headers()), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> direccion = (Map<?, ?>) respuesta.getBody().get("direccion");
        assertThat(direccion.get("codigoPostal")).isEqualTo("90210");
        assertThat(direccion.get("colonia")).isEqualTo("Cualquier Colonia");
    }
}
