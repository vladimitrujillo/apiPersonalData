package mx.personas.api.persona;

import mx.personas.api.common.TestApiKey;
import mx.personas.api.common.error.ErrorCode;
import mx.personas.api.common.error.RecursoNoEncontradoException;
import mx.personas.api.persona.controller.PersonaController;
import mx.personas.api.persona.service.PersonaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PersonaController.class)
@TestPropertySource(properties = "app.security.api-key=" + TestApiKey.VALOR)
class PersonaControllerDeleteTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PersonaService personaService;

    @Test
    void eliminarPersonaActivaRegresa204() throws Exception {
        UUID id = UUID.randomUUID();
        willDoNothing().given(personaService).eliminar(id);

        mockMvc.perform(delete("/api/personas/{id}", id)
                        .header(TestApiKey.HEADER, TestApiKey.VALOR))
                .andExpect(status().isNoContent());
    }

    @Test
    void eliminarPersonaYaEliminadaRegresa404() throws Exception {
        UUID id = UUID.randomUUID();
        willThrow(new RecursoNoEncontradoException(ErrorCode.PERSONA_NO_ENCONTRADA,
                "No existe una persona activa con el identificador '" + id + "'"))
                .given(personaService).eliminar(id);

        mockMvc.perform(delete("/api/personas/{id}", id)
                        .header(TestApiKey.HEADER, TestApiKey.VALOR))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PERSONA_NO_ENCONTRADA"));
    }
}
