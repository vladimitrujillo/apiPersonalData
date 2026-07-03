package mx.personas.api.persona;

import mx.personas.api.common.TestApiKey;
import mx.personas.api.common.error.ErrorCode;
import mx.personas.api.common.error.RecursoNoEncontradoException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PersonaController.class)
@TestPropertySource(properties = "app.security.api-key=" + TestApiKey.VALOR)
class PersonaControllerUpdateTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PersonaService personaService;

    @Test
    void actualizacionParcialPreservaCamposNoEnviadosRegresa200() throws Exception {
        UUID id = UUID.randomUUID();
        DireccionResponseDTO direccion = new DireccionResponseDTO(
                "Av. Insurgentes", "100", "Roma Norte", "Cuauhtémoc", "Ciudad de México", "06700", "MX");
        PersonaResponseDTO respuesta = new PersonaResponseDTO(
                id, "Juana", "Pérez López", LocalDate.of(1990, 5, 10), "F",
                "PELJ900510MDFRZN09", "PELJ900510AB1", "juana.perez@example.com", "5587654321", direccion);
        given(personaService.actualizar(eq(id), any())).willReturn(respuesta);

        mockMvc.perform(patch("/api/personas/{id}", id)
                        .header(TestApiKey.HEADER, TestApiKey.VALOR)
                        .contentType("application/json")
                        .content("{\"telefono\": \"5587654321\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefono").value("5587654321"))
                .andExpect(jsonPath("$.nombres").value("Juana"));
    }

    @Test
    void actualizarPersonaEliminadaRegresa404() throws Exception {
        UUID id = UUID.randomUUID();
        given(personaService.actualizar(eq(id), any())).willThrow(
                new RecursoNoEncontradoException(ErrorCode.PERSONA_NO_ENCONTRADA,
                        "No existe una persona activa con el identificador '" + id + "'"));

        mockMvc.perform(patch("/api/personas/{id}", id)
                        .header(TestApiKey.HEADER, TestApiKey.VALOR)
                        .contentType("application/json")
                        .content("{\"telefono\": \"5587654321\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PERSONA_NO_ENCONTRADA"));
    }
}
