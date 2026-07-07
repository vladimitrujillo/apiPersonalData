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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US2: la CURP es identidad global y permanente; distingue conflicto contra un registro
 * activo (sin cambio) de conflicto contra uno eliminado (409 accionable) - FR-002 a FR-004.
 */
@TestPropertySource(properties = {
        "app.security.admin-bootstrap-login=" + TestJwt.ADMIN_LOGIN,
        "app.security.admin-bootstrap-password=" + TestJwt.ADMIN_PASSWORD
})
class PersonaCurpGlobalIT extends AbstractIntegrationTest {

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

        cp = "0960" + (System.nanoTime() % 10);
        String csv = """
                codigoPostal|estado|municipio|asentamiento|tipoAsentamiento|idAsentaCpcons
                %s|Ciudad de México|Cuauhtémoc|Roma Norte|Colonia|1
                """.formatted(cp);
        Path archivo = Files.createTempFile("sepomex-curp-global-it", ".csv");
        Files.writeString(archivo, csv, StandardCharsets.UTF_8);
        sepomexImportService.importar(archivo);
    }

    private Map<String, Object> cuerpoPersonaValida(String correo, String curp) {
        Map<String, Object> direccion = new LinkedHashMap<>();
        direccion.put("calle", "Calle CURP");
        direccion.put("numero", "1");
        direccion.put("colonia", "Roma Norte");
        direccion.put("codigoPostal", cp);
        direccion.put("pais", "MX");

        Map<String, Object> persona = new LinkedHashMap<>();
        persona.put("nombres", "Curp");
        persona.put("apellidos", "Prueba");
        persona.put("fechaNacimiento", "1990-01-01");
        persona.put("sexo", "F");
        persona.put("curp", curp);
        persona.put("rfc", "CUPR900101AB1");
        persona.put("correo", correo);
        persona.put("telefono", "5500066677");
        persona.put("direccion", direccion);
        return persona;
    }

    private ResponseEntity<Map> crear(String correo, String curp) {
        return restTemplate.exchange(
                "http://localhost:" + port + "/api/personas", HttpMethod.POST,
                new HttpEntity<>(cuerpoPersonaValida(correo, curp), TestJwt.bearerHeaders(adminToken)), Map.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    void curpDeRegistroEliminadoRegresa409AccionableSinDatosPersonales() {
        String curpCompartida = "GLOB900101HDFRRN0" + (System.nanoTime() % 10);
        ResponseEntity<Map> creada = crear("curp.original." + System.nanoTime() + "@example.com", curpCompartida);
        assertThat(creada.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String idOriginal = (String) creada.getBody().get("id");

        restTemplate.exchange("http://localhost:" + port + "/api/personas/" + idOriginal, HttpMethod.DELETE,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Void.class);

        ResponseEntity<Map> intento = crear("curp.nuevo." + System.nanoTime() + "@example.com", curpCompartida);

        assertThat(intento.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(intento.getBody().get("codigo")).isEqualTo("PERSONA_CURP_ELIMINADA");
        List<Map<String, Object>> detalles = (List<Map<String, Object>>) intento.getBody().get("detalles");
        String motivo = (String) detalles.get(0).get("motivo");
        assertThat(motivo).contains(idOriginal);
        assertThat(motivo).doesNotContain("Curp").doesNotContain("@example.com");
    }

    @SuppressWarnings("unchecked")
    @Test
    void actualizarOtraPersonaConCurpDeRegistroEliminadoRegresaMismo409() {
        String curpCompartida = "GLOB900101HDFRRN1" + (System.nanoTime() % 10);
        ResponseEntity<Map> creada = crear("curp.actualizar.orig." + System.nanoTime() + "@example.com",
                curpCompartida);
        String idOriginal = (String) creada.getBody().get("id");
        restTemplate.exchange("http://localhost:" + port + "/api/personas/" + idOriginal, HttpMethod.DELETE,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Void.class);

        ResponseEntity<Map> otra = crear("curp.actualizar.otra." + System.nanoTime() + "@example.com",
                "GLOB900101HDFRRN2" + (System.nanoTime() % 10));
        String idOtra = (String) otra.getBody().get("id");

        ResponseEntity<Map> actualizacion = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas/" + idOtra, HttpMethod.PATCH,
                new HttpEntity<>(Map.of("curp", curpCompartida), TestJwt.bearerHeaders(adminToken)), Map.class);

        assertThat(actualizacion.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(actualizacion.getBody().get("codigo")).isEqualTo("PERSONA_CURP_ELIMINADA");
    }

    @Test
    void dosPersonasActivasNoPuedenCompartirCurp() {
        String curpCompartida = "GLOB900101HDFRRN3" + (System.nanoTime() % 10);
        ResponseEntity<Map> primera = crear("curp.activa1." + System.nanoTime() + "@example.com", curpCompartida);
        assertThat(primera.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> segunda = crear("curp.activa2." + System.nanoTime() + "@example.com", curpCompartida);

        assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(segunda.getBody().get("codigo")).isEqualTo("PERSONA_CURP_DUPLICADO");
    }

    @Test
    void correoYCurpDeLaMismaPersonaEliminadaResponde409DeCurpNoDeCorreo() {
        String correoCompartido = "correo.y.curp." + System.nanoTime() + "@example.com";
        String curpCompartida = "GLOB900101HDFRRN4" + (System.nanoTime() % 10);
        ResponseEntity<Map> creada = crear(correoCompartido, curpCompartida);
        String idOriginal = (String) creada.getBody().get("id");
        restTemplate.exchange("http://localhost:" + port + "/api/personas/" + idOriginal, HttpMethod.DELETE,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Void.class);

        // El mismo correo Y la misma CURP del registro eliminado: debe ganar el 409 de CURP,
        // ya que el correo por si solo nunca conflictua contra un registro eliminado (D3).
        ResponseEntity<Map> intento = crear(correoCompartido, curpCompartida);

        assertThat(intento.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(intento.getBody().get("codigo")).isEqualTo("PERSONA_CURP_ELIMINADA");
    }
}
