package mx.personas.api.persona.repository;

import mx.personas.api.persona.model.Persona;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface PersonaRepository extends JpaRepository<Persona, UUID>, JpaSpecificationExecutor<Persona> {

    Optional<Persona> findByIdAndActivoTrue(UUID id);

    boolean existsByCorreoAndActivoTrue(String correo);

    boolean existsByCorreoAndActivoTrueAndIdNot(String correo, UUID id);

    /** Sin filtro de activo: la CURP es global, a lo sumo una fila puede coincidir (D2). */
    Optional<Persona> findByCurp(String curp);

    /** Para incluir el id de la persona activa en el mensaje accionable de restaurar (FR-009). */
    Optional<Persona> findByCorreoAndActivoTrue(String correo);

    /** Vista dedicada de personas eliminadas logicamente (US4). */
    Page<Persona> findByActivoFalse(Pageable pageable);
}
