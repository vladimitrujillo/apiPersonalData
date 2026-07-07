package mx.personas.api.common.error;

/**
 * Lanzada ante cualquier fallo de login o de refresh (credenciales incorrectas, usuario
 * inexistente, usuario desactivado, token de refresco invalido/expirado/ya usado). Siempre
 * se traduce al mismo 401 generico, sin distinguir el motivo (FR-003, FR-005, FR-013).
 */
public class CredencialesInvalidasException extends ApiException {

    public CredencialesInvalidasException(String mensaje) {
        super(ErrorCode.NO_AUTENTICADO, mensaje);
    }
}
