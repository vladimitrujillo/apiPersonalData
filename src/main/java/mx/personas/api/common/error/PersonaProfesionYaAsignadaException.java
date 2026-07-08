package mx.personas.api.common.error;

/**
 * Lanzada al intentar asignar a una persona una profesion que ya tiene
 * asignada de forma activa (FR-013).
 */
public class PersonaProfesionYaAsignadaException extends ApiException {

    public PersonaProfesionYaAsignadaException(String mensaje) {
        super(ErrorCode.PERSONA_PROFESION_YA_ASIGNADA, mensaje);
    }
}
