package mx.personas.api.common.error;

import java.util.List;

/**
 * Excepcion base de negocio. Cada subclase representa una familia de errores de dominio
 * mapeada por el GlobalExceptionHandler al formato unico de ApiError.
 */
public abstract class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<ApiError.CampoError> detalles;

    protected ApiException(ErrorCode errorCode, String mensaje) {
        this(errorCode, mensaje, List.of());
    }

    protected ApiException(ErrorCode errorCode, String mensaje, List<ApiError.CampoError> detalles) {
        super(mensaje);
        this.errorCode = errorCode;
        this.detalles = detalles;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public List<ApiError.CampoError> getDetalles() {
        return detalles;
    }
}
