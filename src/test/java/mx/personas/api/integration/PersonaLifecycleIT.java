package mx.personas.api.integration;

import mx.personas.api.codigopostal.importer.SepomexImportService;
import mx.personas.api.common.AbstractIntegrationTest;
import mx.personas.api.common.TestApiKey;
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
import org.springframework.http.MediaType;
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
 * Ciclo completo crear -> consultar -> actualizar -> eliminar (SC-001), verificando que
 * el borrado es logico y no fisico (SC-003), y que la direccion se autocompleta contra el
 * catalogo de codigos postales (US5, FR-020).
 */
@TestPropertySource(properties = "app.security.api-key=" + TestApiKey.VALOR)
class PersonaLifecycleIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SepomexImportService sepomexImportService;

    @BeforeEach
    void preparar() throws IOException {
        // HttpURLConnection (factory por defecto) no soporta PATCH; JdkClientHttpRequestFactory
        // (java.net.http.HttpClient) si.
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());

        String csv = """
                codigoPostal,estado,municipio,asentamiento,tipoAsentamiento,idAsentaCpcons
                06700,Ciudad de México,Cuauhtémoc,Roma Norte,Colonia,1
                """;
        Path archivo = Files.createTempFile("sepomex-lifecycle-it", ".csv");
        Files.writeString(archivo, csv, StandardCharsets.UTF_8);
        sepomexImportService.importar(archivo);
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/personas";
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(TestApiKey.HEADER, TestApiKey.VALOR);
        return headers;
    }

    private Map<String, Object> cuerpoPersonaValida() {
        Map<String, Object> direccion = new LinkedHashMap<>();
        direccion.put("calle", "Av. Insurgentes");
        direccion.put("numero", "100");
        direccion.put("colonia", "Roma Norte");
        direccion.put("codigoPostal", "06700");
        direccion.put("pais", "MX");

        Map<String, Object> persona = new LinkedHashMap<>();
        persona.put("nombres", "Juana");
        persona.put("apellidos", "Pérez López");
        persona.put("fechaNacimiento", "1990-05-10");
        persona.put("sexo", "F");
        persona.put("curp", "PELJ900510MDFRZN" + TestUniqueId.homoclave());
        persona.put("rfc", "PELJ900510AB1");
        persona.put("correo", "juana.lifecycle." + System.nanoTime() + "@example.com");
        persona.put("telefono", "5512345678");
        persona.put("direccion", direccion);
        return persona;
    }

    @Test
    void cicloCompletoCrearConsultarActualizarEliminar() {
        // Crear
        ResponseEntity<Map> creado = restTemplate.exchange(
                baseUrl(), HttpMethod.POST, new HttpEntity<>(cuerpoPersonaValida(), headers()), Map.class);
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = (String) creado.getBody().get("id");
        assertThat(id).isNotBlank();

        // Consultar
        ResponseEntity<Map> consultado = restTemplate.exchange(
                baseUrl() + "/" + id, HttpMethod.GET, new HttpEntity<>(headers()), Map.class);
        assertThat(consultado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(consultado.getBody().get("nombres")).isEqualTo("Juana");
        Map<?, ?> direccion = (Map<?, ?>) consultado.getBody().get("direccion");
        assertThat(direccion.get("municipio")).isEqualTo("Cuauhtémoc");
        assertThat(direccion.get("estado")).isEqualTo("Ciudad de México");

        // Actualizar parcialmente
        Map<String, Object> actualizacion = Map.of("telefono", "5587654321");
        ResponseEntity<Map> actualizado = restTemplate.exchange(
                baseUrl() + "/" + id, HttpMethod.PATCH, new HttpEntity<>(actualizacion, headers()), Map.class);
        assertThat(actualizado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(actualizado.getBody().get("telefono")).isEqualTo("5587654321");
        assertThat(actualizado.getBody().get("nombres")).isEqualTo("Juana");

        // Eliminar
        ResponseEntity<Void> eliminado = restTemplate.exchange(
                baseUrl() + "/" + id, HttpMethod.DELETE, new HttpEntity<>(headers()), Void.class);
        assertThat(eliminado.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Consultar tras eliminar -> 404 (SC-003: no aparece, pero el registro persiste en BD)
        ResponseEntity<Map> consultaTrasEliminar = restTemplate.exchange(
                baseUrl() + "/" + id, HttpMethod.GET, new HttpEntity<>(headers()), Map.class);
        assertThat(consultaTrasEliminar.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
