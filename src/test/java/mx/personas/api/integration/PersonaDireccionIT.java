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
 * US5: al registrar/actualizar la direccion de una persona con un CP mexicano valido, el
 * sistema autocompleta municipio/estado y acepta una colonia de la lista de ese CP
 * (FR-019, FR-020, Acceptance Scenarios 1 y 2).
 */
@TestPropertySource(properties = "app.security.api-key=" + TestApiKey.VALOR)
class PersonaDireccionIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SepomexImportService sepomexImportService;

    private String cp;

    @BeforeEach
    void sembrarCatalogo() throws IOException {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        cp = "0850" + (System.nanoTime() % 10);
        String csv = """
                codigoPostal,estado,municipio,asentamiento,tipoAsentamiento,idAsentaCpcons
                %s,Jalisco,Guadalajara,Americana,Colonia,1
                %s,Jalisco,Guadalajara,Chapultepec,Fraccionamiento,2
                """.formatted(cp, cp);
        Path archivo = Files.createTempFile("sepomex-direccion-it", ".csv");
        Files.writeString(archivo, csv, StandardCharsets.UTF_8);
        sepomexImportService.importar(archivo);
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(TestApiKey.HEADER, TestApiKey.VALOR);
        return headers;
    }

    private Map<String, Object> cuerpoPersona(String correo, String colonia) {
        Map<String, Object> direccion = new LinkedHashMap<>();
        direccion.put("calle", "Av. Chapultepec");
        direccion.put("numero", "500");
        direccion.put("colonia", colonia);
        direccion.put("codigoPostal", cp);
        direccion.put("pais", "MX");

        Map<String, Object> persona = new LinkedHashMap<>();
        persona.put("nombres", "Direccion");
        persona.put("apellidos", "Test");
        persona.put("fechaNacimiento", "1990-01-01");
        persona.put("sexo", "M");
        persona.put("curp", "DITE900101HDFRZN" + TestUniqueId.homoclave());
        persona.put("rfc", "DITE900101AB1");
        persona.put("correo", correo);
        persona.put("telefono", "5511122233");
        persona.put("direccion", direccion);
        return persona;
    }

    @Test
    void crearConCpValidoAutocompletaMunicipioYEstado() {
        String correo = "direccion.crear." + System.nanoTime() + "@example.com";
        ResponseEntity<Map> respuesta = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas", HttpMethod.POST,
                new HttpEntity<>(cuerpoPersona(correo, "Americana"), headers()), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> direccion = (Map<?, ?>) respuesta.getBody().get("direccion");
        assertThat(direccion.get("municipio")).isEqualTo("Guadalajara");
        assertThat(direccion.get("estado")).isEqualTo("Jalisco");
        assertThat(direccion.get("colonia")).isEqualTo("Americana");
    }

    @Test
    void aceptaColoniaQuePerteneceALaListaDelCp() {
        String correo = "direccion.colonia." + System.nanoTime() + "@example.com";
        ResponseEntity<Map> respuesta = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas", HttpMethod.POST,
                new HttpEntity<>(cuerpoPersona(correo, "Chapultepec"), headers()), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> direccion = (Map<?, ?>) respuesta.getBody().get("direccion");
        assertThat(direccion.get("colonia")).isEqualTo("Chapultepec");
    }

    @Test
    void actualizarDireccionConCpValidoAutocompleta() {
        String correo = "direccion.update." + System.nanoTime() + "@example.com";
        ResponseEntity<Map> creado = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas", HttpMethod.POST,
                new HttpEntity<>(cuerpoPersona(correo, "Americana"), headers()), Map.class);
        String id = (String) creado.getBody().get("id");

        Map<String, Object> nuevaDireccion = new LinkedHashMap<>();
        nuevaDireccion.put("colonia", "Chapultepec");
        Map<String, Object> actualizacion = Map.of("direccion", nuevaDireccion);

        ResponseEntity<Map> actualizado = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas/" + id, HttpMethod.PATCH,
                new HttpEntity<>(actualizacion, headers()), Map.class);

        assertThat(actualizado.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> direccion = (Map<?, ?>) actualizado.getBody().get("direccion");
        assertThat(direccion.get("colonia")).isEqualTo("Chapultepec");
        assertThat(direccion.get("municipio")).isEqualTo("Guadalajara");
    }
}
