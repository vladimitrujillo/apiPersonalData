package mx.personas.api.common.error;

/**
 * Lanzada al intentar restaurar una persona que ya esta activa (FR-015).
 */
public class PersonaYaActivaException extends ApiException {

    public PersonaYaActivaException(String mensaje) {
        super(ErrorCode.PERSONA_YA_ACTIVA, mensaje);
    }
}
