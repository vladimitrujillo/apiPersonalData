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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US1: el correo de una persona eliminada logicamente puede reutilizarse (D3, sin cambio
 * de comportamiento respecto al codigo ya existente - FR-001).
 */
@TestPropertySource(properties = {
        "app.security.admin-bootstrap-login=" + TestJwt.ADMIN_LOGIN,
        "app.security.admin-bootstrap-password=" + TestJwt.ADMIN_PASSWORD
})
class PersonaCorreoReutilizableIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SepomexImportService sepomexImportService;

    private String adminToken;
    private String cp;

    @BeforeEach
    void preparar() throws IOException {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        adminToken = TestJwt.loginAdmin(restTemplate, port);

        cp = "0940" + (System.nanoTime() % 10);
        String csv = """
                codigoPostal|estado|municipio|asentamiento|tipoAsentamiento|idAsentaCpcons
                %s|Ciudad de México|Cuauhtémoc|Roma Norte|Colonia|1
                """.formatted(cp);
        Path archivo = Files.createTempFile("sepomex-correo-reutilizable-it", ".csv");
        Files.writeString(archivo, csv, StandardCharsets.UTF_8);
        sepomexImportService.importar(archivo);
    }

    private Map<String, Object> cuerpoPersonaValida(String correo, String curp) {
        Map<String, Object> direccion = new LinkedHashMap<>();
        direccion.put("calle", "Calle Correo");
        direccion.put("numero", "1");
        direccion.put("colonia", "Roma Norte");
        direccion.put("codigoPostal", cp);
        direccion.put("pais", "MX");

        Map<String, Object> persona = new LinkedHashMap<>();
        persona.put("nombres", "Correo");
        persona.put("apellidos", "Prueba");
        persona.put("fechaNacimiento", "1990-01-01");
        persona.put("sexo", "F");
        persona.put("curp", curp);
        persona.put("rfc", "COPR900101AB1");
        persona.put("correo", correo);
        persona.put("telefono", "5500044455");
        persona.put("direccion", direccion);
        return persona;
    }

    private ResponseEntity<Map> crear(String correo, String curp) {
        return restTemplate.exchange(
                "http://localhost:" + port + "/api/personas", HttpMethod.POST,
                new HttpEntity<>(cuerpoPersonaValida(correo, curp), TestJwt.bearerHeaders(adminToken)), Map.class);
    }

    @Test
    void correoDePersonaEliminadaPuedeSerReutilizadoPorUnaPersonaNueva() {
        String correoCompartido = "reutilizable." + System.nanoTime() + "@example.com";
        ResponseEntity<Map> creada1 = crear(correoCompartido, "REUS900101MDFRZN" + TestUniqueId.homoclave());
        assertThat(creada1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id1 = (String) creada1.getBody().get("id");

        restTemplate.exchange("http://localhost:" + port + "/api/personas/" + id1, HttpMethod.DELETE,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Void.class);

        ResponseEntity<Map> creada2 = crear(correoCompartido, "REUS900101MDFRZN" + TestUniqueId.homoclave());

        assertThat(creada2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void dosPersonasActivasNoPuedenCompartirCorreo() {
        String correoCompartido = "activo.vs.activo." + System.nanoTime() + "@example.com";
        ResponseEntity<Map> creada1 = crear(correoCompartido, "ACVA900101MDFRZN" + TestUniqueId.homoclave());
        assertThat(creada1.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> creada2 = crear(correoCompartido, "ACVB900101MDFRZN" + TestUniqueId.homoclave());

        assertThat(creada2.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(creada2.getBody().get("codigo")).isEqualTo("PERSONA_CORREO_DUPLICADO");
    }
}
