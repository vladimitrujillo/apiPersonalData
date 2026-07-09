package mx.personas.api.persona;

import mx.personas.api.common.error.DuplicateFieldException;
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
import mx.personas.api.automovil.service.AutomovilService;
import mx.personas.api.automovil.mapper.AutomovilMapper;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PersonaController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
@WithMockUser(roles = "ADMIN")
class PersonaControllerUpdateTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PersonaService personaService;

    @MockBean
    private PersonaProfesionService personaProfesionService;

    @MockBean
    private AutomovilService automovilService;

    @MockBean
    private AutomovilMapper automovilMapper;

    @Test
    void actualizacionParcialPreservaCamposNoEnviadosRegresa200() throws Exception {
        UUID id = UUID.randomUUID();
        OffsetDateTime ahora = OffsetDateTime.now();
        DireccionResponseDTO direccion = new DireccionResponseDTO(
                "Av. Insurgentes", "100", "Roma Norte", "Cuauhtémoc", "Ciudad de México", "06700", "MX",
                "admin", ahora, "admin", ahora);
        PersonaResponseDTO respuesta = new PersonaResponseDTO(
                id, "Juana", "Pérez López", LocalDate.of(1990, 5, 10), "F",
                "PELJ900510MDFRZN09", "PELJ900510AB1", "juana.perez@example.com", "5587654321",
                "admin", ahora, "admin", ahora, direccion);
        given(personaService.actualizar(eq(id), any())).willReturn(respuesta);

        mockMvc.perform(patch("/api/personas/{id}", id)
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
                        .contentType("application/json")
                        .content("{\"telefono\": \"5587654321\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PERSONA_NO_ENCONTRADA"));
    }

    @Test
    void curpDuplicadaActivaRegresa409() throws Exception {
        UUID id = UUID.randomUUID();
        given(personaService.actualizar(eq(id), any())).willThrow(
                new DuplicateFieldException(ErrorCode.PERSONA_CURP_DUPLICADO, "curp",
                        "Ya existe una persona activa registrada con este CURP",
                        "Debe ser único entre personas activas"));

        mockMvc.perform(patch("/api/personas/{id}", id)
                        .contentType("application/json")
                        .content("{\"curp\": \"PELJ900510MDFRZN09\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PERSONA_CURP_DUPLICADO"));
    }

    @Test
    void curpDeRegistroEliminadoRegresa409Accionable() throws Exception {
        UUID id = UUID.randomUUID();
        UUID idEliminado = UUID.randomUUID();
        given(personaService.actualizar(eq(id), any())).willThrow(
                new DuplicateFieldException(ErrorCode.PERSONA_CURP_ELIMINADA, "curp",
                        "Existe un registro eliminado con este CURP; un ADMIN puede restaurarlo",
                        "Registro eliminado con id " + idEliminado));

        mockMvc.perform(patch("/api/personas/{id}", id)
                        .contentType("application/json")
                        .content("{\"curp\": \"PELJ900510MDFRZN09\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PERSONA_CURP_ELIMINADA"))
                .andExpect(jsonPath("$.detalles[0].motivo").value("Registro eliminado con id " + idEliminado));
    }
}
