package mx.personas.api.integration;

import mx.personas.api.common.AbstractIntegrationTest;
import mx.personas.api.common.TestJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR contract (contracts/catalogo-importacion-api.md): 400 CATALOGO_ARCHIVO_DEMASIADO_GRANDE
 * cuando el archivo excede el tamaño máximo configurado (research.md §4). Clase IT
 * separada (no @WebMvcTest) porque MockMvc no ejerce el parseo real de multipart del
 * contenedor servlet — MaxUploadSizeExceededException solo se dispara con una petición
 * HTTP real contra el servidor embebido. Límite bajo aislado en su propia clase para no
 * afectar los archivos grandes de otros IT de este dominio.
 */
@TestPropertySource(properties = {
        "app.security.admin-bootstrap-login=" + TestJwt.ADMIN_LOGIN,
        "app.security.admin-bootstrap-password=" + TestJwt.ADMIN_PASSWORD,
        "spring.servlet.multipart.max-file-size=1KB",
        "spring.servlet.multipart.max-request-size=1KB"
})
class CatalogoImportacionTamanoMaximoIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String adminToken;

    @BeforeEach
    void preparar() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        adminToken = TestJwt.loginAdmin(restTemplate, port);
    }

    @Test
    void archivoQueExcedeElTamanoMaximoRegresa400() {
        byte[] contenidoDemasiadoGrande = "x".repeat(4096).getBytes(StandardCharsets.UTF_8);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource archivo = new ByteArrayResource(contenidoDemasiadoGrande) {
            @Override
            public String getFilename() {
                return "catalogo-demasiado-grande.csv";
            }
        };
        body.add("archivo", archivo);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(adminToken);

        ResponseEntity<Map> respuesta = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/codigos-postales/importaciones",
                new HttpEntity<>(body, headers), Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody().get("codigo")).isEqualTo("CATALOGO_ARCHIVO_DEMASIADO_GRANDE");
    }
}
