package mx.personas.api.persona;

import mx.personas.api.common.error.ErrorCode;
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

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PersonaController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
@WithMockUser(roles = "ADMIN")
class PersonaControllerGetTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PersonaService personaService;

    @MockBean
    private PersonaProfesionService personaProfesionService;

    @Test
    void consultaPersonaActivaRegresa200() throws Exception {
        UUID id = UUID.randomUUID();
        OffsetDateTime ahora = OffsetDateTime.now();
        DireccionResponseDTO direccion = new DireccionResponseDTO(
                "Av. Insurgentes", "100", "Roma Norte", "Cuauhtémoc", "Ciudad de México", "06700", "MX",
                "admin", ahora, "admin", ahora);
        PersonaResponseDTO respuesta = new PersonaResponseDTO(
                id, "Juana", "Pérez López", LocalDate.of(1990, 5, 10), "F",
                "PELJ900510MDFRZN09", "PELJ900510AB1", "juana.perez@example.com", "5512345678",
                "admin", ahora, "admin", ahora, direccion);
        given(personaService.obtenerPorId(id)).willReturn(respuesta);

        mockMvc.perform(get("/api/personas/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void consultaIncluyeDatosDeAuditoriaDePersonaYDireccion() throws Exception {
        UUID id = UUID.randomUUID();
        OffsetDateTime creacion = OffsetDateTime.now().minusDays(1);
        OffsetDateTime modificacion = OffsetDateTime.now();
        DireccionResponseDTO direccion = new DireccionResponseDTO(
                "Av. Insurgentes", "100", "Roma Norte", "Cuauhtémoc", "Ciudad de México", "06700", "MX",
                "admin", creacion, "jperez", modificacion);
        PersonaResponseDTO respuesta = new PersonaResponseDTO(
                id, "Juana", "Pérez López", LocalDate.of(1990, 5, 10), "F",
                "PELJ900510MDFRZN09", "PELJ900510AB1", "juana.perez@example.com", "5512345678",
                "admin", creacion, "jperez", modificacion, direccion);
        given(personaService.obtenerPorId(id)).willReturn(respuesta);

        mockMvc.perform(get("/api/personas/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creadoPor").value("admin"))
                .andExpect(jsonPath("$.creadoEn").exists())
                .andExpect(jsonPath("$.modificadoPor").value("jperez"))
                .andExpect(jsonPath("$.modificadoEn").exists())
                .andExpect(jsonPath("$.direccion.creadoPor").value("admin"))
                .andExpect(jsonPath("$.direccion.modificadoPor").value("jperez"));
    }

    @Test
    void consultaPersonaInexistenteRegresa404() throws Exception {
        UUID id = UUID.randomUUID();
        given(personaService.obtenerPorId(id)).willThrow(
                new RecursoNoEncontradoException(ErrorCode.PERSONA_NO_ENCONTRADA,
                        "No existe una persona activa con el identificador '" + id + "'"));

        mockMvc.perform(get("/api/personas/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PERSONA_NO_ENCONTRADA"));
    }
}
