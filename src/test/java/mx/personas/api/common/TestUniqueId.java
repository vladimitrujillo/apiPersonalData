package mx.personas.api.common;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generador de sufijos unicos para datos de prueba (p. ej. la homoclave final de un CURP
 * de prueba: 2 caracteres alfanumericos, [A-Z0-9]{2}). Usa un contador atomico en vez de
 * System.nanoTime() % 10 (solo 10 valores posibles) para evitar colisiones entre los
 * distintos metodos/clases de prueba que comparten la misma base de datos dentro de una
 * misma ejecucion de la suite (research.md #9, patron de contenedor singleton).
 */
public final class TestUniqueId {

    private static final String ALFABETO = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final AtomicInteger CONTADOR = new AtomicInteger();

    private TestUniqueId() {
    }

    /** Dos caracteres alfanumericos unicos por llamada (hasta 36*36 = 1296 combinaciones). */
    public static String homoclave() {
        int n = CONTADOR.getAndIncrement() % (ALFABETO.length() * ALFABETO.length());
        return "" + ALFABETO.charAt(n / ALFABETO.length()) + ALFABETO.charAt(n % ALFABETO.length());
    }
}
