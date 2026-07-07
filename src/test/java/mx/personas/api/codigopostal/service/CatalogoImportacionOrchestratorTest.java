package mx.personas.api.codigopostal.service;

import mx.personas.api.codigopostal.importer.ResumenImportacion;
import mx.personas.api.codigopostal.importer.SepomexImportService;
import mx.personas.api.codigopostal.model.CatalogoImportacion;
import mx.personas.api.codigopostal.model.CatalogoImportacion.EstadoImportacion;
import mx.personas.api.codigopostal.model.CatalogoImportacion.OrigenImportacion;
import mx.personas.api.codigopostal.repository.AdvisoryLockRepository;
import mx.personas.api.codigopostal.repository.CatalogoImportacionRepository;
import mx.personas.api.common.error.CatalogoArchivoInvalidoException;
import mx.personas.api.common.error.CatalogoImportacionEnCursoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre la orquestación de CatalogoImportacionOrchestrator: candado no disponible,
 * éxito con/sin cambios (evicción condicional, research.md §6), y archivo inválido
 * (FR-011, FR-014). El PlatformTransactionManager se mockea de forma permisiva - la
 * atomicidad real de la transacción se verifica en los IT (Testcontainers).
 */
@ExtendWith(MockitoExtension.class)
class CatalogoImportacionOrchestratorTest {

    @Mock
    private AdvisoryLockRepository advisoryLockRepository;

    @Mock
    private SepomexImportService sepomexImportService;

    @Mock
    private CatalogoImportacionRepository catalogoImportacionRepository;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private Cache cache;

    private CatalogoImportacionOrchestrator orchestrator() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        return new CatalogoImportacionOrchestrator(
                advisoryLockRepository, sepomexImportService, catalogoImportacionRepository, cacheManager,
                transactionManager);
    }

    @Test
    void candadoNoDisponibleRegistraRechazoPorConcurrenciaYLanzaExcepcionSinLlamarAlImportador() {
        when(advisoryLockRepository.intentarTomarCandado()).thenReturn(false);

        assertThatThrownBy(() -> orchestrator().ejecutar(
                Path.of("/tmp/no-importa.csv"), "archivo.csv", "hash-1",
                OrigenImportacion.MANUAL, UUID.randomUUID()))
                .isInstanceOf(CatalogoImportacionEnCursoException.class);

        verify(sepomexImportService, never()).importar(any());
        ArgumentCaptor<CatalogoImportacion> captor = ArgumentCaptor.forClass(CatalogoImportacion.class);
        verify(catalogoImportacionRepository).save(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo(EstadoImportacion.RECHAZADA_CONCURRENCIA);
    }

    @Test
    void exitoConCambiosRegistraExitoYEvictaElCache() {
        when(advisoryLockRepository.intentarTomarCandado()).thenReturn(true);
        ResumenImportacion resumen = new ResumenImportacion(5, 2, 100, 0, List.of());
        when(sepomexImportService.importar(any())).thenReturn(resumen);
        when(cacheManager.getCache("codigosPostales")).thenReturn(cache);

        ResumenImportacion resultado = orchestrator().ejecutar(
                Path.of("/tmp/archivo.csv"), "archivo.csv", "hash-2",
                OrigenImportacion.PROGRAMADA, null);

        assertThat(resultado).isEqualTo(resumen);
        ArgumentCaptor<CatalogoImportacion> captor = ArgumentCaptor.forClass(CatalogoImportacion.class);
        verify(catalogoImportacionRepository).save(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo(EstadoImportacion.EXITO);
        assertThat(captor.getValue().getInsertados()).isEqualTo(5);
        assertThat(captor.getValue().getArchivoHash()).isEqualTo("hash-2");
        verify(cache).clear();
    }

    @Test
    void exitoSinCambiosNoEvictaElCache() {
        when(advisoryLockRepository.intentarTomarCandado()).thenReturn(true);
        ResumenImportacion resumen = new ResumenImportacion(0, 0, 150, 0, List.of());
        when(sepomexImportService.importar(any())).thenReturn(resumen);

        orchestrator().ejecutar(Path.of("/tmp/archivo.csv"), "archivo.csv", "hash-3",
                OrigenImportacion.MANUAL, UUID.randomUUID());

        verify(cacheManager, never()).getCache(any());
    }

    @Test
    void archivoInvalidoRegistraErrorConDetalleYRelanzaLaExcepcionSinEvictar() {
        when(advisoryLockRepository.intentarTomarCandado()).thenReturn(true);
        CatalogoArchivoInvalidoException excepcion = new CatalogoArchivoInvalidoException("encabezado inválido");
        when(sepomexImportService.importar(any())).thenThrow(excepcion);

        assertThatThrownBy(() -> orchestrator().ejecutar(
                Path.of("/tmp/archivo.csv"), "archivo.csv", "hash-4",
                OrigenImportacion.MANUAL, UUID.randomUUID()))
                .isSameAs(excepcion);

        ArgumentCaptor<CatalogoImportacion> captor = ArgumentCaptor.forClass(CatalogoImportacion.class);
        verify(catalogoImportacionRepository).save(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo(EstadoImportacion.ERROR);
        assertThat(captor.getValue().getDetalleError()).isEqualTo("encabezado inválido");
        verify(cacheManager, never()).getCache(any());
    }
}
