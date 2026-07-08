package mx.personas.api.profesion.repository;

import mx.personas.api.profesion.model.PersonaProfesion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PersonaProfesionRepository extends JpaRepository<PersonaProfesion, UUID> {

    boolean existsByPersonaIdAndProfesionIdAndActivoTrue(UUID personaId, Long profesionId);

    /** Solo asignaciones activas (FR-016, vista por defecto). */
    List<PersonaProfesion> findByPersonaIdAndActivoTrue(UUID personaId);

    /** Incluye retiradas; solo para ADMIN (FR-017). */
    List<PersonaProfesion> findByPersonaId(UUID personaId);

    /** Para retirar/validar que la asignacion pertenece a esa persona. */
    Optional<PersonaProfesion> findByIdAndPersonaId(UUID id, UUID personaId);

    /**
     * Directorio por profesión (US4, FR-018/FR-019): solo personas activas con
     * una asignación activa de esa profesión — una persona eliminada
     * lógicamente NUNCA aparece, aunque su asignación siga activa.
     */
    @Query("""
            SELECT pp FROM PersonaProfesion pp
            WHERE pp.profesion.id = :profesionId
            AND pp.activo = true
            AND pp.persona.activo = true
            """)
    Page<PersonaProfesion> findDirectorioByProfesionId(@Param("profesionId") Long profesionId, Pageable pageable);
}
