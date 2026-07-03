package mx.personas.api.codigopostal;

import mx.personas.api.codigopostal.controller.CodigoPostalController;
import mx.personas.api.codigopostal.dto.CodigoPostalResponseDTO;
import mx.personas.api.codigopostal.dto.ColoniaDTO;
import mx.personas.api.codigopostal.service.CodigoPostalService;
import mx.personas.api.common.TestApiKey;
import mx.personas.api.common.error.ErrorCode;
import mx.personas.api.common.error.RecursoNoEncontradoException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CodigoPostalController.class)
@TestPropertySource(properties = "app.security.api-key=" + TestApiKey.VALOR)
class CodigoPostalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CodigoPostalService codigoPostalService;

    @Test
    void consultaCpExistenteRegresa200ConColonias() throws Exception {
        CodigoPostalResponseDTO respuesta = new CodigoPostalResponseDTO("06700", "Ciudad de México", "Cuauhtémoc",
                List.of(new ColoniaDTO("Roma Norte", "Colonia"), new ColoniaDTO("Roma Sur", "Colonia")));
        given(codigoPostalService.consultarPorCodigoPostal("06700")).willReturn(respuesta);

        mockMvc.perform(get("/api/codigos-postales/{cp}", "06700").header(TestApiKey.HEADER, TestApiKey.VALOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Ciudad de México"))
                .andExpect(jsonPath("$.municipio").value("Cuauhtémoc"))
                .andExpect(jsonPath("$.colonias.length()").value(2));
    }

    @Test
    void consultaCpInexistenteRegresa404() throws Exception {
        given(codigoPostalService.consultarPorCodigoPostal("00000")).willThrow(
                new RecursoNoEncontradoException(ErrorCode.CP_NO_ENCONTRADO,
                        "No existe un código postal '00000' en el catálogo"));

        mockMvc.perform(get("/api/codigos-postales/{cp}", "00000").header(TestApiKey.HEADER, TestApiKey.VALOR))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CP_NO_ENCONTRADO"));
    }

    @Test
    void cpConFormatoInvalidoRegresa400() throws Exception {
        mockMvc.perform(get("/api/codigos-postales/{cp}", "ABCDE").header(TestApiKey.HEADER, TestApiKey.VALOR))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CP_FORMATO_INVALIDO"));
    }

    @Test
    void cpConMenosDeCincoDigitosRegresa400() throws Exception {
        mockMvc.perform(get("/api/codigos-postales/{cp}", "123").header(TestApiKey.HEADER, TestApiKey.VALOR))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CP_FORMATO_INVALIDO"));
    }
}
