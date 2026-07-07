package mx.personas.api.usuario;

import mx.personas.api.common.error.DuplicateFieldException;
import mx.personas.api.common.error.ErrorCode;
import mx.personas.api.common.error.RecursoNoEncontradoException;
import mx.personas.api.common.security.JwtAuthenticationFilter;
import mx.personas.api.common.security.JwtService;
import mx.personas.api.common.security.SecurityConfig;
import mx.personas.api.usuario.controller.UsuarioController;
import mx.personas.api.usuario.dto.UsuarioResponseDTO;
import mx.personas.api.usuario.model.Rol;
import mx.personas.api.usuario.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UsuarioController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
@WithMockUser(roles = "ADMIN")
class UsuarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UsuarioService usuarioService;

    private String cuerpoCrear(String login) {
        return """
                {"login": "%s", "password": "clave-temporal-123", "nombre": "Juan Pérez", "rol": "CAPTURISTA"}
                """.formatted(login);
    }

    @Test
    void crearUsuarioValidoRegresa201SinContrasenaEnLaRespuesta() throws Exception {
        UUID id = UUID.randomUUID();
        given(usuarioService.crear(any())).willReturn(new UsuarioResponseDTO(id, "jperez", "Juan Pérez",
                Rol.CAPTURISTA, true));

        mockMvc.perform(post("/api/usuarios")
                        .contentType("application/json")
                        .content(cuerpoCrear("jperez")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.login").value("jperez"))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void crearUsuarioConLoginDuplicadoRegresa409() throws Exception {
        given(usuarioService.crear(any())).willThrow(
                new DuplicateFieldException(ErrorCode.USUARIO_LOGIN_DUPLICADO, "login",
                        "Ya existe un usuario registrado con este login", "Debe ser único de forma permanente"));

        mockMvc.perform(post("/api/usuarios")
                        .contentType("application/json")
                        .content(cuerpoCrear("jperez")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("USUARIO_LOGIN_DUPLICADO"));
    }

    @Test
    void listarUsuariosRegresa200SinContrasenas() throws Exception {
        given(usuarioService.listar()).willReturn(List.of(
                new UsuarioResponseDTO(UUID.randomUUID(), "admin", "Admin", Rol.ADMIN, true)));

        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].login").value("admin"))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    void desactivarUsuarioExistenteRegresa200() throws Exception {
        UUID id = UUID.randomUUID();
        given(usuarioService.desactivar(id)).willReturn(
                new UsuarioResponseDTO(id, "jperez", "Juan Pérez", Rol.CAPTURISTA, false));

        mockMvc.perform(patch("/api/usuarios/{id}/desactivar", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    void desactivarUsuarioInexistenteRegresa404() throws Exception {
        UUID id = UUID.randomUUID();
        given(usuarioService.desactivar(id)).willThrow(
                new RecursoNoEncontradoException(ErrorCode.USUARIO_NO_ENCONTRADO,
                        "No existe un usuario con el identificador '" + id + "'"));

        mockMvc.perform(patch("/api/usuarios/{id}/desactivar", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("USUARIO_NO_ENCONTRADO"));
    }

    @Test
    void restablecerContrasenaValidaRegresa204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(patch("/api/usuarios/{id}/contrasena", id)
                        .contentType("application/json")
                        .content("{\"nuevaContrasena\": \"otra-clave-larga\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void restablecerContrasenaDeUsuarioInexistenteRegresa404() throws Exception {
        UUID id = UUID.randomUUID();
        willThrow(new RecursoNoEncontradoException(ErrorCode.USUARIO_NO_ENCONTRADO,
                "No existe un usuario con el identificador '" + id + "'"))
                .given(usuarioService).restablecerContrasena(eq(id), any());

        mockMvc.perform(patch("/api/usuarios/{id}/contrasena", id)
                        .contentType("application/json")
                        .content("{\"nuevaContrasena\": \"otra-clave-larga\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("USUARIO_NO_ENCONTRADO"));
    }

    @Test
    @WithMockUser(roles = "CAPTURISTA")
    void capturistaNoPuedeCrearUsuarioRegresa403() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .contentType("application/json")
                        .content(cuerpoCrear("otro")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    @WithMockUser(roles = "CAPTURISTA")
    void capturistaNoPuedeListarUsuariosRegresa403() throws Exception {
        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    @WithMockUser(roles = "CAPTURISTA")
    void capturistaNoPuedeDesactivarUsuarioRegresa403() throws Exception {
        mockMvc.perform(patch("/api/usuarios/{id}/desactivar", UUID.randomUUID()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    @WithMockUser(roles = "CAPTURISTA")
    void capturistaNoPuedeRestablecerContrasenaRegresa403() throws Exception {
        mockMvc.perform(patch("/api/usuarios/{id}/contrasena", UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"nuevaContrasena\": \"otra-clave-larga\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }
}
