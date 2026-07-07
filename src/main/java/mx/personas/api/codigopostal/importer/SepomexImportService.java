package mx.personas.api.codigopostal.importer;

import mx.personas.api.codigopostal.repository.CpCatalogoRepository;
import mx.personas.api.common.error.CatalogoArchivoInvalidoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Importa el Catalogo Nacional de Codigos Postales de SEPOMEX a la tabla local cp_catalogo
 * de forma idempotente (FR-017, research.md #3 de 001).
 *
 * Formato de archivo esperado: texto delimitado por "|" (pipe), con encabezado, y
 * columnas en este orden:
 * codigoPostal|estado|municipio|asentamiento|tipoAsentamiento|idAsentaCpcons
 *
 * Se usa "|" en vez de "," para evitar ambiguedad cuando un nombre de estado, municipio
 * o asentamiento contiene una coma.
 *
 * Reimportar el mismo archivo no duplica filas (upsert sobre la clave natural
 * codigo_postal + id_asenta_cpcons); una fila cuyo contenido cambio en una version mas
 * reciente del catalogo se actualiza en su lugar.
 *
 * Extendido por 006-sepomex-import-automatico (research.md #1): antes de aplicar
 * cualquier upsert, se valida la estructura general del archivo completo (encabezado y
 * numero de columnas por linea) - si falla, el catalogo queda intacto por construccion
 * (FR-011). Superada esa validacion, cada fila se procesa individualmente: una fila con
 * un campo invalido (p. ej. un codigo postal que no son 5 digitos) se cuenta como
 * rechazada y se continua con la siguiente, sin abortar el resto (FR-012).
 */
@Service
public class SepomexImportService {

    private static final String ENCABEZADO_ESPERADO =
            "codigoPostal|estado|municipio|asentamiento|tipoAsentamiento|idAsentaCpcons";
    private static final int NUMERO_COLUMNAS = 6;

    private final CpCatalogoRepository cpCatalogoRepository;

    public SepomexImportService(CpCatalogoRepository cpCatalogoRepository) {
        this.cpCatalogoRepository = cpCatalogoRepository;
    }

    // noRollbackFor: CatalogoArchivoInvalidoException la captura y maneja
    // CatalogoImportacionOrchestrator (registra la fila de bitacora ERROR en la MISMA
    // transaccion antes de relanzarla) - sin esto, el interceptor de @Transactional de
    // este metodo marcaria la transaccion compartida como rollback-only en cuanto la
    // excepcion se lanza aqui, y el commit final del orquestador fallaria con
    // UnexpectedRollbackException aunque el catch ya la haya manejado.
    @Transactional(noRollbackFor = CatalogoArchivoInvalidoException.class)
    public ResumenImportacion importar(Path archivoCsv) {
        validarEstructura(archivoCsv);

        int insertados = 0;
        int actualizados = 0;
        int sinCambio = 0;
        List<String> detallesRechazados = new ArrayList<>();

        try (BufferedReader lector = Files.newBufferedReader(archivoCsv, StandardCharsets.UTF_8)) {
            lector.readLine(); // encabezado, ya validado, se descarta
            String linea;
            int numeroLinea = 1;
            while ((linea = lector.readLine()) != null) {
                numeroLinea++;
                if (linea.isBlank()) {
                    continue;
                }
                try {
                    Optional<Boolean> resultado = importarLinea(linea);
                    if (resultado.isEmpty()) {
                        sinCambio++;
                    } else if (resultado.get()) {
                        insertados++;
                    } else {
                        actualizados++;
                    }
                } catch (IllegalArgumentException ex) {
                    detallesRechazados.add("Línea " + numeroLinea + ": " + ex.getMessage());
                }
            }
            return new ResumenImportacion(insertados, actualizados, sinCambio,
                    detallesRechazados.size(), detallesRechazados);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo del catálogo SEPOMEX: " + archivoCsv, e);
        }
    }

    /**
     * Lee el archivo completo una vez para validar que el encabezado sea el esperado y
     * que cada linea tenga exactamente 6 columnas, antes de aplicar ningun upsert
     * (FR-011). El catalogo no se toca en absoluto si esta validacion falla.
     */
    private void validarEstructura(Path archivoCsv) {
        try (BufferedReader lector = Files.newBufferedReader(archivoCsv, StandardCharsets.UTF_8)) {
            String encabezado = lector.readLine();
            if (encabezado == null || !encabezado.strip().equals(ENCABEZADO_ESPERADO)) {
                throw new CatalogoArchivoInvalidoException(
                        "Encabezado del catálogo SEPOMEX inválido: se esperaba '"
                                + ENCABEZADO_ESPERADO + "'");
            }
            String linea;
            int numeroLinea = 1;
            while ((linea = lector.readLine()) != null) {
                numeroLinea++;
                if (linea.isBlank()) {
                    continue;
                }
                String[] columnas = linea.split("\\|", -1);
                if (columnas.length != NUMERO_COLUMNAS) {
                    throw new CatalogoArchivoInvalidoException(
                            "Línea " + numeroLinea + " no tiene " + NUMERO_COLUMNAS
                                    + " columnas separadas por '|': " + linea);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo del catálogo SEPOMEX: " + archivoCsv, e);
        }
    }

    /**
     * @return vacio = fila sin cambio; true = insertada; false = actualizada con cambio real.
     * @throws IllegalArgumentException si un campo individual es invalido (fila rechazada, FR-012).
     */
    private Optional<Boolean> importarLinea(String linea) {
        String[] columnas = linea.split("\\|", -1);
        String codigoPostal = columnas[0].trim();
        String estado = columnas[1].trim();
        String municipio = columnas[2].trim();
        String asentamiento = columnas[3].trim();
        String tipoAsentamiento = columnas[4].trim();
        String idAsentaCpcons = columnas[5].trim();

        if (!codigoPostal.matches("\\d{5}")) {
            throw new IllegalArgumentException("código postal '" + codigoPostal + "' no son 5 dígitos");
        }

        return cpCatalogoRepository.upsert(
                codigoPostal, estado, municipio, asentamiento, tipoAsentamiento, idAsentaCpcons);
    }
}
