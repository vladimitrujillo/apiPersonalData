package mx.personas.api.codigopostal;

import mx.personas.api.codigopostal.controller.CatalogoImportacionController;
import mx.personas.api.codigopostal.repository.CatalogoImportacionRepository;
import mx.personas.api.codigopostal.service.CatalogoImportacionOrchestrator;
import mx.personas.api.common.audit.SecurityAuditorAware;
import mx.personas.api.common.error.CatalogoArchivoInvalidoException;
import mx.personas.api.common.error.CatalogoImportacionEnCursoException;
import mx.personas.api.common.security.JwtAuthenticationFilter;
import mx.personas.api.common.security.JwtService;
import mx.personas.api.common.security.SecurityConfig;
import mx.personas.api.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CatalogoImportacionController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
class CatalogoImportacionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CatalogoImportacionOrchestrator orchestrator;

    @MockBean
    private SecurityAuditorAware securityAuditorAware;

    @MockBean
    private CatalogoImportacionRepository catalogoImportacionRepository;

    @MockBean
    private UsuarioRepository usuarioRepository;

    private MockMultipartFile archivoDeEjemplo() {
        return new MockMultipartFile("archivo", "catalogo.csv", "text/csv",
                "codigoPostal|estado|municipio|asentamiento|tipoAsentamiento|idAsentaCpcons\n".getBytes());
    }

    @Test
    @WithMockUser(roles = "CAPTURISTA")
    void capturistaNoPuedeDispararLaImportacionManualRegresa403() throws Exception {
        mockMvc.perform(multipart("/api/codigos-postales/importaciones").file(archivoDeEjemplo()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void candadoNoDisponibleRegresa409() throws Exception {
        given(securityAuditorAware.getCurrentAuditor()).willReturn(Optional.of(UUID.randomUUID()));
        given(orchestrator.ejecutar(any(), any(), any(), any(), any())).willThrow(
                new CatalogoImportacionEnCursoException("Ya hay una importación en curso"));

        mockMvc.perform(multipart("/api/codigos-postales/importaciones").file(archivoDeEjemplo()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CATALOGO_IMPORTACION_EN_CURSO"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void archivoInvalidoRegresa400() throws Exception {
        given(securityAuditorAware.getCurrentAuditor()).willReturn(Optional.of(UUID.randomUUID()));
        given(orchestrator.ejecutar(any(), any(), any(), any(), any())).willThrow(
                new CatalogoArchivoInvalidoException("Encabezado inválido"));

        mockMvc.perform(multipart("/api/codigos-postales/importaciones").file(archivoDeEjemplo()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CATALOGO_ARCHIVO_INVALIDO"));
    }

    @Test
    @WithMockUser(roles = "CAPTURISTA")
    void capturistaNoPuedeConsultarLaBitacoraRegresa403() throws Exception {
        mockMvc.perform(get("/api/codigos-postales/importaciones"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

}
