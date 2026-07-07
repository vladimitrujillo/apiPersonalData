package mx.personas.api.codigopostal.repository;

import mx.personas.api.codigopostal.model.CatalogoImportacion;
import mx.personas.api.codigopostal.model.CatalogoImportacion.EstadoImportacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CatalogoImportacionRepository extends JpaRepository<CatalogoImportacion, UUID> {

    /**
     * "Ya procesado" se determina por hash de contenido, no por nombre de archivo
     * (FR-003, research.md #5) — robusto a que SEPOMEX republique bajo el mismo nombre.
     */
    boolean existsByArchivoHashAndEstado(String archivoHash, EstadoImportacion estado);

    /** Bitácora de la corrida más reciente a la más antigua (US3). */
    Page<CatalogoImportacion> findAllByOrderByFechaInicioDesc(Pageable pageable);
}
