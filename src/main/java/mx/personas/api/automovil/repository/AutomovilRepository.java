package mx.personas.api.automovil.repository;

import mx.personas.api.automovil.model.Automovil;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AutomovilRepository extends JpaRepository<Automovil, UUID> {

    boolean existsByPlacasAndActivoTrue(String placas);

    boolean existsByPlacasAndActivoTrueAndIdNot(String placas, UUID id);

    /** VIN es global, sin condicion de estado; solo se usa al crear (inmutable despues). */
    boolean existsByVin(String vin);

    List<Automovil> findByPersonaId(UUID personaId);
}
