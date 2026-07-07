package mx.personas.api.integration;

import mx.personas.api.codigopostal.model.CatalogoImportacion;
import mx.personas.api.codigopostal.repository.CatalogoImportacionRepository;
import mx.personas.api.codigopostal.repository.CpCatalogoRepository;
import mx.personas.api.common.AbstractIntegrationTest;
import mx.personas.api.common.TestJwt;
import mx.personas.api.common.TestUniqueId;
import mx.personas.api.usuario.model.Rol;
import mx.personas.api.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US2: disparo manual con resultado inmediato (FR-004 a FR-006, FR-009, FR-010, FR-013).
 */
@TestPropertySource(properties = {
        "app.security.admin-bootstrap-login=" + TestJwt.ADMIN_LOGIN,
        "app.security.admin-bootstrap-password=" + TestJwt.ADMIN_PASSWORD
})
class CatalogoImportacionManualIT extends AbstractIntegrationTest {

    private static final String URL = "/api/codigos-postales/importaciones";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CpCatalogoRepository cpCatalogoRepository;

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

    private String url() {
        return "http://localhost:" + port + URL;
    }

    private ResponseEntity<Map> subirArchivo(String token, String nombreArchivo, String contenido) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource archivo = new ByteArrayResource(contenido.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return nombreArchivo;
            }
        };
        body.add("archivo", archivo);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token);

        return restTemplate.postForEntity(url(), new HttpEntity<>(body, headers), Map.class);
    }

    @Test
    void capturistaNoPuedeDispararLaImportacionManual() {
        ResponseEntity<Map> respuesta = subirArchivo(capturistaToken, "catalogo.csv",
                "codigoPostal|estado|municipio|asentamiento|tipoAsentamiento|idAsentaCpcons\n");
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminSubeArchivoValidoYRecibeElResumenLaSegundaVezSinCambios() {
        String cp = "0950" + TestUniqueId.homoclave().charAt(0);
        String csv = """
                codigoPostal|estado|municipio|asentamiento|tipoAsentamiento|idAsentaCpcons
                %s|Ciudad de México|Iztapalapa|Barrio Manual|Colonia|1
                """.formatted(cp);

        ResponseEntity<Map> primera = subirArchivo(adminToken, "catalogo-manual.csv", csv);
        assertThat(primera.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(primera.getBody().get("insertados")).isEqualTo(1);
        assertThat(cpCatalogoRepository.findByCodigoPostal(cp)).hasSize(1);

        ResponseEntity<Map> segunda = subirArchivo(adminToken, "catalogo-manual.csv", csv);
        assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(segunda.getBody().get("insertados")).isEqualTo(0);
        assertThat(segunda.getBody().get("sinCambio")).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void filaConCampoInvalidoQuedaEnDetallesRechazadosYElRestoSeImporta() {
        String cpValido = "0951" + TestUniqueId.homoclave().charAt(0);
        String csv = """
                codigoPostal|estado|municipio|asentamiento|tipoAsentamiento|idAsentaCpcons
                %s|Ciudad de México|Iztapalapa|Barrio Válido|Colonia|1
                XXXXX|Ciudad de México|Iztapalapa|Barrio Inválido|Colonia|2
                """.formatted(cpValido);

        ResponseEntity<Map> respuesta = subirArchivo(adminToken, "catalogo-mixto.csv", csv);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody().get("insertados")).isEqualTo(1);
        assertThat(respuesta.getBody().get("rechazados")).isEqualTo(1);
        List<String> detalles = (List<String>) respuesta.getBody().get("detallesRechazados");
        assertThat(detalles).hasSize(1);
        assertThat(cpCatalogoRepository.findByCodigoPostal(cpValido)).hasSize(1);
    }

    @Test
    void archivoCorruptoRegresa400YQuedaComoErrorEnBitacoraSinTocarElCatalogo() {
        ResponseEntity<Map> respuesta = subirArchivo(adminToken, "catalogo-corrupto.csv",
                "esto no es un catálogo válido en absoluto");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody().get("codigo")).isEqualTo("CATALOGO_ARCHIVO_INVALIDO");

        List<CatalogoImportacion> corridas = catalogoImportacionRepository
                .findAllByOrderByFechaInicioDesc(PageRequest.of(0, 20)).getContent();
        assertThat(corridas).anySatisfy(c ->
                assertThat(c.getEstado()).isEqualTo(CatalogoImportacion.EstadoImportacion.ERROR));
    }

    @Test
    @SuppressWarnings("unchecked")
    void dosSubidasCasiSimultaneasSoloUnaTieneExitoLaOtraRegresa409YQuedaEnLaBitacora() throws Exception {
        String cp1 = "0952" + TestUniqueId.homoclave().charAt(0);
        String cp2 = "0953" + TestUniqueId.homoclave().charAt(0);
        String csv1 = csvGrande(cp1);
        String csv2 = csvGrande(cp2);
        String nombreRechazado = "concurrente-rechazado-" + TestUniqueId.homoclave() + ".csv";

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<Map>> f1 = executor.submit(() -> subirArchivo(adminToken, "concurrente-1.csv", csv1));
            Future<ResponseEntity<Map>> f2 = executor.submit(() -> subirArchivo(adminToken, nombreRechazado, csv2));

            ResponseEntity<Map> r1 = f1.get(30, TimeUnit.SECONDS);
            ResponseEntity<Map> r2 = f2.get(30, TimeUnit.SECONDS);

            List<HttpStatus> estados = List.of(
                    HttpStatus.valueOf(r1.getStatusCode().value()), HttpStatus.valueOf(r2.getStatusCode().value()));
            assertThat(estados).containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);

            String archivoRechazado = r1.getStatusCode() == HttpStatus.CONFLICT ? "concurrente-1.csv" : nombreRechazado;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(adminToken);
            ResponseEntity<Map> bitacora = restTemplate.exchange(
                    "http://localhost:" + port + "/api/codigos-postales/importaciones?size=50",
                    org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            List<Map<String, Object>> contenido = (List<Map<String, Object>>) bitacora.getBody().get("contenido");

            assertThat(contenido).anySatisfy(c -> {
                assertThat(c.get("archivo")).isEqualTo(archivoRechazado);
                assertThat(c.get("estado")).isEqualTo("RECHAZADA_CONCURRENCIA");
            });
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void lecturaDeCodigoPostalMientrasUnaImportacionGrandeEstaEnCursoRespondeConNormalidad() throws Exception {
        String cpConsultado = "0954" + TestUniqueId.homoclave().charAt(0);
        // Precarga y deja el CP en cache antes de que arranque la importacion concurrente.
        subirArchivo(adminToken, "precarga.csv", csvGrande(cpConsultado));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        restTemplate.exchange("http://localhost:" + port + "/api/codigos-postales/" + cpConsultado,
                org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        String cpNuevoEnArchivoGrande = "0955" + TestUniqueId.homoclave().charAt(0);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<Map>> importacion = executor.submit(
                    () -> subirArchivo(adminToken, "grande-concurrente.csv", csvGrande(cpNuevoEnArchivoGrande)));

            Thread.sleep(20);
            ResponseEntity<Map> lectura = restTemplate.exchange(
                    "http://localhost:" + port + "/api/codigos-postales/" + cpConsultado,
                    org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            assertThat(lectura.getStatusCode()).isEqualTo(HttpStatus.OK);
            importacion.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    /** Archivo con suficientes filas para que su importacion tome un tiempo perceptible. */
    private String csvGrande(String cpBase) {
        StringBuilder sb = new StringBuilder(
                "codigoPostal|estado|municipio|asentamiento|tipoAsentamiento|idAsentaCpcons\n");
        sb.append(cpBase).append("|Ciudad de México|Iztapalapa|Barrio Base|Colonia|1\n");
        for (int i = 0; i < 8000; i++) {
            sb.append("09999|Ciudad de México|Iztapalapa|Barrio Relleno ").append(i)
                    .append("|Colonia|").append(1000 + i).append('\n');
        }
        return sb.toString();
    }
}
