package mx.personas.api.common;

import mx.personas.api.usuario.model.Rol;
import mx.personas.api.usuario.model.Usuario;
import mx.personas.api.usuario.repository.UsuarioRepository;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

/**
 * Reemplaza a TestApiKey (research.md #7). Para pruebas @WebMvcTest, usar
 * @WithMockUser(roles = TestJwt.ROL_ADMIN) o TestJwt.ROL_CAPTURISTA (spring-security-test);
 * el JwtAuthenticationFilter no rechaza peticiones sin header en ese slice, solo deja de
 * autenticar, y el contexto de seguridad simulado por @WithMockUser prevalece. Para
 * pruebas de integracion, hacer login real contra POST /login con las constantes de ADMIN
 * de prueba (sembrado por el propio ApplicationRunner via las propiedades de bootstrap) y
 * usar el accessToken obtenido.
 */
public final class TestJwt {

    public static final String ROL_ADMIN = "ADMIN";
    public static final String ROL_CAPTURISTA = "CAPTURISTA";

    public static final String ADMIN_LOGIN = "it-admin";
    public static final String ADMIN_PASSWORD = "it-admin-password-123";

    public static final String CAPTURISTA_LOGIN = "it-capturista";
    public static final String CAPTURISTA_PASSWORD = "it-capturista-password-123";

    private TestJwt() {
    }

    /** Siembra un usuario de prueba directo por repositorio (evita depender de US4 para US2/US3). */
    public static Usuario sembrarUsuario(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                                          String login, String password, Rol rol) {
        return usuarioRepository.findByLogin(login)
                .orElseGet(() -> usuarioRepository.save(
                        new Usuario(login, passwordEncoder.encode(password), "Usuario de prueba", rol)));
    }

    public static String login(TestRestTemplate restTemplate, int port, String login, String password) {
        String url = "http://localhost:" + port + "/login";
        Map<String, String> body = Map.of("login", login, "password", password);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, body, Map.class);
        return (String) response.getBody().get("accessToken");
    }

    public static String loginAdmin(TestRestTemplate restTemplate, int port) {
        return login(restTemplate, port, ADMIN_LOGIN, ADMIN_PASSWORD);
    }

    public static HttpHeaders bearerHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        return headers;
    }
}
