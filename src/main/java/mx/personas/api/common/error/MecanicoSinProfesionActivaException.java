package mx.personas.api.common.error;

/**
 * Lanzada cuando el mecanicoId de un mantenimiento corresponde a una persona activa
 * que no tiene la profesion "Mecanico" asignada de forma activa (FR-020).
 */
public class MecanicoSinProfesionActivaException extends ApiException {

    public MecanicoSinProfesionActivaException(String mensaje) {
        super(ErrorCode.MECANICO_SIN_PROFESION_ACTIVA, mensaje);
    }
}
