package mx.personas.api.profesion;

import mx.personas.api.common.security.JwtAuthenticationFilter;
import mx.personas.api.common.security.JwtService;
import mx.personas.api.common.security.SecurityConfig;
import mx.personas.api.profesion.controller.ProfesionController;
import mx.personas.api.profesion.mapper.ProfesionMapper;
import mx.personas.api.profesion.model.Profesion;
import mx.personas.api.profesion.service.PersonaProfesionService;
import mx.personas.api.profesion.service.ProfesionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProfesionController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
class ProfesionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProfesionService profesionService;

    @MockBean
    private PersonaProfesionService personaProfesionService;

    @MockBean
    private ProfesionMapper profesionMapper;

    @Test
    @WithMockUser(roles = "CAPTURISTA")
    void capturistaRegresa403AlCrearEditarDesactivarReactivar() throws Exception {
        mockMvc.perform(post("/api/profesiones").contentType("application/json")
                        .content("{\"nombre\":\"Plomero\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));

        mockMvc.perform(patch("/api/profesiones/1").contentType("application/json")
                        .content("{\"descripcion\":\"x\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/profesiones/1/desactivar"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/profesiones/1/reactivar"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminPuedeCrear() throws Exception {
        given(profesionService.crear(any())).willReturn(new Profesion("Plomero", null));
        given(profesionMapper.toResponseDTO(any())).willReturn(
                new mx.personas.api.profesion.dto.ProfesionResponseDTO(1L, "Plomero", null, true));

        mockMvc.perform(post("/api/profesiones").contentType("application/json")
                        .content("{\"nombre\":\"Plomero\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Plomero"));
    }

    @Test
    @WithMockUser(roles = "CAPTURISTA")
    void capturistaConIncluirInactivasSigueViendoSoloActivas() throws Exception {
        given(profesionService.listarCatalogo(any(), anyBoolean(), anyBoolean()))
                .willReturn(Page.empty(PageRequest.of(0, 20)));

        mockMvc.perform(get("/api/profesiones").param("incluirInactivas", "true"))
                .andExpect(status().isOk());

        verify(profesionService).listarCatalogo(any(), org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.eq(false));
    }
}
