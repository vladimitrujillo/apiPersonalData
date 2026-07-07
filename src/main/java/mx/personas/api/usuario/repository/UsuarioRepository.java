package mx.personas.api.usuario.repository;

import jakarta.persistence.QueryHint;
import mx.personas.api.usuario.model.Rol;
import mx.personas.api.usuario.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    /**
     * FlushMode COMMIT: SecurityAuditorAware.getCurrentAuditor() invoca este metodo desde
     * dentro de un callback @PreUpdate (touchForUpdate) de otra entidad Auditable que ya
     * esta siendo flusheada. Sin este hint, la consulta dispara un auto-flush que
     * reprocesa esa misma entidad dirty, reinvocando el callback recursivamente hasta
     * StackOverflowError.
     */
    @QueryHints(@QueryHint(name = "org.hibernate.flushMode", value = "COMMIT"))
    Optional<Usuario> findByLogin(String login);

    boolean existsByLogin(String login);

    boolean existsByRol(Rol rol);
}
