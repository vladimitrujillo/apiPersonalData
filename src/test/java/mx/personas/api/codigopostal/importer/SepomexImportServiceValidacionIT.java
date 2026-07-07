package mx.personas.api.codigopostal.importer;

import mx.personas.api.codigopostal.repository.CpCatalogoRepository;
import mx.personas.api.common.AbstractIntegrationTest;
import mx.personas.api.common.error.CatalogoArchivoInvalidoException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-011 (validacion estructural previa, catalogo intacto ante fallo) y FR-012
 * (tolerancia por fila individual invalida, sin abortar el resto) - research.md #1.
 */
class SepomexImportServiceValidacionIT extends AbstractIntegrationTest {

    @Autowired
    private SepomexImportService sepomexImportService;

    @Autowired
    private CpCatalogoRepository cpCatalogoRepository;

    private Path escribirCsv(String contenido) throws IOException {
        Path archivo = Files.createTempFile("sepomex-validacion-test", ".csv");
        Files.writeString(archivo, contenido, StandardCharsets.UTF_8);
        return archivo;
    }

    @Test
    void encabezadoInvalidoRechazaElArchivoCompletoSinTocarElCatalogo() throws IOException {
        String cp = "0930" + (System.nanoTime() % 10);
        String csv = """
                cp|state|city|colonia|tipo|id
                %s|Ciudad de México|Iztapalapa|Barrio Nuevo|Colonia|1
                """.formatted(cp);
        Path archivo = escribirCsv(csv);

        assertThatThrownBy(() -> sepomexImportService.importar(archivo))
                .isInstanceOf(CatalogoArchivoInvalidoException.class);

        assertThat(cpCatalogoRepository.findByCodigoPostal(cp)).isEmpty();
    }

    @Test
    void lineaConMenosDeSeisColumnasRechazaElArchivoCompletoSinTocarElCatalogo() throws IOException {
        String cpValido = "0931" + (System.nanoTime() % 10);
        String csv = """
                codigoPostal|estado|municipio|asentamiento|tipoAsentamiento|idAsentaCpcons
                %s|Ciudad de México|Iztapalapa|Barrio Nuevo|Colonia|1
                09999|Ciudad de México|Iztapalapa|Barrio Incompleto
                """.formatted(cpValido);
        Path archivo = escribirCsv(csv);

        assertThatThrownBy(() -> sepomexImportService.importar(archivo))
                .isInstanceOf(CatalogoArchivoInvalidoException.class);

        assertThat(cpCatalogoRepository.findByCodigoPostal(cpValido)).isEmpty();
    }

    @Test
    void filaConCodigoPostalInvalidoSeCuentaComoRechazadaSinAbortarElResto() throws IOException {
        String cpValido1 = "0932" + (System.nanoTime() % 10);
        String cpValido2 = "0933" + (System.nanoTime() % 10);
        String csv = """
                codigoPostal|estado|municipio|asentamiento|tipoAsentamiento|idAsentaCpcons
                %s|Ciudad de México|Iztapalapa|Barrio Uno|Colonia|1
                ABCDE|Ciudad de México|Iztapalapa|Barrio Con CP Invalido|Colonia|2
                %s|Ciudad de México|Iztapalapa|Barrio Dos|Colonia|3
                """.formatted(cpValido1, cpValido2);
        Path archivo = escribirCsv(csv);

        ResumenImportacion resumen = sepomexImportService.importar(archivo);

        assertThat(resumen.insertados()).isEqualTo(2);
        assertThat(resumen.rechazados()).isEqualTo(1);
        assertThat(resumen.detallesRechazados()).hasSize(1);
        assertThat(resumen.detallesRechazados().get(0)).contains("ABCDE");
        assertThat(cpCatalogoRepository.findByCodigoPostal(cpValido1)).hasSize(1);
        assertThat(cpCatalogoRepository.findByCodigoPostal(cpValido2)).hasSize(1);
    }
}
