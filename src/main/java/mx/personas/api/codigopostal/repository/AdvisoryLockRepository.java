package mx.personas.api.codigopostal.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

/**
 * Candado de ejecucion unica de importaciones, con un advisory lock nativo de
 * PostgreSQL de alcance de transaccion (research.md #3) — se libera automaticamente al
 * terminar la transaccion (COMMIT o ROLLBACK), a diferencia de la variante de sesion,
 * que podria quedar retenida en una conexion reciclada por el pool.
 */
@Repository
public class AdvisoryLockRepository {

    /** Clave numerica fija: hash de un identificador unico de este proceso de importacion. */
    private static final long CLAVE_CANDADO_IMPORTACION = "sepomex-importacion".hashCode();

    @PersistenceContext
    private EntityManager entityManager;

    /** true si el candado se obtuvo (nadie mas lo tenia); false si ya estaba tomado. */
    public boolean intentarTomarCandado() {
        Object resultado = entityManager
                .createNativeQuery("SELECT pg_try_advisory_xact_lock(:clave)")
                .setParameter("clave", CLAVE_CANDADO_IMPORTACION)
                .getSingleResult();
        return (Boolean) resultado;
    }
}
