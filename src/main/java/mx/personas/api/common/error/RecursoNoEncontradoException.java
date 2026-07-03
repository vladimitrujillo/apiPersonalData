package mx.personas.api.common.error;

/**
 * Lanzada cuando el recurso solicitado no existe o ya no esta activo
 * (persona eliminada logicamente, o codigo postal fuera del catalogo).
 */
public class RecursoNoEncontradoException extends ApiException {

    public RecursoNoEncontradoException(ErrorCode errorCode, String mensaje) {
        super(errorCode, mensaje);
    }
}
