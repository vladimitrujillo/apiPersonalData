package mx.personas.api.common.error;

/**
 * Lanzada al intentar restaurar un automovil que ya esta activo (FR-011).
 */
public class AutomovilYaActivoException extends ApiException {

    public AutomovilYaActivoException(String mensaje) {
        super(ErrorCode.AUTOMOVIL_YA_ACTIVO, mensaje);
    }
}
