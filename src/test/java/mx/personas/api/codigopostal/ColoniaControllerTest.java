package mx.personas.api.codigopostal;

import mx.personas.api.codigopostal.controller.ColoniaController;
import mx.personas.api.codigopostal.dto.ColoniaBusquedaDTO;
import mx.personas.api.codigopostal.service.CodigoPostalService;
import mx.personas.api.common.TestApiKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ColoniaController.class)
@TestPropertySource(properties = "app.security.api-key=" + TestApiKey.VALOR)
class ColoniaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CodigoPostalService codigoPostalService;

    @Test
    void buscaPorNombreParcialRegresa200ConCoincidencias() throws Exception {
        given(codigoPostalService.buscarColonias(eq("roma"), isNull(), isNull())).willReturn(List.of(
                new ColoniaBusquedaDTO("06700", "Ciudad de México", "Cuauhtémoc", "Roma Sur", "Colonia")));

        mockMvc.perform(get("/api/colonias").header(TestApiKey.HEADER, TestApiKey.VALOR).param("nombre", "roma"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Roma Sur"));
    }

    @Test
    void buscaAcotadaPorEstadoRegresaSoloEseEstado() throws Exception {
        given(codigoPostalService.buscarColonias(eq("roma"), eq("Ciudad de México"), any())).willReturn(List.of(
                new ColoniaBusquedaDTO("06700", "Ciudad de México", "Cuauhtémoc", "Roma Sur", "Colonia")));

        mockMvc.perform(get("/api/colonias")
                        .header(TestApiKey.HEADER, TestApiKey.VALOR)
                        .param("nombre", "roma")
                        .param("estado", "Ciudad de México"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void sinCoincidenciasRegresa200ConListaVacia() throws Exception {
        given(codigoPostalService.buscarColonias(eq("xyz"), isNull(), isNull())).willReturn(List.of());

        mockMvc.perform(get("/api/colonias").header(TestApiKey.HEADER, TestApiKey.VALOR).param("nombre", "xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void sinNombreRegresa400() throws Exception {
        mockMvc.perform(get("/api/colonias").header(TestApiKey.HEADER, TestApiKey.VALOR))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }
}
