package mx.personas.api.common.error;

/**
 * Lanzada al intentar editar un automovil eliminado logicamente, o registrar/editar
 * un mantenimiento sobre uno (FR-008, FR-013, FR-024).
 */
public class AutomovilEliminadoException extends ApiException {

    public AutomovilEliminadoException(String mensaje) {
        super(ErrorCode.AUTOMOVIL_ELIMINADO, mensaje);
    }
}
