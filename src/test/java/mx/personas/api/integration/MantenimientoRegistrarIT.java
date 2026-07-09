package mx.personas.api.integration;

import mx.personas.api.automovil.repository.AutomovilRepository;
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

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US3: registrar un mantenimiento con sus piezas (FR-012 a FR-021).
 */
@TestPropertySource(properties = {
        "app.security.admin-bootstrap-login=" + TestJwt.ADMIN_LOGIN,
        "app.security.admin-bootstrap-password=" + TestJwt.ADMIN_PASSWORD
})
class MantenimientoRegistrarIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AutomovilRepository automovilRepository;

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

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String crearPersona(String prefijo) {
        String h = TestUniqueId.homoclave();
        Map<String, Object> direccion = new LinkedHashMap<>();
        direccion.put("calle", "Calle Mtto");
        direccion.put("numero", "1");
        direccion.put("colonia", "Centro");
        direccion.put("municipio", "Municipio Test");
        direccion.put("estado", "Estado Test");
        direccion.put("codigoPostal", "00000");
        direccion.put("pais", "US");

        Map<String, Object> persona = new LinkedHashMap<>();
        persona.put("nombres", prefijo);
        persona.put("apellidos", "Test " + h);
        persona.put("fechaNacimiento", LocalDate.of(1990, 1, 1).toString());
        persona.put("sexo", "F");
        // CURP/RFC usan un prefijo fijo de 4 letras (no el nombre de longitud variable) para
        // siempre producir un formato válido de 18/13 caracteres, sin importar el prefijo pedido.
        persona.put("curp", "MTTO900101MDFRZN" + h);
        persona.put("rfc", "MTTO900101AB" + h.charAt(0));
        persona.put("correo", prefijo.toLowerCase() + "." + System.nanoTime() + "@example.com");
        persona.put("telefono", "5500011188");
        persona.put("direccion", direccion);

        ResponseEntity<Map> creado = restTemplate.exchange(url("/api/personas"), HttpMethod.POST,
                new HttpEntity<>(persona, TestJwt.bearerHeaders(adminToken)), Map.class);
        return (String) creado.getBody().get("id");
    }

    private String crearAutomovil(String personaId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("marca", "Nissan");
        body.put("modelo", "Versa");
        body.put("anio", 2022);
        body.put("color", "Rojo");
        body.put("placas", "MTO-" + TestUniqueId.homoclave());

        ResponseEntity<Map> creado = restTemplate.postForEntity(url("/api/personas/" + personaId + "/automoviles"),
                new HttpEntity<>(body, TestJwt.bearerHeaders(capturistaToken)), Map.class);
        return (String) creado.getBody().get("id");
    }

    /** Crea una persona con la profesión "Mecánico" asignada de forma activa (007). */
    private String crearMecanicoElegible() {
        String personaId = crearPersona("Mec");
        ResponseEntity<Map> catalogo = restTemplate.exchange(
                url("/api/profesiones?incluirInactivas=true&size=100"), HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Map.class);
        List<Map<String, Object>> contenido = (List<Map<String, Object>>) catalogo.getBody().get("contenido");
        Long mecanicoProfesionId = contenido.stream()
                .filter(p -> "Mecánico".equals(p.get("nombre")))
                .findFirst()
                .map(p -> ((Number) p.get("id")).longValue())
                .orElseThrow();

        restTemplate.postForEntity(url("/api/personas/" + personaId + "/profesiones"),
                new HttpEntity<>(Map.of("profesionId", mecanicoProfesionId), TestJwt.bearerHeaders(adminToken)),
                Map.class);
        return personaId;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> historialAuditoriaDe(String personaId) {
        ResponseEntity<Map> respuesta = restTemplate.exchange(
                url("/api/personas/" + personaId + "/historial"), HttpMethod.GET,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Map.class);
        return (List<Map<String, Object>>) respuesta.getBody().get("contenido");
    }

    private Map<String, Object> mantenimientoBody(String fecha, int kilometraje, String costoTotal,
                                                     String mecanicoId, List<Map<String, Object>> piezas) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("descripcion", "Servicio");
        body.put("fecha", fecha);
        body.put("kilometraje", kilometraje);
        body.put("costoTotal", costoTotal);
        if (mecanicoId != null) {
            body.put("mecanicoId", mecanicoId);
        }
        if (piezas != null) {
            body.put("piezas", piezas);
        }
        return body;
    }

    @Test
    void registrarConPiezasYMecanicoElegibleYSinNinguno() {
        String personaId = crearPersona("Auto");
        String automovilId = crearAutomovil(personaId);
        String mecanicoId = crearMecanicoElegible();
        int historialAntes = historialAuditoriaDe(personaId).size();

        List<Map<String, Object>> piezas = List.of(
                Map.of("nombre", "Filtro", "costo", "100.00"),
                Map.of("nombre", "Aceite", "numeroParte", "AC-1", "costo", "400.00"));
        ResponseEntity<Map> conPiezas = restTemplate.postForEntity(
                url("/api/automoviles/" + automovilId + "/mantenimientos"),
                new HttpEntity<>(mantenimientoBody(LocalDate.now().toString(), 1000, "500.00", mecanicoId, piezas),
                        TestJwt.bearerHeaders(capturistaToken)),
                Map.class);
        assertThat(conPiezas.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat((List<?>) conPiezas.getBody().get("piezas")).hasSize(2);
        assertThat(((Map<?, ?>) conPiezas.getBody().get("mecanico")).get("id")).isEqualTo(mecanicoId);

        // FR-028, hallazgo F1 de /speckit-converge (fortalecido tras hallazgo F1 de una
        // tercera corrida de /speckit-converge, T062): comparar tamaño antes/después —
        // `crearAutomovil` ya deja su propia entrada MODIFICACION, así que `anySatisfy`
        // no detectaría una regresión en el registro del mantenimiento en sí.
        assertThat(historialAuditoriaDe(personaId)).hasSizeGreaterThan(historialAntes);

        ResponseEntity<Map> sinNinguno = restTemplate.postForEntity(
                url("/api/automoviles/" + automovilId + "/mantenimientos"),
                new HttpEntity<>(mantenimientoBody(LocalDate.now().plusDays(0).toString(), 2000, "0", null, null),
                        TestJwt.bearerHeaders(capturistaToken)),
                Map.class);
        assertThat(sinNinguno.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(sinNinguno.getBody().get("mecanico")).isNull();
        assertThat((List<?>) sinNinguno.getBody().get("piezas")).isEmpty();
    }

    @Test
    void fechaFuturaCostoNegativoOKilometrajeNegativoResponden400() {
        String personaId = crearPersona("Val");
        String automovilId = crearAutomovil(personaId);

        ResponseEntity<Map> fechaFutura = restTemplate.postForEntity(
                url("/api/automoviles/" + automovilId + "/mantenimientos"),
                new HttpEntity<>(mantenimientoBody(LocalDate.now().plusDays(5).toString(), 1000, "10.00", null, null),
                        TestJwt.bearerHeaders(capturistaToken)),
                Map.class);
        assertThat(fechaFutura.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> costoNegativo = restTemplate.postForEntity(
                url("/api/automoviles/" + automovilId + "/mantenimientos"),
                new HttpEntity<>(mantenimientoBody(LocalDate.now().toString(), 1000, "-1", null, null),
                        TestJwt.bearerHeaders(capturistaToken)),
                Map.class);
        assertThat(costoNegativo.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> kmNegativo = restTemplate.postForEntity(
                url("/api/automoviles/" + automovilId + "/mantenimientos"),
                new HttpEntity<>(mantenimientoBody(LocalDate.now().toString(), -5, "10.00", null, null),
                        TestJwt.bearerHeaders(capturistaToken)),
                Map.class);
        assertThat(kmNegativo.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // FR-015, hallazgo F2 de /speckit-converge: T036 prometía este escenario y no
        // quedó cubierto — el costo de una pieza individual también debe ser >= 0.
        Map<String, Object> bodyConPiezaNegativa = mantenimientoBody(LocalDate.now().toString(), 1000, "10.00",
                null, null);
        bodyConPiezaNegativa.put("piezas", List.of(Map.of("nombre", "Filtro", "costo", "-5.00")));
        ResponseEntity<Map> piezaCostoNegativo = restTemplate.postForEntity(
                url("/api/automoviles/" + automovilId + "/mantenimientos"),
                new HttpEntity<>(bodyConPiezaNegativa, TestJwt.bearerHeaders(capturistaToken)), Map.class);
        assertThat(piezaCostoNegativo.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void kilometrajeMenorAlMasRecienteResponde409() {
        String personaId = crearPersona("Kmt");
        String automovilId = crearAutomovil(personaId);
        restTemplate.postForEntity(url("/api/automoviles/" + automovilId + "/mantenimientos"),
                new HttpEntity<>(mantenimientoBody(LocalDate.now().toString(), 5000, "0", null, null),
                        TestJwt.bearerHeaders(capturistaToken)),
                Map.class);

        ResponseEntity<Map> respuesta = restTemplate.postForEntity(
                url("/api/automoviles/" + automovilId + "/mantenimientos"),
                new HttpEntity<>(mantenimientoBody(LocalDate.now().toString(), 1000, "0", null, null),
                        TestJwt.bearerHeaders(capturistaToken)),
                Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody().get("codigo")).isEqualTo("KILOMETRAJE_INCONSISTENTE");
    }

    @Test
    void mecanicoInexistenteResponde400() {
        String personaId = crearPersona("Mno");
        String automovilId = crearAutomovil(personaId);

        ResponseEntity<Map> respuesta = restTemplate.postForEntity(
                url("/api/automoviles/" + automovilId + "/mantenimientos"),
                new HttpEntity<>(mantenimientoBody(LocalDate.now().toString(), 1000, "0",
                        UUID.randomUUID().toString(), null), TestJwt.bearerHeaders(capturistaToken)),
                Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody().get("codigo")).isEqualTo("MECANICO_NO_ENCONTRADO");
    }

    @Test
    void mecanicoEliminadoLogicamenteResponde409() {
        String personaId = crearPersona("Meli");
        String automovilId = crearAutomovil(personaId);
        String mecanicoId = crearMecanicoElegible();
        restTemplate.exchange(url("/api/personas/" + mecanicoId), HttpMethod.DELETE,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Void.class);

        ResponseEntity<Map> respuesta = restTemplate.postForEntity(
                url("/api/automoviles/" + automovilId + "/mantenimientos"),
                new HttpEntity<>(mantenimientoBody(LocalDate.now().toString(), 1000, "0", mecanicoId, null),
                        TestJwt.bearerHeaders(capturistaToken)),
                Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody().get("codigo")).isEqualTo("MECANICO_ELIMINADO");
    }

    @Test
    void mecanicoSinProfesionActivaResponde409() {
        String personaId = crearPersona("Msp");
        String automovilId = crearAutomovil(personaId);
        String personaSinProfesion = crearPersona("NoMec");

        ResponseEntity<Map> respuesta = restTemplate.postForEntity(
                url("/api/automoviles/" + automovilId + "/mantenimientos"),
                new HttpEntity<>(mantenimientoBody(LocalDate.now().toString(), 1000, "0", personaSinProfesion, null),
                        TestJwt.bearerHeaders(capturistaToken)),
                Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody().get("codigo")).isEqualTo("MECANICO_SIN_PROFESION_ACTIVA");
    }

    @Test
    void automovilEliminadoLogicamenteResponde409() {
        String personaId = crearPersona("AutEli");
        String automovilId = crearAutomovil(personaId);
        var automovil = automovilRepository.findById(UUID.fromString(automovilId)).orElseThrow();
        automovil.desactivar();
        automovilRepository.saveAndFlush(automovil);

        ResponseEntity<Map> respuesta = restTemplate.postForEntity(
                url("/api/automoviles/" + automovilId + "/mantenimientos"),
                new HttpEntity<>(mantenimientoBody(LocalDate.now().toString(), 1000, "0", null, null),
                        TestJwt.bearerHeaders(capturistaToken)),
                Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody().get("codigo")).isEqualTo("AUTOMOVIL_ELIMINADO");
    }

    @Test
    void personaDuenaDelAutomovilEliminadaLogicamenteResponde409() {
        String personaId = crearPersona("DueEli");
        String automovilId = crearAutomovil(personaId);
        restTemplate.exchange(url("/api/personas/" + personaId), HttpMethod.DELETE,
                new HttpEntity<>(TestJwt.bearerHeaders(adminToken)), Void.class);

        ResponseEntity<Map> respuesta = restTemplate.postForEntity(
                url("/api/automoviles/" + automovilId + "/mantenimientos"),
                new HttpEntity<>(mantenimientoBody(LocalDate.now().toString(), 1000, "0", null, null),
                        TestJwt.bearerHeaders(capturistaToken)),
                Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody().get("codigo")).isEqualTo("PERSONA_ELIMINADA");
    }
}
