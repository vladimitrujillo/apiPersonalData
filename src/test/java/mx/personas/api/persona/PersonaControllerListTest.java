package mx.personas.api.persona;

import mx.personas.api.common.error.ErrorCode;
import mx.personas.api.common.error.FormatoInvalidoException;
import mx.personas.api.common.security.JwtAuthenticationFilter;
import mx.personas.api.common.security.JwtService;
import mx.personas.api.common.security.SecurityConfig;
import mx.personas.api.persona.controller.PersonaController;
import mx.personas.api.persona.dto.DireccionResumenDTO;
import mx.personas.api.persona.dto.PersonaBusquedaFiltroDTO;
import mx.personas.api.persona.dto.PersonaPageResponseDTO;
import mx.personas.api.persona.dto.PersonaResumenDTO;
import mx.personas.api.persona.service.PersonaService;
import mx.personas.api.profesion.service.PersonaProfesionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PersonaController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
@WithMockUser(roles = "ADMIN")
class PersonaControllerListTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PersonaService personaService;

    @MockBean
    private PersonaProfesionService personaProfesionService;

    private PersonaResumenDTO personaDeEjemplo() {
        DireccionResumenDTO direccion = new DireccionResumenDTO(
                "Av. Insurgentes", "100", "Roma Norte", "Cuauhtémoc", "Ciudad de México", "06700", "MX");
        return new PersonaResumenDTO(UUID.randomUUID(), "Juana", "Pérez López", LocalDate.of(1990, 5, 10), "F",
                "PELJ900510MDFRZN09", "PELJ900510AB1", "juana.perez@example.com", "5512345678", direccion);
    }

    @Test
    void listaSinFiltrosRegresa200ConMetadatosDePaginacion() throws Exception {
        PersonaPageResponseDTO pagina = new PersonaPageResponseDTO(List.of(personaDeEjemplo()), 0, 20, 1, 1);
        given(personaService.listar(any(), any(), any())).willReturn(pagina);

        mockMvc.perform(get("/api/personas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanoPagina").value(20))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.totalPaginas").value(1));
    }

    @Test
    void listaNoIncluyeDatosDeAuditoria() throws Exception {
        PersonaPageResponseDTO pagina = new PersonaPageResponseDTO(List.of(personaDeEjemplo()), 0, 20, 1, 1);
        given(personaService.listar(any(), any(), any())).willReturn(pagina);

        mockMvc.perform(get("/api/personas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].creadoPor").doesNotExist())
                .andExpect(jsonPath("$.contenido[0].modificadoPor").doesNotExist())
                .andExpect(jsonPath("$.contenido[0].direccion.creadoPor").doesNotExist());
    }

    @Test
    void listaConFiltroDeMunicipioRegresaSoloCoincidencias() throws Exception {
        PersonaPageResponseDTO pagina = new PersonaPageResponseDTO(List.of(personaDeEjemplo()), 0, 20, 1, 1);
        ArgumentCaptor<PersonaBusquedaFiltroDTO> captor = ArgumentCaptor.forClass(PersonaBusquedaFiltroDTO.class);
        given(personaService.listar(captor.capture(), any(), any())).willReturn(pagina);

        mockMvc.perform(get("/api/personas")
                        .param("municipio", "Cuauhtémoc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(1));

        assertThat(captor.getValue().municipio()).isEqualTo("Cuauhtémoc");
        assertThat(captor.getValue().nombre()).isNull();
        assertThat(captor.getValue().estado()).isNull();
    }

    @Test
    void paginaFueraDeRangoRegresaListaVacia() throws Exception {
        PersonaPageResponseDTO paginaVacia = new PersonaPageResponseDTO(List.of(), 5, 20, 1, 1);
        given(personaService.listar(any(), any(), any())).willReturn(paginaVacia);

        mockMvc.perform(get("/api/personas")
                        .param("page", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(0));
    }

    // ---------- FR-011/FR-013 (regresion sin parametros nuevos, Foundational + US1) ----------

    @Test
    void sinParametrosNuevosNoSeAplicaNingunOrdenNiCriterioNuevo() throws Exception {
        PersonaPageResponseDTO pagina = new PersonaPageResponseDTO(List.of(personaDeEjemplo()), 0, 20, 1, 1);
        ArgumentCaptor<PersonaBusquedaFiltroDTO> captor = ArgumentCaptor.forClass(PersonaBusquedaFiltroDTO.class);
        given(personaService.listar(captor.capture(), any(), any())).willReturn(pagina);

        mockMvc.perform(get("/api/personas"))
                .andExpect(status().isOk());

        PersonaBusquedaFiltroDTO filtro = captor.getValue();
        assertThat(filtro.curpPrefijo()).isNull();
        assertThat(filtro.edadMinima()).isNull();
        assertThat(filtro.edadMaxima()).isNull();
        assertThat(filtro.fechaRegistroDesde()).isNull();
        assertThat(filtro.fechaRegistroHasta()).isNull();
        assertThat(filtro.sexo()).isNull();
        assertThat(filtro.ordenarPor()).isNull();
        assertThat(filtro.direccionOrden()).isNull();
    }

    // ---------- Validaciones (US2, FR-014/FR-016) ----------

    @Test
    void edadMinimaNegativaRegresa400ConCampoEdadMinima() throws Exception {
        mockMvc.perform(get("/api/personas").param("edadMinima", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
                .andExpect(jsonPath("$.detalles[?(@.campo == 'edadMinima')]").exists());
    }

    @Test
    void ordenarPorDesconocidoRegresa400ConCampoOrdenarPor() throws Exception {
        given(personaService.listar(any(), any(), any())).willThrow(
                new FormatoInvalidoException(ErrorCode.VALIDACION_FALLIDA, "ordenarPor",
                        "Debe ser uno de: [NOMBRE, FECHA_NACIMIENTO, FECHA_REGISTRO]"));

        mockMvc.perform(get("/api/personas").param("ordenarPor", "APELLIDO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
                .andExpect(jsonPath("$.detalles[0].campo").value("ordenarPor"));
    }

    // ---------- estadoRegistro por rol (US3, FR-007/FR-008) ----------

    @Test
    @WithMockUser(roles = "CAPTURISTA")
    void capturistaConEstadoRegistroEliminadasSeFuerzaAActivas() throws Exception {
        PersonaPageResponseDTO pagina = new PersonaPageResponseDTO(List.of(), 0, 20, 0, 0);
        ArgumentCaptor<String> estadoCaptor = ArgumentCaptor.forClass(String.class);
        given(personaService.listar(any(), estadoCaptor.capture(), any())).willReturn(pagina);

        mockMvc.perform(get("/api/personas").param("estadoRegistro", "ELIMINADAS"))
                .andExpect(status().isOk());

        assertThat(estadoCaptor.getValue()).isEqualTo("ACTIVAS");
    }

    @Test
    void adminConEstadoRegistroEliminadasPasaElValorSinAlterar() throws Exception {
        PersonaPageResponseDTO pagina = new PersonaPageResponseDTO(List.of(), 0, 20, 0, 0);
        ArgumentCaptor<String> estadoCaptor = ArgumentCaptor.forClass(String.class);
        given(personaService.listar(any(), estadoCaptor.capture(), any())).willReturn(pagina);

        mockMvc.perform(get("/api/personas").param("estadoRegistro", "ELIMINADAS"))
                .andExpect(status().isOk());

        assertThat(estadoCaptor.getValue()).isEqualTo("ELIMINADAS");
    }
}
