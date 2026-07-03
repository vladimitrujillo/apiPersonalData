package mx.personas.api.codigopostal.importer;

import mx.personas.api.codigopostal.repository.CpCatalogoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Importa el Catalogo Nacional de Codigos Postales de SEPOMEX a la tabla local cp_catalogo
 * de forma idempotente (FR-017, research.md #3).
 *
 * Formato de archivo esperado: CSV con encabezado y columnas en este orden:
 * codigoPostal,estado,municipio,asentamiento,tipoAsentamiento,idAsentaCpcons
 *
 * Reimportar el mismo archivo no duplica filas (upsert sobre la clave natural
 * codigo_postal + id_asenta_cpcons); una fila cuyo contenido cambio en una version mas
 * reciente del catalogo se actualiza en su lugar.
 */
@Service
public class SepomexImportService {

    private final CpCatalogoRepository cpCatalogoRepository;

    public SepomexImportService(CpCatalogoRepository cpCatalogoRepository) {
        this.cpCatalogoRepository = cpCatalogoRepository;
    }

    @Transactional
    public int importar(Path archivoCsv) {
        try (BufferedReader lector = Files.newBufferedReader(archivoCsv, StandardCharsets.UTF_8)) {
            String linea = lector.readLine(); // encabezado, se descarta
            int filasImportadas = 0;
            while ((linea = lector.readLine()) != null) {
                if (linea.isBlank()) {
                    continue;
                }
                importarLinea(linea);
                filasImportadas++;
            }
            return filasImportadas;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo del catálogo SEPOMEX: " + archivoCsv, e);
        }
    }

    private void importarLinea(String linea) {
        String[] columnas = linea.split(",", -1);
        if (columnas.length < 6) {
            throw new IllegalArgumentException("Línea de catálogo SEPOMEX con formato inválido: " + linea);
        }
        String codigoPostal = columnas[0].trim();
        String estado = columnas[1].trim();
        String municipio = columnas[2].trim();
        String asentamiento = columnas[3].trim();
        String tipoAsentamiento = columnas[4].trim();
        String idAsentaCpcons = columnas[5].trim();

        cpCatalogoRepository.upsert(codigoPostal, estado, municipio, asentamiento, tipoAsentamiento, idAsentaCpcons);
    }
}
