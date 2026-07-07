package mx.personas.api.codigopostal.importer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Ejecuta la importacion del catalogo SEPOMEX cuando la aplicacion arranca con el
 * argumento --import-sepomex=<ruta-al-csv> (ver quickstart.md).
 */
@Component
public class SepomexImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SepomexImportRunner.class);
    private static final String ARGUMENTO = "import-sepomex";

    private final SepomexImportService sepomexImportService;

    public SepomexImportRunner(SepomexImportService sepomexImportService) {
        this.sepomexImportService = sepomexImportService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption(ARGUMENTO)) {
            return;
        }
        String ruta = args.getOptionValues(ARGUMENTO).get(0);
        ResumenImportacion resumen = sepomexImportService.importar(Path.of(ruta));
        log.info("Importación del catálogo SEPOMEX completada desde {}: {} insertados, {} actualizados, "
                        + "{} sin cambio, {} rechazados",
                ruta, resumen.insertados(), resumen.actualizados(), resumen.sinCambio(), resumen.rechazados());
    }
}
