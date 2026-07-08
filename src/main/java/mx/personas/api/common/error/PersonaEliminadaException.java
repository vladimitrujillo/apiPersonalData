package mx.personas.api.common.error;

/**
 * Lanzada al intentar asignar una profesion a una persona eliminada
 * logicamente (FR-014).
 */
public class PersonaEliminadaException extends ApiException {

    public PersonaEliminadaException(String mensaje) {
        super(ErrorCode.PERSONA_ELIMINADA, mensaje);
    }
}
