package mx.personas.api.persona.service;

import mx.personas.api.codigopostal.model.CpCatalogo;
import mx.personas.api.codigopostal.repository.CpCatalogoRepository;
import mx.personas.api.common.error.ColoniaInvalidaException;
import mx.personas.api.common.error.ErrorCode;
import mx.personas.api.common.error.FormatoInvalidoException;
import mx.personas.api.common.error.RecursoNoEncontradoException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Valida y completa la direccion de una persona contra el catalogo de codigos postales
 * cuando el pais es Mexico (US5, FR-019 a FR-022).
 */
@Service
public class DireccionValidationService {

    private static final Pattern FORMATO_CP = Pattern.compile("^\\d{5}$");
    private static final String PAIS_MEXICO = "MX";

    private final CpCatalogoRepository cpCatalogoRepository;

    public DireccionValidationService(CpCatalogoRepository cpCatalogoRepository) {
        this.cpCatalogoRepository = cpCatalogoRepository;
    }

    /**
     * Si {@code pais} no es Mexico, regresa la direccion tal cual, sin validar contra el
     * catalogo (FR-022). Si es Mexico, valida el formato y la existencia del CP (FR-019),
     * autocompleta municipio/estado (FR-020), y valida que la colonia pertenezca a la
     * lista de colonias de ese CP (FR-021).
     */
    public DireccionValidada validarYCompletar(String colonia, String municipio, String estado,
                                                String codigoPostal, String pais) {
        if (!PAIS_MEXICO.equalsIgnoreCase(pais)) {
            return new DireccionValidada(colonia, municipio, estado, codigoPostal, pais, null);
        }

        if (!FORMATO_CP.matcher(codigoPostal == null ? "" : codigoPostal).matches()) {
            throw new FormatoInvalidoException(ErrorCode.CP_FORMATO_INVALIDO, "direccion.codigoPostal",
                    "El código postal debe tener exactamente 5 dígitos numéricos");
        }

        List<CpCatalogo> filas = cpCatalogoRepository.findByCodigoPostal(codigoPostal);
        if (filas.isEmpty()) {
            throw new RecursoNoEncontradoException(ErrorCode.CP_NO_ENCONTRADO,
                    "No existe un código postal '" + codigoPostal + "' en el catálogo");
        }

        CpCatalogo coincidencia = filas.stream()
                .filter(fila -> fila.getAsentamiento().equalsIgnoreCase(colonia))
                .findFirst()
                .orElseThrow(() -> new ColoniaInvalidaException(
                        "La colonia '" + colonia + "' no pertenece a la lista de colonias del código postal '"
                                + codigoPostal + "'. Colonias válidas: "
                                + filas.stream().map(CpCatalogo::getAsentamiento).distinct()
                                        .reduce((a, b) -> a + ", " + b).orElse("")));

        return new DireccionValidada(coincidencia.getAsentamiento(), coincidencia.getMunicipio(),
                coincidencia.getEstado(), codigoPostal, pais, coincidencia.getId());
    }
}
