package mx.personas.api.codigopostal.importer;

import mx.personas.api.codigopostal.model.CatalogoImportacion;
import mx.personas.api.codigopostal.model.CatalogoImportacion.EstadoImportacion;
import mx.personas.api.codigopostal.model.CatalogoImportacion.OrigenImportacion;
import mx.personas.api.codigopostal.repository.CatalogoImportacionRepository;
import mx.personas.api.codigopostal.repository.CpCatalogoRepository;
import mx.personas.api.common.AbstractIntegrationTest;
import mx.personas.api.common.TestUniqueId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US1: revisión periódica del directorio de entrada e importación automática de
 * archivos no procesados antes (FR-001, FR-002, FR-003). Invoca el método anotado
 * `@Scheduled` directamente — no espera al cron real.
 */
@TestPropertySource(properties = {
        "app.sepomex.directorio-entrada=${java.io.tmpdir}/sepomex-scheduler-it-entrada",
        "app.sepomex.directorio-procesados=${java.io.tmpdir}/sepomex-scheduler-it-procesados"
})
class SepomexImportSchedulerIT extends AbstractIntegrationTest {

    @Autowired
    private SepomexImportScheduler scheduler;

    @Autowired
    private CpCatalogoRepository cpCatalogoRepository;

    @Autowired
    private CatalogoImportacionRepository catalogoImportacionRepository;

    private Path directorioEntrada;
    private Path directorioProcesados;

    @BeforeEach
    void preparar() throws IOException {
        directorioEntrada = Path.of(System.getProperty("java.io.tmpdir"), "sepomex-scheduler-it-entrada");
        directorioProcesados = Path.of(System.getProperty("java.io.tmpdir"), "sepomex-scheduler-it-procesados");
        Files.createDirectories(directorioEntrada);
        Files.createDirectories(directorioProcesados);
        limpiarDirectorio(directorioEntrada);
        limpiarDirectorio(directorioProcesados);
    }

