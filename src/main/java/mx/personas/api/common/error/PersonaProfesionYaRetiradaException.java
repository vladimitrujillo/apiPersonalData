package mx.personas.api.common.error;

/**
 * Lanzada al intentar retirar una asignacion de profesion que ya estaba
 * retirada (FR-015).
 */
public class PersonaProfesionYaRetiradaException extends ApiException {

    public PersonaProfesionYaRetiradaException(String mensaje) {
        super(ErrorCode.PERSONA_PROFESION_YA_RETIRADA, mensaje);
    }
}
