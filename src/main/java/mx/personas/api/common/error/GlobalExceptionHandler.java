package mx.personas.api.common.error;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Manejador global de errores: produce el formato JSON unico definido en
 * contracts/error-format.md para toda la API (Principio V de la constitucion).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex) {
        ApiError body = new ApiError(ex.getErrorCode().name(), ex.getMessage(), ex.getDetalles());
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiError.CampoError> detalles = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.CampoError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ApiError body = new ApiError(
                ErrorCode.VALIDACION_FALLIDA.name(),
                "La solicitud contiene uno o más campos inválidos",
                detalles);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        List<ApiError.CampoError> detalles = ex.getConstraintViolations().stream()
                .map(cv -> new ApiError.CampoError(cv.getPropertyPath().toString(), cv.getMessage()))
                .toList();
        ApiError body = new ApiError(
                ErrorCode.VALIDACION_FALLIDA.name(),
                "La solicitud contiene uno o más campos inválidos",
                detalles);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        ApiError body = new ApiError(
                ErrorCode.ACCESO_DENEGADO.name(),
                "No tiene permisos para realizar esta operación");
        return ResponseEntity.status(ErrorCode.ACCESO_DENEGADO.getHttpStatus()).body(body);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex) {
        ApiError body = new ApiError(
                ErrorCode.VALIDACION_FALLIDA.name(),
                "La solicitud contiene uno o más campos inválidos",
                List.of(new ApiError.CampoError(ex.getParameterName(), "Es un parámetro requerido")));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
