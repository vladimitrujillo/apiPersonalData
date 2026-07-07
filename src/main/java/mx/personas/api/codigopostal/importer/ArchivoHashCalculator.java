package mx.personas.api.codigopostal.importer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Calcula el hash SHA-256 del contenido de un archivo — clave real de "ya procesado"
 * (FR-003, research.md §5), usada tanto por {@link SepomexImportScheduler} (para
 * decidir si reimportar) como por el disparo manual (solo para dejar la bitácora
 * completa, sin afectar su decisión de ejecutar).
 */
public final class ArchivoHashCalculator {

    private ArchivoHashCalculator() {
    }

    public static String calcular(Path archivo) {
        try {
            byte[] contenido = Files.readAllBytes(archivo);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(contenido));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo para calcular su hash: " + archivo, e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
