package mx.personas.api.usuario.repository;

import mx.personas.api.usuario.model.Rol;
import mx.personas.api.usuario.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByLogin(String login);

    boolean existsByLogin(String login);

    boolean existsByRol(Rol rol);
}
