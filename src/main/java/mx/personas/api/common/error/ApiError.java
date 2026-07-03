package mx.personas.api.common.error;

import java.util.List;

/**
 * Formato unico de respuesta de error para toda la API (Principio V de la constitucion).
 * Ver specs/001-personas-codigos-postales/contracts/error-format.md
 */
public record ApiError(String codigo, String mensaje, List<CampoError> detalles) {

    public ApiError(String codigo, String mensaje) {
        this(codigo, mensaje, List.of());
    }

    public record CampoError(String campo, String motivo) {
    }
}
