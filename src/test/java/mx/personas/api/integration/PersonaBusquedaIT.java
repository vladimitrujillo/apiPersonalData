package mx.personas.api.integration;

import mx.personas.api.codigopostal.importer.SepomexImportService;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Busqueda avanzada de personas: regresion de los 3 criterios preexistentes tras el
 * reemplazo de buscarActivas por Specification (Foundational), texto insensible a
 * acentos (US1), combinacion de criterios nuevos (US2) y restriccion de estadoRegistro
 * por rol (US3). No extiende H2 - unaccent solo existe en PostgreSQL real.
 */
@TestPropertySource(properties = {
        "app.security.admin-bootstrap-login=" + TestJwt.ADMIN_LOGIN,
        "app.security.admin-bootstrap-password=" + TestJwt.ADMIN_PASSWORD
})
class PersonaBusquedaIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SepomexImportService sepomexImportService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String capturistaToken;
    private String cpCdmx;
    private String cpJalisco;

    @BeforeEach
    void preparar() throws IOException {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        adminToken = TestJwt.loginAdmin(restTemplate, port);
        TestJwt.sembrarUsuario(usuarioRepository, passwordEncoder,
                TestJwt.CAPTURISTA_LOGIN, TestJwt.CAPTURISTA_PASSWORD, Rol.CAPTURISTA);
        capturistaToken = TestJwt.login(restTemplate, port, TestJwt.CAPTURISTA_LOGIN, TestJwt.CAPTURISTA_PASSWORD);

        cpCdmx = "0930" + TestUniqueId.homoclave().charAt(0);
        cpJalisco = "4430" + TestUniqueId.homoclave().charAt(0);
        String csv = """
                codigoPostal|estado|municipio|asentamiento|tipoAsentamiento|idAsentaCpcons
                %s|Ciudad de México|Cuauhtémoc|Roma Norte|Colonia|1
                %s|Jalisco|Guadalajara|Centro|Colonia|2
                """.formatted(cpCdmx, cpJalisco);
        Path archivo = Files.createTempFile("sepomex-busqueda-it", ".csv");
        Files.writeString(archivo, csv, StandardCharsets.UTF_8);
        sepomexImportService.importar(archivo);
    }

    private String crearPersona(String nombres, String apellidos, LocalDate fechaNacimiento, String sexo,
                                 String correo, String cp) {
        Map<String, Object> direccion = new LinkedHashMap<>();
        direccion.put("calle", "Calle Busqueda");
        direccion.put("numero", "1");
        direccion.put("colonia", cp.equals(cpCdmx) ? "Roma Norte" : "Centro");
        direccion.put("codigoPostal", cp);
        direccion.put("pais", "MX");

        Map<String, Object> persona = new LinkedHashMap<>();
        persona.put("nombres", nombres);
        persona.put("apellidos", apellidos);
        persona.put("fechaNacimiento", fechaNacimiento.toString());
        persona.put("sexo", sexo);
        persona.put("curp", "BUSQ" + fechaNacimiento.toString().replace("-", "").substring(2)
                + (sexo.equals("F") ? "M" : "H") + "DFRZN" + TestUniqueId.homoclave());
        persona.put("rfc", "BUSQ800101AB1");
        persona.put("correo", correo);
        persona.put("telefono", "5500077788");
        persona.put("direccion", direccion);

        ResponseEntity<Map> creado = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas", HttpMethod.POST,
                new HttpEntity<>(persona, TestJwt.bearerHeaders(adminToken)), Map.class);
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) creado.getBody().get("id");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buscar(String token, Map<String, String> params) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString("http://localhost:" + port + "/api/personas");
        params.forEach(uri::queryParam);
        ResponseEntity<Map> respuesta = restTemplate.exchange(
                uri.build().toUri(), HttpMethod.GET, new HttpEntity<>(TestJwt.bearerHeaders(token)), Map.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (List<Map<String, Object>>) respuesta.getBody().get("contenido");
    }

    // ---------- Foundational: regresion de nombre/municipio/estado (sin acentos en el texto) ----------

    @Test
    void regresionNombreParcialMunicipioYEstadoSigueFuncionandoIgualQueAntes() {
        String sufijo = TestUniqueId.homoclave();
        crearPersona("Regresion" + sufijo, "Prueba", LocalDate.of(1990, 1, 1), "F",
                "regresion." + sufijo + "@example.com", cpCdmx);

        assertThat(buscar(adminToken, Map.of("nombre", "regresion" + sufijo)))
                .extracting(p -> p.get("nombres")).contains("Regresion" + sufijo);
        assertThat(buscar(adminToken, Map.of("municipio", "Cuauhtémoc")))
                .isNotEmpty();
        assertThat(buscar(adminToken, Map.of("estado", "Ciudad de México")))
                .isNotEmpty();
    }

    // ---------- US1: texto insensible a acentos ----------

    @Test
    void textoInsensibleAAcentosEncuentraJoseGarcia() {
        String sufijo = TestUniqueId.homoclave();
        crearPersona("José" + sufijo, "García", LocalDate.of(1985, 3, 15), "M",
                "jose.garcia." + sufijo + "@example.com", cpCdmx);

        assertThat(buscar(adminToken, Map.of("nombre", "jose" + sufijo)))
                .extracting(p -> p.get("apellidos")).contains("García");
        assertThat(buscar(adminToken, Map.of("nombre", "garcia")))
                .anySatisfy(p -> assertThat(p.get("nombres")).isEqualTo("José" + sufijo));
    }

    // ---------- US2: combinacion de criterios ----------

    @Test
    void combinarTextoEdadYEstadoGeograficoDevuelveLaInterseccion() {
        String sufijo = TestUniqueId.homoclave();
        LocalDate hoy = LocalDate.now();
        // 30 años exactos, en CDMX
        crearPersona("Interseccion" + sufijo, "Uno", hoy.minusYears(30), "F",
                "interseccion.uno." + sufijo + "@example.com", cpCdmx);
        // mismo nombre, 30 años, pero en Jalisco (no debe aparecer al filtrar por estado=CDMX)
        crearPersona("Interseccion" + sufijo, "Dos", hoy.minusYears(30), "F",
                "interseccion.dos." + sufijo + "@example.com", cpJalisco);
        // mismo nombre y CDMX, pero fuera de rango de edad (60 años)
        crearPersona("Interseccion" + sufijo, "Tres", hoy.minusYears(60), "F",
                "interseccion.tres." + sufijo + "@example.com", cpCdmx);

        List<Map<String, Object>> resultado = buscar(adminToken, Map.of(
                "nombre", "interseccion" + sufijo,
                "edadMinima", "25", "edadMaxima", "35",
                "estado", "Ciudad de México"));

        assertThat(resultado).extracting(p -> p.get("apellidos")).containsExactly("Uno");
    }

    @Test
    void combinarCurpPrefijoFechaRegistroMunicipioYSexoDevuelveLaInterseccion() {
        String sufijo = TestUniqueId.homoclave();
        String id = crearPersona("Combinada" + sufijo, "Prueba", LocalDate.of(1995, 6, 20), "F",
                "combinada." + sufijo + "@example.com", cpCdmx);

        ResponseEntity<Map> obtenida = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas/" + id, HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Map.class);
        String curpCreada = (String) obtenida.getBody().get("curp");
        LocalDate hoy = LocalDate.now();

        List<Map<String, Object>> resultado = buscar(adminToken, Map.of(
                "curpPrefijo", curpCreada.substring(0, 6),
                "fechaRegistroDesde", hoy.toString(), "fechaRegistroHasta", hoy.toString(),
                "municipio", "Cuauhtémoc", "sexo", "F"));

        assertThat(resultado).extracting(p -> p.get("apellidos")).contains("Prueba");
        assertThat(resultado).anySatisfy(p -> assertThat(p.get("curp")).isEqualTo(curpCreada));
    }

    @Test
    void curpPrefijoSinCoincidenciasDevuelvePaginaVaciaSinError() {
        assertThat(buscar(adminToken, Map.of("curpPrefijo", "ZZZZNOEXISTE")))
                .isEmpty();
    }

    @Test
    void ordenarPorFechaNacimientoRespetaLaDireccion() {
        String sufijo = TestUniqueId.homoclave();
        String nombreComun = "Orden" + sufijo;
        crearPersona(nombreComun, "Joven", LocalDate.of(2000, 1, 1), "F",
                "orden.joven." + sufijo + "@example.com", cpCdmx);
        crearPersona(nombreComun, "Mayor", LocalDate.of(1970, 1, 1), "F",
                "orden.mayor." + sufijo + "@example.com", cpCdmx);

        List<Map<String, Object>> ascendente = buscar(adminToken, Map.of(
                "nombre", nombreComun, "ordenarPor", "FECHA_NACIMIENTO", "direccionOrden", "ASC"));
        List<Map<String, Object>> descendente = buscar(adminToken, Map.of(
                "nombre", nombreComun, "ordenarPor", "FECHA_NACIMIENTO", "direccionOrden", "DESC"));

        assertThat(ascendente).extracting(p -> p.get("apellidos")).containsExactly("Mayor", "Joven");
        assertThat(descendente).extracting(p -> p.get("apellidos")).containsExactly("Joven", "Mayor");
    }

    @Test
    void edadMaximaIncluyeAQuienCumpleAnosExactamenteHoy() {
        String sufijo = TestUniqueId.homoclave();
        LocalDate hoy = LocalDate.now();
        crearPersona("Cumpleanos" + sufijo, "Exacto", hoy.minusYears(65), "F",
                "cumpleanos.exacto." + sufijo + "@example.com", cpCdmx);
        // Nacido un año antes que "Exacto" (no un dia antes: nacer un dia antes NO cruza un
        // año completo de edad, ya cumplio 65 ayer y sigue teniendo 65 hoy) - cumple 66 hoy,
        // debe quedar excluido con edadMaxima=65.
        crearPersona("Cumpleanos" + sufijo, "UnAnoMayor", hoy.minusYears(66), "F",
                "cumpleanos.unanio." + sufijo + "@example.com", cpCdmx);

        List<Map<String, Object>> resultado = buscar(adminToken, Map.of(
                "nombre", "cumpleanos" + sufijo, "edadMaxima", "65"));

        assertThat(resultado).extracting(p -> p.get("apellidos")).containsExactly("Exacto");
    }

    // ---------- US3: estadoRegistro solo tiene efecto para ADMIN ----------

    @Test
    void capturistaConEstadoRegistroEliminadasOTodasNuncaVeEliminadas() {
        String sufijo = TestUniqueId.homoclave();
        String id = crearPersona("Eliminada" + sufijo, "Busqueda", LocalDate.of(1992, 2, 2), "F",
                "eliminada.busqueda." + sufijo + "@example.com", cpCdmx);
        restTemplate.exchange("http://localhost:" + port + "/api/personas/" + id, HttpMethod.DELETE,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Void.class);

        assertThat(buscar(capturistaToken, Map.of("nombre", "eliminada" + sufijo, "estadoRegistro", "ELIMINADAS")))
                .isEmpty();
        assertThat(buscar(capturistaToken, Map.of("nombre", "eliminada" + sufijo, "estadoRegistro", "TODAS")))
                .isEmpty();
        assertThat(buscar(adminToken, Map.of("nombre", "eliminada" + sufijo, "estadoRegistro", "ELIMINADAS")))
                .extracting(p -> p.get("apellidos")).contains("Busqueda");
    }
}
