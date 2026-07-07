package mx.personas.api.persona;

import mx.personas.api.common.security.JwtAuthenticationFilter;
import mx.personas.api.common.security.JwtService;
import mx.personas.api.common.security.SecurityConfig;
import mx.personas.api.persona.controller.PersonaController;
import mx.personas.api.persona.dto.DireccionResponseDTO;
import mx.personas.api.persona.dto.PersonaPageResponseDTO;
import mx.personas.api.persona.dto.PersonaResponseDTO;
import mx.personas.api.persona.service.PersonaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
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

    private PersonaResponseDTO personaDeEjemplo() {
        DireccionResponseDTO direccion = new DireccionResponseDTO(
                "Av. Insurgentes", "100", "Roma Norte", "Cuauhtémoc", "Ciudad de México", "06700", "MX");
        return new PersonaResponseDTO(UUID.randomUUID(), "Juana", "Pérez López", LocalDate.of(1990, 5, 10), "F",
                "PELJ900510MDFRZN09", "PELJ900510AB1", "juana.perez@example.com", "5512345678", direccion);
    }

    @Test
    void listaSinFiltrosRegresa200ConMetadatosDePaginacion() throws Exception {
        PersonaPageResponseDTO pagina = new PersonaPageResponseDTO(List.of(personaDeEjemplo()), 0, 20, 1, 1);
        given(personaService.listar(isNull(), isNull(), isNull(), any())).willReturn(pagina);

        mockMvc.perform(get("/api/personas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanoPagina").value(20))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.totalPaginas").value(1));
    }

    @Test
    void listaConFiltroDeMunicipioRegresaSoloCoincidencias() throws Exception {
        PersonaPageResponseDTO pagina = new PersonaPageResponseDTO(List.of(personaDeEjemplo()), 0, 20, 1, 1);
        given(personaService.listar(isNull(), org.mockito.ArgumentMatchers.eq("Cuauhtémoc"), isNull(), any()))
                .willReturn(pagina);

        mockMvc.perform(get("/api/personas")
                        .param("municipio", "Cuauhtémoc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(1));
    }

    @Test
    void paginaFueraDeRangoRegresaListaVacia() throws Exception {
        PersonaPageResponseDTO paginaVacia = new PersonaPageResponseDTO(List.of(), 5, 20, 1, 1);
        given(personaService.listar(isNull(), isNull(), isNull(), any())).willReturn(paginaVacia);

        mockMvc.perform(get("/api/personas")
                        .param("page", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(0));
    }
}
