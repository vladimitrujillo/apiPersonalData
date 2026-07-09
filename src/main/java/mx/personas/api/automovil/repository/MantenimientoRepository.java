package mx.personas.api.automovil.repository;

import mx.personas.api.automovil.model.Mantenimiento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MantenimientoRepository extends JpaRepository<Mantenimiento, UUID> {

    /** Historial paginado, mas reciente primero (FR-022). */
    Page<Mantenimiento> findByAutomovilIdAndActivoTrueOrderByFechaDescCreatedAtDesc(UUID automovilId,
                                                                                     Pageable pageable);

    /** Para validar consistencia de kilometraje al crear (research.md #6). */
    Optional<Mantenimiento> findFirstByAutomovilIdAndActivoTrueOrderByFechaDescCreatedAtDesc(UUID automovilId);

    /** Igual, excluyendose a si mismo, para validar al editar. */
    Optional<Mantenimiento> findFirstByAutomovilIdAndActivoTrueAndIdNotOrderByFechaDescCreatedAtDesc(
            UUID automovilId, UUID excluirId);
}
