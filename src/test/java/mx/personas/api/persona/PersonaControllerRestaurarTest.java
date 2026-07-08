package mx.personas.api.persona;

import mx.personas.api.common.error.DuplicateFieldException;
import mx.personas.api.common.error.ErrorCode;
import mx.personas.api.common.error.PersonaYaActivaException;
import mx.personas.api.common.error.RecursoNoEncontradoException;
import mx.personas.api.common.security.JwtAuthenticationFilter;
import mx.personas.api.common.security.JwtService;
import mx.personas.api.common.security.SecurityConfig;
import mx.personas.api.persona.controller.PersonaController;
import mx.personas.api.persona.dto.DireccionResponseDTO;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PersonaController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
class PersonaControllerRestaurarTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PersonaService personaService;

    @MockBean
    private PersonaProfesionService personaProfesionService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminRestauraPersonaEliminadaRegresa200() throws Exception {
        UUID id = UUID.randomUUID();
        OffsetDateTime ahora = OffsetDateTime.now();
        DireccionResponseDTO direccion = new DireccionResponseDTO(
                "Av. Insurgentes", "100", "Roma Norte", "Cuauhtémoc", "Ciudad de México", "06700", "MX",
                "admin", ahora, "admin", ahora);
        PersonaResponseDTO respuesta = new PersonaResponseDTO(
                id, "Juana", "Pérez López", LocalDate.of(1990, 5, 10), "F",
                "PELJ900510MDFRZN09", "PELJ900510AB1", "juana.perez@example.com", "5512345678",
                "admin", ahora, "admin", ahora, direccion);
        given(personaService.restaurar(id)).willReturn(respuesta);

        mockMvc.perform(post("/api/personas/{id}/restaurar", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void restaurarPersonaInexistenteRegresa404() throws Exception {
        UUID id = UUID.randomUUID();
        given(personaService.restaurar(id)).willThrow(
                new RecursoNoEncontradoException(ErrorCode.PERSONA_NO_ENCONTRADA,
                        "No existe una persona con el identificador '" + id + "'"));

        mockMvc.perform(post("/api/personas/{id}/restaurar", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PERSONA_NO_ENCONTRADA"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void restaurarPersonaYaActivaRegresa409() throws Exception {
        UUID id = UUID.randomUUID();
        given(personaService.restaurar(id)).willThrow(
                new PersonaYaActivaException("La persona con el identificador '" + id + "' ya está activa"));

        mockMvc.perform(post("/api/personas/{id}/restaurar", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PERSONA_YA_ACTIVA"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void restaurarConCorreoYaTomadoPorOtraPersonaActivaRegresa409() throws Exception {
        UUID id = UUID.randomUUID();
        UUID idActivaConElCorreo = UUID.randomUUID();
        given(personaService.restaurar(id)).willThrow(
                new DuplicateFieldException(ErrorCode.PERSONA_CORREO_DUPLICADO, "correo",
                        "Ya existe una persona activa registrada con este correo electrónico",
                        "En uso por la persona activa con id " + idActivaConElCorreo));

        mockMvc.perform(post("/api/personas/{id}/restaurar", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PERSONA_CORREO_DUPLICADO"))
                .andExpect(jsonPath("$.detalles[0].motivo")
                        .value("En uso por la persona activa con id " + idActivaConElCorreo));
    }

    @Test
    @WithMockUser(roles = "CAPTURISTA")
    void capturistaNoPuedeRestaurarRegresa403() throws Exception {
        mockMvc.perform(post("/api/personas/{id}/restaurar", UUID.randomUUID()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }
}
