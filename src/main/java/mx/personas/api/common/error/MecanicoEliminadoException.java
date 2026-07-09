package mx.personas.api.common.error;

/**
 * Lanzada cuando el mecanicoId de un mantenimiento corresponde a una persona
 * eliminada logicamente (FR-020).
 */
public class MecanicoEliminadoException extends ApiException {

    public MecanicoEliminadoException(String mensaje) {
        super(ErrorCode.MECANICO_ELIMINADO, mensaje);
    }
}
