package mx.personas.api.persona;

import mx.personas.api.common.security.JwtAuthenticationFilter;
import mx.personas.api.common.security.JwtService;
import mx.personas.api.common.security.SecurityConfig;
import mx.personas.api.persona.controller.PersonaController;
import mx.personas.api.persona.dto.DireccionResponseDTO;
import mx.personas.api.persona.dto.PersonaEliminadaPageResponseDTO;
import mx.personas.api.persona.dto.PersonaResponseDTO;
import mx.personas.api.persona.service.PersonaService;
import mx.personas.api.profesion.service.PersonaProfesionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
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
class PersonaControllerEliminadasTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PersonaService personaService;

    @MockBean
    private PersonaProfesionService personaProfesionService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminConsultaEliminadasRegresa200ConShapePaginado() throws Exception {
        OffsetDateTime ahora = OffsetDateTime.now();
        DireccionResponseDTO direccion = new DireccionResponseDTO(
                "Av. Insurgentes", "100", "Roma Norte", "Cuauhtémoc", "Ciudad de México", "06700", "MX",
                "admin", ahora, "admin", ahora);
        PersonaResponseDTO eliminada = new PersonaResponseDTO(
                UUID.randomUUID(), "Juana", "Pérez López", LocalDate.of(1990, 5, 10), "F",
                "PELJ900510MDFRZN09", "PELJ900510AB1", "juana.perez@example.com", "5512345678",
                "admin", ahora, "admin", ahora, direccion);
        PersonaEliminadaPageResponseDTO pagina = new PersonaEliminadaPageResponseDTO(
                List.of(eliminada), 0, 20, 1, 1);
        given(personaService.listarEliminadas(any())).willReturn(pagina);

        mockMvc.perform(get("/api/personas/eliminadas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.contenido[0].correo").value("juana.perez@example.com"))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    @WithMockUser(roles = "CAPTURISTA")
    void capturistaConsultaEliminadasRegresa403() throws Exception {
        mockMvc.perform(get("/api/personas/eliminadas"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }
}
