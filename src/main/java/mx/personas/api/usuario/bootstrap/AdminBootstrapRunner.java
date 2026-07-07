package mx.personas.api.usuario.bootstrap;

import mx.personas.api.usuario.model.Rol;
import mx.personas.api.usuario.model.Usuario;
import mx.personas.api.usuario.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Siembra el primer ADMIN de forma idempotente a partir de configuracion externa, sin
 * credenciales fijas en el codigo (FR-017, research.md #6).
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final String bootstrapLogin;
    private final String bootstrapPassword;

    public AdminBootstrapRunner(UsuarioRepository usuarioRepository,
                                 PasswordEncoder passwordEncoder,
                                 @Value("${app.security.admin-bootstrap-login}") String bootstrapLogin,
                                 @Value("${app.security.admin-bootstrap-password}") String bootstrapPassword) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapLogin = bootstrapLogin;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (usuarioRepository.existsByRol(Rol.ADMIN)) {
            return;
        }
        if (bootstrapLogin == null || bootstrapLogin.isBlank()
                || bootstrapPassword == null || bootstrapPassword.isBlank()) {
            log.warn("No existe ningun ADMIN y no se proporcionaron ADMIN_BOOTSTRAP_LOGIN/"
                    + "ADMIN_BOOTSTRAP_PASSWORD; el arranque continua sin sembrar un ADMIN inicial.");
            return;
        }
        Usuario admin = new Usuario(bootstrapLogin, passwordEncoder.encode(bootstrapPassword),
                "Administrador inicial", Rol.ADMIN);
        usuarioRepository.save(admin);
        log.info("ADMIN inicial sembrado con login '{}'.", bootstrapLogin);
    }
}
