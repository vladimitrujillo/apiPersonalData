package mx.personas.api.codigopostal;

import mx.personas.api.codigopostal.importer.SepomexImportService;
import mx.personas.api.codigopostal.model.CpCatalogo;
import mx.personas.api.codigopostal.repository.CpCatalogoRepository;
import mx.personas.api.common.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que el proceso de importacion del catalogo SEPOMEX es idempotente (FR-017,
 * SC-005, research.md #3): reimportar el mismo archivo no duplica filas, y una fila
 * modificada en una "nueva version" del archivo se actualiza sin duplicarse.
 */
class SepomexImportServiceIT extends AbstractIntegrationTest {

    @Autowired
    private SepomexImportService sepomexImportService;

    @Autowired
    private CpCatalogoRepository cpCatalogoRepository;

    private Path escribirCsv(String contenido) throws IOException {
        Path archivo = Files.createTempFile("sepomex-test", ".csv");
        Files.writeString(archivo, contenido, StandardCharsets.UTF_8);
        return archivo;
    }

    @Test
    void reimportarElMismoArchivoNoDuplicaFilas() throws IOException {
        String cp = "0910" + (System.nanoTime() % 10);
        String csv = """
                codigoPostal|estado|municipio|asentamiento|tipoAsentamiento|idAsentaCpcons
                %s|Ciudad de México|Iztapalapa|Barrio San Lucas|Colonia|1
                %s|Ciudad de México|Iztapalapa|Barrio San Pablo|Colonia|2
                """.formatted(cp, cp);
        Path archivo = escribirCsv(csv);

        var primeraImportacion = sepomexImportService.importar(archivo);
        assertThat(primeraImportacion.insertados()).isEqualTo(2);
        List<CpCatalogo> trasPrimeraImportacion = cpCatalogoRepository.findByCodigoPostal(cp);
        assertThat(trasPrimeraImportacion).hasSize(2);

        var segundaImportacion = sepomexImportService.importar(archivo);
        assertThat(segundaImportacion.insertados()).isZero();
        assertThat(segundaImportacion.sinCambio()).isEqualTo(2);
        List<CpCatalogo> trasSegundaImportacion = cpCatalogoRepository.findByCodigoPostal(cp);
        assertThat(trasSegundaImportacion).hasSize(2);
    }

    @Test
    void reimportarConDatosModificadosActualizaSinDuplicar() throws IOException {
        String cp = "0920" + (System.nanoTime() % 10);
        String csvOriginal = """
                codigoPostal|estado|municipio|asentamiento|tipoAsentamiento|idAsentaCpcons
                %s|Ciudad de México|Iztapalapa|Barrio Viejo|Colonia|1
                """.formatted(cp);
        sepomexImportService.importar(escribirCsv(csvOriginal));

        String csvActualizado = """
                codigoPostal|estado|municipio|asentamiento|tipoAsentamiento|idAsentaCpcons
                %s|Ciudad de México|Iztapalapa|Barrio Renombrado|Fraccionamiento|1
                """.formatted(cp);
        var segundaImportacion = sepomexImportService.importar(escribirCsv(csvActualizado));
        assertThat(segundaImportacion.actualizados()).isEqualTo(1);
        assertThat(segundaImportacion.insertados()).isZero();

        List<CpCatalogo> filas = cpCatalogoRepository.findByCodigoPostal(cp);
        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).getAsentamiento()).isEqualTo("Barrio Renombrado");
        assertThat(filas.get(0).getTipoAsentamiento()).isEqualTo("Fraccionamiento");
    }
}
