package mx.personas.api.persona;

import mx.personas.api.common.error.ErrorCode;
import mx.personas.api.common.error.RecursoNoEncontradoException;
import mx.personas.api.common.security.JwtAuthenticationFilter;
import mx.personas.api.common.security.JwtService;
import mx.personas.api.common.security.SecurityConfig;
import mx.personas.api.persona.controller.PersonaController;
import mx.personas.api.persona.service.PersonaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PersonaController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
@WithMockUser(roles = "ADMIN")
class PersonaControllerDeleteTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PersonaService personaService;

    @Test
    void eliminarPersonaActivaRegresa204() throws Exception {
        UUID id = UUID.randomUUID();
        willDoNothing().given(personaService).eliminar(id);

        mockMvc.perform(delete("/api/personas/{id}", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void eliminarPersonaYaEliminadaRegresa404() throws Exception {
        UUID id = UUID.randomUUID();
        willThrow(new RecursoNoEncontradoException(ErrorCode.PERSONA_NO_ENCONTRADA,
                "No existe una persona activa con el identificador '" + id + "'"))
                .given(personaService).eliminar(id);

        mockMvc.perform(delete("/api/personas/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PERSONA_NO_ENCONTRADA"));
    }

    @Test
    @WithMockUser(roles = "CAPTURISTA")
    void capturistaNoPuedeEliminarPersonaRegresa403() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/personas/{id}", id))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }
}
