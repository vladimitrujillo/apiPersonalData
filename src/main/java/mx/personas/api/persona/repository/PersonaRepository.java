package mx.personas.api.persona.repository;

import mx.personas.api.persona.model.Persona;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface PersonaRepository extends JpaRepository<Persona, UUID>, JpaSpecificationExecutor<Persona> {

    Optional<Persona> findByIdAndActivoTrue(UUID id);

    boolean existsByCorreoAndActivoTrue(String correo);

    boolean existsByCurpAndActivoTrue(String curp);

    boolean existsByCorreoAndActivoTrueAndIdNot(String correo, UUID id);

    boolean existsByCurpAndActivoTrueAndIdNot(String curp, UUID id);
}
