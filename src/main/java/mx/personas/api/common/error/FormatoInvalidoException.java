package mx.personas.api.common.error;

import java.util.List;

/**
 * Lanzada para errores de formato/regla de negocio que no provienen directamente de
 * Bean Validation (p. ej. CP con formato invalido, fecha de nacimiento futura).
 */
public class FormatoInvalidoException extends ApiException {

    public FormatoInvalidoException(ErrorCode errorCode, String campo, String mensaje) {
        super(errorCode, mensaje, List.of(new ApiError.CampoError(campo, mensaje)));
    }
}
