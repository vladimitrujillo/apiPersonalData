package mx.personas.api.auth;

import mx.personas.api.auth.controller.AuthController;
import mx.personas.api.auth.dto.TokenResponseDTO;
import mx.personas.api.auth.service.AuthService;
import mx.personas.api.common.error.CredencialesInvalidasException;
import mx.personas.api.common.security.JwtAuthenticationFilter;
import mx.personas.api.common.security.JwtService;
import mx.personas.api.common.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    private String cuerpoLogin(String login, String password) {
        return """
                {"login": "%s", "password": "%s"}
                """.formatted(login, password);
    }

    @Test
    void loginConCredencialesValidasRegresa200ConTokens() throws Exception {
        given(authService.login(any(), any())).willReturn(new TokenResponseDTO(
                "un-jwt-de-acceso", "un-token-de-refresco-opaco", OffsetDateTime.now().plusMinutes(15)));

        mockMvc.perform(post("/login")
                        .contentType("application/json")
                        .content(cuerpoLogin("admin", "clave-correcta")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("un-jwt-de-acceso"))
                .andExpect(jsonPath("$.refreshToken").value("un-token-de-refresco-opaco"))
                .andExpect(jsonPath("$.expiraEn").exists());
    }

    @Test
    void loginConContrasenaIncorrectaRegresa401Generico() throws Exception {
        given(authService.login(any(), any()))
                .willThrow(new CredencialesInvalidasException("Usuario o contraseña incorrectos"));

        mockMvc.perform(post("/login")
                        .contentType("application/json")
                        .content(cuerpoLogin("admin", "clave-incorrecta")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void loginConUsuarioInexistenteRegresaElMismo401Generico() throws Exception {
        given(authService.login(any(), any()))
                .willThrow(new CredencialesInvalidasException("Usuario o contraseña incorrectos"));

        mockMvc.perform(post("/login")
                        .contentType("application/json")
                        .content(cuerpoLogin("no-existe", "lo-que-sea")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void loginConUsuarioDesactivadoRegresaElMismo401Generico() throws Exception {
        given(authService.login(any(), any()))
                .willThrow(new CredencialesInvalidasException("Usuario o contraseña incorrectos"));

        mockMvc.perform(post("/login")
                        .contentType("application/json")
                        .content(cuerpoLogin("desactivado", "clave-correcta")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void loginSinLoginRegresa400() throws Exception {
        mockMvc.perform(post("/login")
                        .contentType("application/json")
                        .content(cuerpoLogin("", "clave")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"));
    }

    private String cuerpoRefresh(String refreshToken) {
        return """
                {"refreshToken": "%s"}
                """.formatted(refreshToken);
    }

    @Test
    void refreshConTokenVigenteRegresa200ConNuevoParDeTokens() throws Exception {
        given(authService.refresh(any())).willReturn(new TokenResponseDTO(
                "un-nuevo-jwt", "un-nuevo-refresco-opaco", OffsetDateTime.now().plusMinutes(15)));

        mockMvc.perform(post("/refresh")
                        .contentType("application/json")
                        .content(cuerpoRefresh("un-refresh-token-vigente")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("un-nuevo-jwt"))
                .andExpect(jsonPath("$.refreshToken").value("un-nuevo-refresco-opaco"));
    }

    @Test
    void refreshConTokenInvalidoORevocadoRegresa401() throws Exception {
        given(authService.refresh(any()))
                .willThrow(new CredencialesInvalidasException("Token de refresco inválido"));

        mockMvc.perform(post("/refresh")
                        .contentType("application/json")
                        .content(cuerpoRefresh("un-token-ya-usado")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void refreshDeUsuarioDesactivadoRegresa401() throws Exception {
        given(authService.refresh(any()))
                .willThrow(new CredencialesInvalidasException("Token de refresco inválido"));

        mockMvc.perform(post("/refresh")
                        .contentType("application/json")
                        .content(cuerpoRefresh("token-de-usuario-desactivado")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }
}
