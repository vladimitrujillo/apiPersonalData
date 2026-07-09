package mx.personas.api.common.error;

/**
 * Lanzada cuando el mecanicoId de un mantenimiento no corresponde a ninguna persona
 * registrada (FR-019). A diferencia de otras referencias inexistentes del proyecto,
 * este caso es 400 (valor de request invalido), no 404 (research.md #3).
 */
public class MecanicoNoEncontradoException extends ApiException {

    public MecanicoNoEncontradoException(String mensaje) {
        super(ErrorCode.MECANICO_NO_ENCONTRADO, mensaje);
    }
}
