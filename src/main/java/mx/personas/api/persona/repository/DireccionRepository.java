package mx.personas.api.persona.repository;

import mx.personas.api.persona.model.Direccion;
import mx.personas.api.persona.model.Persona;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DireccionRepository extends JpaRepository<Direccion, UUID> {

    Optional<Direccion> findFirstByPersonaOrderByUpdatedAtDesc(Persona persona);
}
