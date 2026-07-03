package mx.personas.api.common.error;

import java.util.List;

/**
 * Lanzada cuando un campo unico (correo, CURP) ya esta en uso por otra persona activa.
 */
public class DuplicateFieldException extends ApiException {

    public DuplicateFieldException(ErrorCode errorCode, String campo, String mensaje, String motivoCampo) {
        super(errorCode, mensaje, List.of(new ApiError.CampoError(campo, motivoCampo)));
    }
}
