package mx.personas.api.common.error;

import java.util.List;

/**
 * Lanzada cuando la colonia enviada no pertenece a la lista de colonias del codigo postal
 * indicado (FR-021).
 */
public class ColoniaInvalidaException extends ApiException {

    public ColoniaInvalidaException(String mensaje) {
        super(ErrorCode.COLONIA_NO_VALIDA_PARA_CP, mensaje,
                List.of(new ApiError.CampoError("direccion.colonia", mensaje)));
    }
}
