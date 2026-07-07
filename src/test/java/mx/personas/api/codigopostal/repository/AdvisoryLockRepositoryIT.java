package mx.personas.api.codigopostal.repository;

import mx.personas.api.common.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-009: nunca dos importaciones a la vez. El candado es un advisory lock de alcance
 * de transaccion (research.md #3): exactamente una de dos transacciones concurrentes lo
 * obtiene; al terminar esa transaccion, se libera solo y una tercera puede tomarlo.
 */
class AdvisoryLockRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private AdvisoryLockRepository advisoryLockRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void exactamenteUnaDeDosTransaccionesConcurrentesObtieneElCandado() throws InterruptedException {
        TransactionTemplate plantilla = new TransactionTemplate(transactionManager);
        AtomicBoolean primeraObtuvo = new AtomicBoolean();
        AtomicBoolean segundaObtuvo = new AtomicBoolean();
        CountDownLatch primeraTomoElCandado = new CountDownLatch(1);
        CountDownLatch puedeTerminarPrimera = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            executor.submit(() -> plantilla.executeWithoutResult(status -> {
                primeraObtuvo.set(advisoryLockRepository.intentarTomarCandado());
                primeraTomoElCandado.countDown();
                await(puedeTerminarPrimera);
            }));

            assertThat(primeraTomoElCandado.await(5, TimeUnit.SECONDS)).isTrue();

            executor.submit(() -> plantilla.executeWithoutResult(
                    status -> segundaObtuvo.set(advisoryLockRepository.intentarTomarCandado())))
                    .get();

            puedeTerminarPrimera.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

            assertThat(primeraObtuvo).isTrue();
            assertThat(segundaObtuvo).isFalse();

            boolean terceraObtuvo = plantilla.execute(status -> advisoryLockRepository.intentarTomarCandado());
            assertThat(terceraObtuvo).isTrue();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            executor.shutdownNow();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
