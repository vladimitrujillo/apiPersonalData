package mx.personas.api.common.error;

import org.springframework.http.HttpStatus;

/**
 * Catalogo de codigos de error estables de la API.
 * Ver specs/001-personas-codigos-postales/contracts/error-format.md
 */
public enum ErrorCode {

    VALIDACION_FALLIDA(HttpStatus.BAD_REQUEST),
    CP_FORMATO_INVALIDO(HttpStatus.BAD_REQUEST),
    FECHA_NACIMIENTO_FUTURA(HttpStatus.BAD_REQUEST),
    COLONIA_NO_VALIDA_PARA_CP(HttpStatus.BAD_REQUEST),
    PERSONA_NO_ENCONTRADA(HttpStatus.NOT_FOUND),
    CP_NO_ENCONTRADO(HttpStatus.NOT_FOUND),
    PERSONA_CORREO_DUPLICADO(HttpStatus.CONFLICT),
    PERSONA_CURP_DUPLICADO(HttpStatus.CONFLICT),
    NO_AUTENTICADO(HttpStatus.UNAUTHORIZED);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
