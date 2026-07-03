package mx.personas.api.persona.repository;

import mx.personas.api.persona.model.Persona;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PersonaRepository extends JpaRepository<Persona, UUID>, JpaSpecificationExecutor<Persona> {

    Optional<Persona> findByIdAndActivoTrue(UUID id);

    boolean existsByCorreoAndActivoTrue(String correo);

    boolean existsByCurpAndActivoTrue(String curp);

    boolean existsByCorreoAndActivoTrueAndIdNot(String correo, UUID id);

    boolean existsByCurpAndActivoTrueAndIdNot(String curp, UUID id);

    /**
     * Lista personas activas con filtros opcionales (nulos = sin filtrar) por coincidencia
     * parcial de nombre (nombres + apellidos) y por municipio/estado de su direccion
     * vigente (FR-003, US2).
     */
    @Query("""
            SELECT p FROM Persona p
            WHERE p.activo = true
            AND (:nombre IS NULL OR LOWER(CONCAT(p.nombres, ' ', p.apellidos))
                LIKE LOWER(CONCAT('%', CAST(:nombre AS string), '%')))
            AND (:municipio IS NULL OR EXISTS (
                SELECT 1 FROM Direccion d WHERE d.persona = p
                AND LOWER(d.municipio) LIKE LOWER(CONCAT('%', CAST(:municipio AS string), '%'))))
            AND (:estado IS NULL OR EXISTS (
                SELECT 1 FROM Direccion d WHERE d.persona = p
                AND LOWER(d.estado) LIKE LOWER(CONCAT('%', CAST(:estado AS string), '%'))))
            """)
    Page<Persona> buscarActivas(@Param("nombre") String nombre, @Param("municipio") String municipio,
                                 @Param("estado") String estado, Pageable pageable);
}
