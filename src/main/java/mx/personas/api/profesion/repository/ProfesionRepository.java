package mx.personas.api.profesion.repository;

import mx.personas.api.profesion.model.Profesion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProfesionRepository extends JpaRepository<Profesion, Long> {

    /**
     * Busca una profesion por nombre normalizado (insensible a mayusculas y
     * acentos), misma normalizacion que el indice unico de V7
     * (research.md §1, §6) para que el 409 de negocio y el indice nunca
     * discrepen.
     */
    @Query(value = """
            SELECT * FROM profesion
            WHERE LOWER(unaccent_immutable(nombre)) = LOWER(unaccent_immutable(:nombre))
            """, nativeQuery = true)
    Optional<Profesion> findByNombreNormalizado(@Param("nombre") String nombre);

    Page<Profesion> findByActivoTrue(Pageable pageable);
}
