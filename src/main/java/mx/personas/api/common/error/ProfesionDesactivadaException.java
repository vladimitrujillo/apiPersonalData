package mx.personas.api.common.error;

/**
 * Lanzada al intentar asignar a una persona una profesion desactivada del
 * catalogo (FR-009).
 */
public class ProfesionDesactivadaException extends ApiException {

    public ProfesionDesactivadaException(String mensaje) {
        super(ErrorCode.PROFESION_DESACTIVADA, mensaje);
    }
}
