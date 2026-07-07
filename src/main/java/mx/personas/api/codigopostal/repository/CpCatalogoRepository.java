package mx.personas.api.codigopostal.repository;

import mx.personas.api.codigopostal.model.CpCatalogo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CpCatalogoRepository extends JpaRepository<CpCatalogo, Long> {

    List<CpCatalogo> findByCodigoPostal(String codigoPostal);

    /**
     * Busca colonias por coincidencia parcial de nombre, opcionalmente acotada por estado
     * y/o municipio (FR-016, US4). Usa el indice compuesto (estado, municipio, asentamiento).
     */
    @Query("""
            SELECT c FROM CpCatalogo c
            WHERE LOWER(c.asentamiento) LIKE LOWER(CONCAT('%', CAST(:nombre AS string), '%'))
            AND (:estado IS NULL OR LOWER(c.estado) LIKE LOWER(CONCAT('%', CAST(:estado AS string), '%')))
            AND (:municipio IS NULL OR LOWER(c.municipio) LIKE LOWER(CONCAT('%', CAST(:municipio AS string), '%')))
            ORDER BY c.estado, c.municipio, c.asentamiento
            """)
    List<CpCatalogo> buscarPorNombreParcial(@Param("nombre") String nombre, @Param("estado") String estado,
                                             @Param("municipio") String municipio);

    /**
     * Upsert idempotente de una fila del catalogo SEPOMEX (research.md #3 y #2): la
     * clave natural es (codigo_postal, id_asenta_cpcons); reimportar el mismo archivo no
     * duplica filas, y una fila cuyo contenido cambio se actualiza en su lugar. La
     * clausula WHERE ... IS DISTINCT FROM ... hace que un valor entrante identico al ya
     * almacenado no actualice nada (RETURNING vacio => Optional.empty(), "sin cambio");
     * xmax = 0 distingue si la fila devuelta vino de la rama INSERT (true) o DO UPDATE
     * (false). No se marca @Modifying: el RETURNING hace que esta sentencia produzca un
     * resultado (0 o 1 fila), no un conteo de filas afectadas.
     */
    @Query(value = """
            INSERT INTO cp_catalogo
                (codigo_postal, estado, municipio, asentamiento, tipo_asentamiento, id_asenta_cpcons,
                 created_at, updated_at)
            VALUES (:codigoPostal, :estado, :municipio, :asentamiento, :tipoAsentamiento, :idAsentaCpcons,
                    now(), now())
            ON CONFLICT (codigo_postal, id_asenta_cpcons) DO UPDATE SET
                estado = EXCLUDED.estado,
                municipio = EXCLUDED.municipio,
                asentamiento = EXCLUDED.asentamiento,
                tipo_asentamiento = EXCLUDED.tipo_asentamiento,
                updated_at = now()
            WHERE (cp_catalogo.estado, cp_catalogo.municipio, cp_catalogo.asentamiento, cp_catalogo.tipo_asentamiento)
                  IS DISTINCT FROM (EXCLUDED.estado, EXCLUDED.municipio, EXCLUDED.asentamiento, EXCLUDED.tipo_asentamiento)
            RETURNING (xmax = 0) AS insertado
            """, nativeQuery = true)
    Optional<Boolean> upsert(@Param("codigoPostal") String codigoPostal, @Param("estado") String estado,
                @Param("municipio") String municipio, @Param("asentamiento") String asentamiento,
                @Param("tipoAsentamiento") String tipoAsentamiento, @Param("idAsentaCpcons") String idAsentaCpcons);
}
