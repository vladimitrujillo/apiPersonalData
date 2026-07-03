package mx.personas.api.integration;

import mx.personas.api.codigopostal.importer.SepomexImportService;
import mx.personas.api.common.AbstractIntegrationTest;
import mx.personas.api.common.TestApiKey;
import mx.personas.api.common.TestUniqueId;
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
 * Verifica que las personas eliminadas logicamente nunca aparecen en ningun listado
 * (US2, Acceptance Scenario 4).
 */
@TestPropertySource(properties = "app.security.api-key=" + TestApiKey.VALOR)
class PersonaListIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SepomexImportService sepomexImportService;

    private String baseUrl() {
        return "http://localhost:" + port + "/api/personas";
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(TestApiKey.HEADER, TestApiKey.VALOR);
        return headers;
    }

    /**
     * Siembra un CP unico con un municipio unico en el catalogo, para poder filtrar por
     * ese municipio sin colisionar con datos de otras pruebas en la misma base compartida.
     * Regresa el codigo postal sembrado.
     */
    private String sembrarCatalogoConMunicipioUnico(String municipioUnico) throws IOException {
        String cp = "0800" + (System.nanoTime() % 10);
        String csv = """
                codigoPostal|estado|municipio|asentamiento|tipoAsentamiento|idAsentaCpcons
                %s|Ciudad de México|%s|Centro|Colonia|1
                """.formatted(cp, municipioUnico);
        Path archivo = Files.createTempFile("sepomex-list-it", ".csv");
        Files.writeString(archivo, csv, StandardCharsets.UTF_8);
        sepomexImportService.importar(archivo);
        return cp;
    }

    private Map<String, Object> cuerpoPersonaValida(String correoUnico, String codigoPostal) {
        Map<String, Object> direccion = new LinkedHashMap<>();
        direccion.put("calle", "Calle de Prueba");
        direccion.put("numero", "1");
        direccion.put("colonia", "Centro");
        direccion.put("codigoPostal", codigoPostal);
        direccion.put("pais", "MX");

        Map<String, Object> persona = new LinkedHashMap<>();
        persona.put("nombres", "Lista");
        persona.put("apellidos", "Prueba");
        persona.put("fechaNacimiento", "1990-01-01");
        persona.put("sexo", "F");
        persona.put("curp", "LIPR900101MDFRZN" + TestUniqueId.homoclave());
        persona.put("rfc", "LIPR900101AB1");
        persona.put("correo", correoUnico);
        persona.put("telefono", "5500000000");
        persona.put("direccion", direccion);
        return persona;
    }

    @SuppressWarnings("unchecked")
    private String crearPersona(String correo, String codigoPostal) {
        ResponseEntity<Map> creado = restTemplate.exchange(
                baseUrl(), HttpMethod.POST, new HttpEntity<>(cuerpoPersonaValida(correo, codigoPostal), headers()),
                Map.class);
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) creado.getBody().get("id");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listar(String queryParams) {
        ResponseEntity<Map> respuesta = restTemplate.exchange(
                baseUrl() + queryParams, HttpMethod.GET, new HttpEntity<>(headers()), Map.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (List<Map<String, Object>>) respuesta.getBody().get("contenido");
    }

    @Test
    void personaEliminadaLogicamenteNoApareceEnNingunListado() throws IOException {
        String municipioUnico = "MunicipioListTest" + System.nanoTime();
        String codigoPostal = sembrarCatalogoConMunicipioUnico(municipioUnico);
        String correo = "lista." + System.nanoTime() + "@example.com";
        String id = crearPersona(correo, codigoPostal);

        List<Map<String, Object>> antesDeEliminar = listar("?municipio=" + municipioUnico);
        assertThat(antesDeEliminar).extracting(p -> p.get("id")).contains(id);

        ResponseEntity<Void> eliminado = restTemplate.exchange(
                baseUrl() + "/" + id, HttpMethod.DELETE, new HttpEntity<>(headers()), Void.class);
        assertThat(eliminado.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        List<Map<String, Object>> sinFiltros = listar("");
        assertThat(sinFiltros).extracting(p -> p.get("id")).doesNotContain(id);

        List<Map<String, Object>> conFiltroMunicipio = listar("?municipio=" + municipioUnico);
        assertThat(conFiltroMunicipio).isEmpty();
    }

    @Test
    void paginaFueraDeRangoRegresaListaVaciaSinError() {
        List<Map<String, Object>> resultado = listar("?page=9999&size=20");
        assertThat(resultado).isEmpty();
    }
}
