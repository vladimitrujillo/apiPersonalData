package mx.personas.api.codigopostal.importer;

import mx.personas.api.codigopostal.model.CatalogoImportacion.EstadoImportacion;
import mx.personas.api.codigopostal.model.CatalogoImportacion.OrigenImportacion;
import mx.personas.api.codigopostal.repository.CatalogoImportacionRepository;
import mx.personas.api.codigopostal.service.CatalogoImportacionOrchestrator;
import mx.personas.api.common.error.CatalogoArchivoInvalidoException;
import mx.personas.api.common.error.CatalogoImportacionEnCursoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

/**
 * Revisa periódicamente un directorio en busca de archivos del catálogo SEPOMEX no
 * procesados antes, y los importa vía el orquestador compartido (US1, FR-001, FR-002).
 *
 * "Ya procesado" se determina por el hash SHA-256 del contenido del archivo, no por su
 * nombre (FR-003, research.md §5) — el catálogo oficial de SEPOMEX se publica
 * típicamente bajo un nombre fijo, así que cada actualización periódica legítima llega
 * con el mismo nombre y contenido nuevo.
 */
@Component
public class SepomexImportScheduler {

    private static final Logger log = LoggerFactory.getLogger(SepomexImportScheduler.class);

    private final CatalogoImportacionOrchestrator orchestrator;
    private final CatalogoImportacionRepository catalogoImportacionRepository;
    private final Path directorioEntrada;
    private final Path directorioProcesados;

    public SepomexImportScheduler(CatalogoImportacionOrchestrator orchestrator,
                                   CatalogoImportacionRepository catalogoImportacionRepository,
                                   @Value("${app.sepomex.directorio-entrada}") String directorioEntrada,
                                   @Value("${app.sepomex.directorio-procesados}") String directorioProcesados) {
        this.orchestrator = orchestrator;
        this.catalogoImportacionRepository = catalogoImportacionRepository;
        this.directorioEntrada = Path.of(directorioEntrada);
        this.directorioProcesados = Path.of(directorioProcesados);
    }

    @Scheduled(cron = "${app.sepomex.import-cron:0 0 3 * * MON}")
    public void revisarDirectorioEImportarArchivosNuevos() {
        List<Path> archivos = listarArchivos(directorioEntrada);
        for (Path archivo : archivos) {
            procesarArchivo(archivo);
        }
    }

    private void procesarArchivo(Path archivo) {
        String nombreArchivo = archivo.getFileName().toString();
        String hash = ArchivoHashCalculator.calcular(archivo);

        if (catalogoImportacionRepository.existsByArchivoHashAndEstado(hash, EstadoImportacion.EXITO)) {
            log.debug("Archivo '{}' ya tiene una corrida exitosa registrada (hash={}); se omite.",
                    nombreArchivo, hash);
            return;
        }

        try {
            orchestrator.ejecutar(archivo, nombreArchivo, hash, OrigenImportacion.PROGRAMADA, null);
            archivarArchivo(archivo, nombreArchivo);
        } catch (CatalogoImportacionEnCursoException ex) {
            log.info("Ciclo programado: importación de '{}' saltada, ya hay una corrida en curso.", nombreArchivo);
        } catch (CatalogoArchivoInvalidoException ex) {
            log.warn("Ciclo programado: archivo '{}' con estructura inválida, no se archiva: {}",
                    nombreArchivo, ex.getMessage());
        }
    }

    private void archivarArchivo(Path archivo, String nombreArchivo) {
        try {
            Files.createDirectories(directorioProcesados);
            Files.move(archivo, directorioProcesados.resolve(nombreArchivo), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("No se pudo archivar '{}' tras una importación exitosa; la bitácora ya registra el éxito, "
                    + "así que no se reimportará en un ciclo posterior aunque el archivo siga presente.",
                    nombreArchivo, e);
        }
    }

    private List<Path> listarArchivos(Path directorio) {
        if (!Files.isDirectory(directorio)) {
            return List.of();
        }
        try (Stream<Path> entradas = Files.list(directorio)) {
            return entradas.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo listar el directorio de entrada: " + directorio, e);
        }
    }
}
