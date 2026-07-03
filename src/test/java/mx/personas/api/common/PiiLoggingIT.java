package mx.personas.api.common;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que ningun log generado durante un flujo de creacion/consulta de persona
 * contiene datos personales en texto plano (Principio III de la constitucion: Privacidad
 * por Diseno).
 */
@TestPropertySource(properties = "app.security.api-key=" + TestApiKey.VALOR)
class PiiLoggingIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void engancharAppender() {
        appender = new ListAppender<>();
        appender.start();
        rootLogger().addAppender(appender);
    }

    @AfterEach
    void desengancharAppender() {
        rootLogger().detachAppender(appender);
    }

    private Logger rootLogger() {
        return (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(TestApiKey.HEADER, TestApiKey.VALOR);
        return headers;
    }

    @Test
    void ningunLogContieneCurpCorreoOTelefonoEnTextoPlano() {
        String correo = "pii.test." + System.nanoTime() + "@example.com";
        String curp = "PIIT900101MDFRZN" + TestUniqueId.homoclave();
        String telefono = "5599988877";

        Map<String, Object> direccion = new LinkedHashMap<>();
        direccion.put("calle", "Calle PII");
        direccion.put("numero", "1");
        direccion.put("colonia", "Cualquiera");
        direccion.put("municipio", "Cualquiera");
        direccion.put("estado", "Cualquiera");
        direccion.put("codigoPostal", "99999");
        direccion.put("pais", "ZZ");

        Map<String, Object> persona = new LinkedHashMap<>();
        persona.put("nombres", "PII");
        persona.put("apellidos", "Test");
        persona.put("fechaNacimiento", "1990-01-01");
        persona.put("sexo", "F");
        persona.put("curp", curp);
        persona.put("rfc", "PIIT900101AB1");
        persona.put("correo", correo);
        persona.put("telefono", telefono);
        persona.put("direccion", direccion);

        ResponseEntity<Map> creado = restTemplate.exchange(
                "http://localhost:" + port + "/api/personas", HttpMethod.POST,
                new HttpEntity<>(persona, headers()), Map.class);
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = (String) creado.getBody().get("id");

        restTemplate.exchange("http://localhost:" + port + "/api/personas/" + id,
                HttpMethod.GET, new HttpEntity<>(headers()), Map.class);

        String todosLosMensajes = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);

        assertThat(todosLosMensajes).doesNotContain(curp);
        assertThat(todosLosMensajes).doesNotContain(correo);
        assertThat(todosLosMensajes).doesNotContain(telefono);
    }
}
