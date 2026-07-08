package mx.personas.api.persona;

import mx.personas.api.common.error.ErrorCode;
import mx.personas.api.common.error.RecursoNoEncontradoException;
import mx.personas.api.common.security.JwtAuthenticationFilter;
import mx.personas.api.common.security.JwtService;
import mx.personas.api.common.security.SecurityConfig;
import mx.personas.api.persona.controller.PersonaController;
import mx.personas.api.persona.dto.CampoCambiadoDTO;
import mx.personas.api.persona.dto.HistorialEntradaDTO;
import mx.personas.api.persona.dto.HistorialPageResponseDTO;
import mx.personas.api.persona.service.PersonaService;
import mx.personas.api.profesion.service.PersonaProfesionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PersonaController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
class PersonaControllerHistorialTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PersonaService personaService;

    @MockBean
    private PersonaProfesionService personaProfesionService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminConsultaHistorialRegresa200ConEntradas() throws Exception {
        UUID id = UUID.randomUUID();
        HistorialEntradaDTO entrada = new HistorialEntradaDTO(OffsetDateTime.now(), "admin", "CREACION",
                List.of(new CampoCambiadoDTO("nombres", null, "Juana")));
        HistorialPageResponseDTO pagina = new HistorialPageResponseDTO(List.of(entrada), 0, 20, 1, 1);
        given(personaService.historial(org.mockito.ArgumentMatchers.eq(id), any())).willReturn(pagina);

        mockMvc.perform(get("/api/personas/{id}/historial", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.contenido[0].usuario").value("admin"))
                .andExpect(jsonPath("$.contenido[0].operacion").value("CREACION"))
                .andExpect(jsonPath("$.contenido[0].cambios[0].campo").value("nombres"));
    }

    @Test
    @WithMockUser(roles = "CAPTURISTA")
    void capturistaConsultaHistorialRegresa403() throws Exception {
        mockMvc.perform(get("/api/personas/{id}/historial", UUID.randomUUID()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void historialDePersonaInexistenteRegresa404() throws Exception {
        UUID id = UUID.randomUUID();
        given(personaService.historial(org.mockito.ArgumentMatchers.eq(id), any())).willThrow(
                new RecursoNoEncontradoException(ErrorCode.PERSONA_NO_ENCONTRADA,
                        "No existe una persona con el identificador '" + id + "'"));

        mockMvc.perform(get("/api/personas/{id}/historial", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PERSONA_NO_ENCONTRADA"));
    }
}