    private void limpiarDirectorio(Path directorio) throws IOException {
        try (var entradas = Files.list(directorio)) {
            for (Path p : entradas.toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    private String csvValido(String cp) {
        return """
                codigoPostal|estado|municipio|asentamiento|tipoAsentamiento|idAsentaCpcons
                %s|Ciudad de México|Iztapalapa|Barrio Scheduler|Colonia|1
                """.formatted(cp);
    }

    @Test
    void archivoValidoYNoProcesadoSeImportaSeArchivaYQuedaEnBitacora() throws IOException {
        String cp = "0940" + TestUniqueId.homoclave().charAt(0);
        String nombreArchivo = "catalogo-" + TestUniqueId.homoclave() + ".csv";
        Files.writeString(directorioEntrada.resolve(nombreArchivo), csvValido(cp), StandardCharsets.UTF_8);

        scheduler.revisarDirectorioEImportarArchivosNuevos();

        assertThat(cpCatalogoRepository.findByCodigoPostal(cp)).hasSize(1);
        assertThat(Files.exists(directorioEntrada.resolve(nombreArchivo))).isFalse();
        assertThat(Files.exists(directorioProcesados.resolve(nombreArchivo))).isTrue();

        List<CatalogoImportacion> corridas = catalogoImportacionRepository
                .findAllByOrderByFechaInicioDesc(PageRequest.of(0, 20)).getContent();
        assertThat(corridas).anySatisfy(c -> {
            assertThat(c.getOrigen()).isEqualTo(OrigenImportacion.PROGRAMADA);
            assertThat(c.getEstado()).isEqualTo(EstadoImportacion.EXITO);
            assertThat(c.getArchivo()).isEqualTo(nombreArchivo);
        });
    }

    @Test
    void republicarBajoElMismoNombreConContenidoNuevoSiSeReimporta() throws IOException {
        String cpOriginal = "0941" + TestUniqueId.homoclave().charAt(0);
        String cpNuevo = "0942" + TestUniqueId.homoclave().charAt(0);
        String nombreArchivo = "catalogo-fijo.csv";

        Files.writeString(directorioEntrada.resolve(nombreArchivo), csvValido(cpOriginal), StandardCharsets.UTF_8);
        scheduler.revisarDirectorioEImportarArchivosNuevos();
        assertThat(cpCatalogoRepository.findByCodigoPostal(cpOriginal)).hasSize(1);

        // SEPOMEX republica bajo el MISMO nombre, con contenido (hash) distinto.
        Files.writeString(directorioEntrada.resolve(nombreArchivo), csvValido(cpNuevo), StandardCharsets.UTF_8);
        scheduler.revisarDirectorioEImportarArchivosNuevos();

        assertThat(cpCatalogoRepository.findByCodigoPostal(cpNuevo)).hasSize(1);
        long corridasExitosas = catalogoImportacionRepository
                .findAllByOrderByFechaInicioDesc(PageRequest.of(0, 20)).getContent().stream()
                .filter(c -> c.getArchivo().equals(nombreArchivo) && c.getEstado() == EstadoImportacion.EXITO)
                .count();
        assertThat(corridasExitosas).isEqualTo(2);
    }

    @Test
    void archivoConCorridaExitosaYaRegistradaNoSeReimportaAunSiElArchivoFisicoSigueAusente() throws IOException {
        String cp = "0943" + TestUniqueId.homoclave().charAt(0);
        String nombreArchivo = "catalogo-una-vez.csv";
        Files.writeString(directorioEntrada.resolve(nombreArchivo), csvValido(cp), StandardCharsets.UTF_8);

        scheduler.revisarDirectorioEImportarArchivosNuevos();
        long corridasTrasPrimerCiclo = catalogoImportacionRepository
                .findAllByOrderByFechaInicioDesc(PageRequest.of(0, 100)).getTotalElements();

        // Simula que el archivado fisico fallo: el archivo original ya no esta ni en
        // entrada ni en procesados (se "perdio"), pero la bitacora ya tiene el EXITO.
        Files.deleteIfExists(directorioProcesados.resolve(nombreArchivo));

        // Un ciclo posterior sin el archivo presente en absoluto no debe generar ninguna
        // corrida nueva para el mismo contenido (nada que escanear).
        scheduler.revisarDirectorioEImportarArchivosNuevos();
        long corridasTrasSegundoCiclo = catalogoImportacionRepository
                .findAllByOrderByFechaInicioDesc(PageRequest.of(0, 100)).getTotalElements();

        assertThat(corridasTrasSegundoCiclo).isEqualTo(corridasTrasPrimerCiclo);
    }

    @Test
    void archivoConEstructuraInvalidaNoModificaElCatalogoYQuedaComoErrorEnLaBitacora() throws IOException {
        String nombreArchivo = "catalogo-corrupto-" + TestUniqueId.homoclave() + ".csv";
        Files.writeString(directorioEntrada.resolve(nombreArchivo), "esto no es un catálogo válido",
                StandardCharsets.UTF_8);

        scheduler.revisarDirectorioEImportarArchivosNuevos();

        // No se archiva un archivo que fallo la validacion estructural.
        assertThat(Files.exists(directorioEntrada.resolve(nombreArchivo))).isTrue();
        assertThat(Files.exists(directorioProcesados.resolve(nombreArchivo))).isFalse();

        List<CatalogoImportacion> corridas = catalogoImportacionRepository
                .findAllByOrderByFechaInicioDesc(PageRequest.of(0, 20)).getContent();
        assertThat(corridas).anySatisfy(c -> {
            assertThat(c.getArchivo()).isEqualTo(nombreArchivo);
            assertThat(c.getEstado()).isEqualTo(EstadoImportacion.ERROR);
            assertThat(c.getDetalleError()).isNotBlank();
        });
    }
}
