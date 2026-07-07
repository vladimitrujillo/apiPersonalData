package mx.personas.api.persona.repository;

import mx.personas.api.persona.model.Persona;
import mx.personas.api.persona.model.PersonaHistorial;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PersonaHistorialRepository extends JpaRepository<PersonaHistorial, UUID> {

    Page<PersonaHistorial> findByPersonaOrderByFechaDesc(Persona persona, Pageable pageable);
}
