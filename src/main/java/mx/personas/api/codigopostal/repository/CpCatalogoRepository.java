package mx.personas.api.codigopostal.repository;

import mx.personas.api.codigopostal.model.CpCatalogo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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
     * Upsert idempotente de una fila del catalogo SEPOMEX (research.md #3): la clave
     * natural es (codigo_postal, id_asenta_cpcons); reimportar el mismo archivo no
     * duplica filas, y una fila cuyo contenido cambio se actualiza en su lugar.
     */
    @Modifying
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
            """, nativeQuery = true)
    void upsert(@Param("codigoPostal") String codigoPostal, @Param("estado") String estado,
                @Param("municipio") String municipio, @Param("asentamiento") String asentamiento,
                @Param("tipoAsentamiento") String tipoAsentamiento, @Param("idAsentaCpcons") String idAsentaCpcons);
}
