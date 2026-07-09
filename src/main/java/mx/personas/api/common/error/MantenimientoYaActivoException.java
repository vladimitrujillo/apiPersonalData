package mx.personas.api.common.error;

/**
 * Lanzada al intentar restaurar un mantenimiento que ya esta activo (FR-025a).
 */
public class MantenimientoYaActivoException extends ApiException {

    public MantenimientoYaActivoException(String mensaje) {
        super(ErrorCode.MANTENIMIENTO_YA_ACTIVO, mensaje);
    }
}
