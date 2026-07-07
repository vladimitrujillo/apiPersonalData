package mx.personas.api.codigopostal.service;

import mx.personas.api.codigopostal.importer.ResumenImportacion;
import mx.personas.api.codigopostal.importer.SepomexImportService;
import mx.personas.api.codigopostal.model.CatalogoImportacion;
import mx.personas.api.codigopostal.model.CatalogoImportacion.OrigenImportacion;
import mx.personas.api.codigopostal.repository.AdvisoryLockRepository;
import mx.personas.api.codigopostal.repository.CatalogoImportacionRepository;
import mx.personas.api.common.error.CatalogoArchivoInvalidoException;
import mx.personas.api.common.error.CatalogoImportacionEnCursoException;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Orquesta candado + validación + upserts + bitácora + evicción de caché para una
 * corrida de importación (research.md §3, §6) — pieza compartida por el job programado
 * (US1) y el disparo manual (US2).
 *
 * El candado ({@code pg_try_advisory_xact_lock}) y todo el trabajo de la corrida
 * (validación, upserts, fila de bitácora exitosa/con error) viven en **una sola
 * transacción** ({@link #ejecutarDentroDeTransaccion}), para que el candado siga
 * retenido durante todo el trabajo y se libere automáticamente al terminar esa
 * transacción. La fila de bitácora de un rechazo por concurrencia se escribe en una
 * **transacción separada y corta**, ya que en ese caso nunca se obtuvo el candado ni se
 * abrió la transacción principal.
 */
@Service
public class CatalogoImportacionOrchestrator {

    private final AdvisoryLockRepository advisoryLockRepository;
    private final SepomexImportService sepomexImportService;
    private final CatalogoImportacionRepository catalogoImportacionRepository;
    private final CacheManager cacheManager;
    private final TransactionTemplate transactionTemplate;

    public CatalogoImportacionOrchestrator(AdvisoryLockRepository advisoryLockRepository,
                                            SepomexImportService sepomexImportService,
                                            CatalogoImportacionRepository catalogoImportacionRepository,
                                            CacheManager cacheManager,
                                            PlatformTransactionManager transactionManager) {
        this.advisoryLockRepository = advisoryLockRepository;
        this.sepomexImportService = sepomexImportService;
        this.catalogoImportacionRepository = catalogoImportacionRepository;
        this.cacheManager = cacheManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * @throws CatalogoImportacionEnCursoException si el candado no está disponible
     * @throws CatalogoArchivoInvalidoException si la estructura del archivo es inválida
     */
    public ResumenImportacion ejecutar(Path archivo, String nombreArchivo, String archivoHash,
                                        OrigenImportacion origen, UUID usuarioId) {
        Resultado resultado = transactionTemplate.execute(status ->
                ejecutarDentroDeTransaccion(archivo, nombreArchivo, archivoHash, origen, usuarioId));

        if (resultado.candadoNoDisponible) {
            registrarRechazoPorConcurrencia(origen, usuarioId, nombreArchivo, archivoHash);
            throw new CatalogoImportacionEnCursoException(
                    "Ya hay una importación en curso; inténtelo de nuevo más tarde");
        }
        if (resultado.excepcionArchivoInvalido != null) {
            throw resultado.excepcionArchivoInvalido;
        }
        return resultado.resumen;
    }

    private Resultado ejecutarDentroDeTransaccion(Path archivo, String nombreArchivo, String archivoHash,
                                                   OrigenImportacion origen, UUID usuarioId) {
        if (!advisoryLockRepository.intentarTomarCandado()) {
            return Resultado.deCandadoNoDisponible();
        }

        long inicio = System.currentTimeMillis();
        try {
            ResumenImportacion resumen = sepomexImportService.importar(archivo);
            long duracionMs = System.currentTimeMillis() - inicio;
            catalogoImportacionRepository.save(CatalogoImportacion.exitosa(
                    origen, usuarioId, nombreArchivo, archivoHash, duracionMs,
                    resumen.insertados(), resumen.actualizados(), resumen.sinCambio(), resumen.rechazados()));
            if (resumen.tuvoCambios()) {
                evictarCacheCodigosPostales();
            }
            return Resultado.exitoso(resumen);
        } catch (CatalogoArchivoInvalidoException ex) {
            long duracionMs = System.currentTimeMillis() - inicio;
            catalogoImportacionRepository.save(CatalogoImportacion.fallida(
                    origen, usuarioId, nombreArchivo, archivoHash, duracionMs, ex.getMessage()));
            return Resultado.archivoInvalido(ex);
        }
    }

    private void registrarRechazoPorConcurrencia(OrigenImportacion origen, UUID usuarioId, String nombreArchivo,
                                                  String archivoHash) {
        transactionTemplate.executeWithoutResult(status -> catalogoImportacionRepository.save(
                CatalogoImportacion.rechazadaPorConcurrencia(origen, usuarioId, nombreArchivo, archivoHash)));
    }

    private void evictarCacheCodigosPostales() {
        Cache cache = cacheManager.getCache("codigosPostales");
        if (cache != null) {
            cache.clear();
        }
    }

    private record Resultado(boolean candadoNoDisponible, ResumenImportacion resumen,
                              CatalogoArchivoInvalidoException excepcionArchivoInvalido) {

        static Resultado deCandadoNoDisponible() {
            return new Resultado(true, null, null);
        }

        static Resultado exitoso(ResumenImportacion resumen) {
            return new Resultado(false, resumen, null);
        }

        static Resultado archivoInvalido(CatalogoArchivoInvalidoException ex) {
            return new Resultado(false, null, ex);
        }
    }
}
