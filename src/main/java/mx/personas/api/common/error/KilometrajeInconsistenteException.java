package mx.personas.api.common.error;

/**
 * Lanzada cuando el kilometraje de un mantenimiento nuevo o editado es menor al del
 * mantenimiento activo con la fecha mas reciente del mismo automovil (FR-017).
 */
public class KilometrajeInconsistenteException extends ApiException {

    public KilometrajeInconsistenteException(String mensaje) {
        super(ErrorCode.KILOMETRAJE_INCONSISTENTE, mensaje);
    }
}
