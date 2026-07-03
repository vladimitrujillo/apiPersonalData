package mx.personas.api.persona;

import mx.personas.api.common.TestApiKey;
import mx.personas.api.common.error.DuplicateFieldException;
import mx.personas.api.common.error.ErrorCode;
import mx.personas.api.common.error.FormatoInvalidoException;
import mx.personas.api.persona.controller.PersonaController;
import mx.personas.api.persona.dto.DireccionResponseDTO;
import mx.personas.api.persona.dto.PersonaResponseDTO;
import mx.personas.api.persona.service.PersonaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PersonaController.class)
@TestPropertySource(properties = "app.security.api-key=" + TestApiKey.VALOR)
class PersonaControllerCreateTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PersonaService personaService;

    private String cuerpoValido() {
        return """
                {
                  "nombres": "Juana",
                  "apellidos": "Pérez López",
                  "fechaNacimiento": "1990-05-10",
                  "sexo": "F",
                  "curp": "PELJ900510MDFRZN09",
                  "rfc": "PELJ900510AB1",
                  "correo": "juana.perez@example.com",
                  "telefono": "5512345678",
                  "direccion": {
                    "calle": "Av. Insurgentes",
                    "numero": "100",
                    "colonia": "Roma Norte",
                    "codigoPostal": "06700",
                    "pais": "MX"
                  }
                }
                """;
    }

    @Test
    void creaPersonaValidaRegresa201() throws Exception {
        DireccionResponseDTO direccion = new DireccionResponseDTO(
                "Av. Insurgentes", "100", "Roma Norte", "Cuauhtémoc", "Ciudad de México", "06700", "MX");
        PersonaResponseDTO respuesta = new PersonaResponseDTO(
                UUID.randomUUID(), "Juana", "Pérez López", LocalDate.of(1990, 5, 10), "F",
                "PELJ900510MDFRZN09", "PELJ900510AB1", "juana.perez@example.com", "5512345678", direccion);
        given(personaService.crear(any())).willReturn(respuesta);

        mockMvc.perform(post("/api/personas")
                        .header(TestApiKey.HEADER, TestApiKey.VALOR)
                        .contentType("application/json")
                        .content(cuerpoValido()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.correo").value("juana.perez@example.com"))
                .andExpect(jsonPath("$.direccion.municipio").value("Cuauhtémoc"));
    }

    @Test
    void correoConFormatoInvalidoRegresa400() throws Exception {
        String cuerpo = cuerpoValido().replace("juana.perez@example.com", "no-es-un-correo");

        mockMvc.perform(post("/api/personas")
                        .header(TestApiKey.HEADER, TestApiKey.VALOR)
                        .contentType("application/json")
                        .content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
                .andExpect(jsonPath("$.detalles[?(@.campo == 'correo')]").exists());
    }

    @Test
    void telefonoConFormatoInvalidoRegresa400() throws Exception {
        String cuerpo = cuerpoValido().replace("5512345678", "123");

        mockMvc.perform(post("/api/personas")
                        .header(TestApiKey.HEADER, TestApiKey.VALOR)
                        .contentType("application/json")
                        .content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
                .andExpect(jsonPath("$.detalles[?(@.campo == 'telefono')]").exists());
    }

    @Test
    void fechaNacimientoFuturaRegresa400() throws Exception {
        given(personaService.crear(any())).willThrow(
                new FormatoInvalidoException(ErrorCode.FECHA_NACIMIENTO_FUTURA, "fechaNacimiento",
                        "La fecha de nacimiento no puede ser posterior a la fecha actual"));

        String cuerpo = cuerpoValido().replace("1990-05-10", "2999-01-01");

        mockMvc.perform(post("/api/personas")
                        .header(TestApiKey.HEADER, TestApiKey.VALOR)
                        .contentType("application/json")
                        .content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FECHA_NACIMIENTO_FUTURA"));
    }

    @Test
    void correoDuplicadoRegresa409() throws Exception {
        given(personaService.crear(any())).willThrow(
                new DuplicateFieldException(ErrorCode.PERSONA_CORREO_DUPLICADO, "correo",
                        "Ya existe una persona activa registrada con este correo electrónico",
                        "Debe ser único entre personas activas"));

        mockMvc.perform(post("/api/personas")
                        .header(TestApiKey.HEADER, TestApiKey.VALOR)
                        .contentType("application/json")
                        .content(cuerpoValido()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PERSONA_CORREO_DUPLICADO"));
    }

    @Test
    void curpDuplicadoRegresa409() throws Exception {
        given(personaService.crear(any())).willThrow(
                new DuplicateFieldException(ErrorCode.PERSONA_CURP_DUPLICADO, "curp",
                        "Ya existe una persona activa registrada con este CURP",
                        "Debe ser único entre personas activas"));

        mockMvc.perform(post("/api/personas")
                        .header(TestApiKey.HEADER, TestApiKey.VALOR)
                        .contentType("application/json")
                        .content(cuerpoValido()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PERSONA_CURP_DUPLICADO"));
    }
}
