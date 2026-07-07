package mx.personas.api.integration;

import mx.personas.api.codigopostal.importer.SepomexImportService;
import mx.personas.api.common.AbstractIntegrationTest;
import mx.personas.api.common.TestJwt;
import mx.personas.api.common.TestUniqueId;
import mx.personas.api.persona.service.HistorialDiffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * SC-006/FR-010: si falla el guardado de la entrada de historial, la operacion completa
 * se revierte, sin dejar cambios parciales en persona/direccion.
 */
@TestPropertySource(properties = {
        "app.security.admin-bootstrap-login=" + TestJwt.ADMIN_LOGIN,
        "app.security.admin-bootstrap-password=" + TestJwt.ADMIN_PASSWORD
})
class PersonaHistorialRollbackIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SepomexImportService sepomexImportService;

    @MockBean
    private HistorialDiffService historialDiffService;

    private String adminToken;
    private String cp;

    @BeforeEach
    void preparar() throws IOException {
        adminToken = TestJwt.loginAdmin(restTemplate, port);

        cp = "0930" + (System.nanoTime() % 10);
        String csv = """
                codigoPostal|estado|municipio|asentamiento|tipoAsentamiento|idAsentaCpcons
                %s|Ciudad de México|Cuauhtémoc|Roma Norte|Colonia|1
                """.formatted(cp);
        Path archivo = Files.createTempFile("sepomex-rollback-it", ".csv");
        Files.writeString(archivo, csv, StandardCharsets.UTF_8);
        sepomexImportService.importar(archivo);
    }

    @Test
    void siFallaElGuardadoDelHistorialLaCreacionDePersonaSeRevierte() {
        given(historialDiffService.serializarCreacion(any(), any()))
                .willThrow(new RuntimeException("Fallo simulado al calcular el historial"));

        String correo = "rollback." + System.nanoTime() + "@example.com";
        Map<String, Object> direccion = new LinkedHashMap<>();
        direccion.put("calle", "Calle Rollback");
        direccion.put("numero", "1");
        direccion.put("colonia", "Roma Norte");
        direccion.put("codigoPostal", cp);
        direccion.put("pais", "MX");

        Map<String, Object> persona = new LinkedHashMap<>();
        persona.put("nombres", "Rollback");
        persona.put("apellidos", "Prueba");
        persona.put("fechaNacimiento", "1990-01-01");
        persona.put("sexo", "F");
        persona.put("curp", "ROBK900101MDFRZN" + TestUniqueId.homoclave());
        persona.put("rfc", "ROBK900101AB1");
        persona.put("correo", correo);
        persona.put("telefono", "5500099988");
        persona.put("direccion", direccion);

        ResponseEntity<Map> respuesta = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas", HttpMethod.POST,
                new HttpEntity<>(persona, TestJwt.bearerHeaders(adminToken)), Map.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        // La persona NO debe haber quedado creada: un alta posterior con el mismo correo
        // debe ser aceptada (si hubiera quedado a medias, este segundo intento fallaria
        // con 409 PERSONA_CORREO_DUPLICADO).
        given(historialDiffService.serializarCreacion(any(), any())).willCallRealMethod();
        ResponseEntity<Map> segundoIntento = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas", HttpMethod.POST,
                new HttpEntity<>(persona, TestJwt.bearerHeaders(adminToken)), Map.class);
        assertThat(segundoIntento.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
