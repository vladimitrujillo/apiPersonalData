package mx.personas.api.common.error;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cubre cada codigo de contracts/error-format.md producido por el GlobalExceptionHandler
 * (Principio V de la constitucion).
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void recursoNoEncontradoRegresa404ConCodigoEspecifico() {
        var ex = new RecursoNoEncontradoException(ErrorCode.PERSONA_NO_ENCONTRADA, "no existe");

        ResponseEntity<ApiError> respuesta = handler.handleApiException(ex);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(respuesta.getBody().codigo()).isEqualTo("PERSONA_NO_ENCONTRADA");
        assertThat(respuesta.getBody().mensaje()).isEqualTo("no existe");
    }

    @Test
    void cpNoEncontradoRegresa404() {
        var ex = new RecursoNoEncontradoException(ErrorCode.CP_NO_ENCONTRADO, "cp no existe");

        ResponseEntity<ApiError> respuesta = handler.handleApiException(ex);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(respuesta.getBody().codigo()).isEqualTo("CP_NO_ENCONTRADO");
    }

    @Test
    void correoDuplicadoRegresa409ConDetalleDeCampo() {
        var ex = new DuplicateFieldException(ErrorCode.PERSONA_CORREO_DUPLICADO, "correo", "duplicado", "motivo");

        ResponseEntity<ApiError> respuesta = handler.handleApiException(ex);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody().codigo()).isEqualTo("PERSONA_CORREO_DUPLICADO");
        assertThat(respuesta.getBody().detalles()).hasSize(1);
        assertThat(respuesta.getBody().detalles().get(0).campo()).isEqualTo("correo");
    }

    @Test
    void curpDuplicadoRegresa409() {
        var ex = new DuplicateFieldException(ErrorCode.PERSONA_CURP_DUPLICADO, "curp", "duplicado", "motivo");

        ResponseEntity<ApiError> respuesta = handler.handleApiException(ex);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody().codigo()).isEqualTo("PERSONA_CURP_DUPLICADO");
    }

    @Test
    void fechaNacimientoFuturaRegresa400() {
        var ex = new FormatoInvalidoException(ErrorCode.FECHA_NACIMIENTO_FUTURA, "fechaNacimiento", "futura");

        ResponseEntity<ApiError> respuesta = handler.handleApiException(ex);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody().codigo()).isEqualTo("FECHA_NACIMIENTO_FUTURA");
    }

    @Test
    void cpFormatoInvalidoRegresa400() {
        var ex = new FormatoInvalidoException(ErrorCode.CP_FORMATO_INVALIDO, "codigoPostal", "formato inválido");

        ResponseEntity<ApiError> respuesta = handler.handleApiException(ex);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody().codigo()).isEqualTo("CP_FORMATO_INVALIDO");
    }

    @Test
    void coloniaInvalidaRegresa400() {
        var ex = new ColoniaInvalidaException("colonia inválida");

        ResponseEntity<ApiError> respuesta = handler.handleApiException(ex);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody().codigo()).isEqualTo("COLONIA_NO_VALIDA_PARA_CP");
    }

    @Test
    void validacionDeBeanValidationRegresa400ConDetallesPorCampo() {
        FieldError fieldError = new FieldError("objeto", "correo", "El correo no es válido");
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ApiError> respuesta = handler.handleValidation(ex);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody().codigo()).isEqualTo("VALIDACION_FALLIDA");
        assertThat(respuesta.getBody().detalles()).hasSize(1);
        assertThat(respuesta.getBody().detalles().get(0).campo()).isEqualTo("correo");
    }

    @Test
    @SuppressWarnings("unchecked")
    void constraintViolationRegresa400ConDetallesPorCampo() {
        ConstraintViolation<Object> violacion = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("telefono");
        when(violacion.getPropertyPath()).thenReturn(path);
        when(violacion.getMessage()).thenReturn("formato inválido");
        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violacion));

        ResponseEntity<ApiError> respuesta = handler.handleConstraintViolation(ex);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody().codigo()).isEqualTo("VALIDACION_FALLIDA");
        assertThat(respuesta.getBody().detalles()).hasSize(1);
        assertThat(respuesta.getBody().detalles().get(0).campo()).isEqualTo("telefono");
    }

    @Test
    void parametroFaltanteRegresa400() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("nombre", "String");

        ResponseEntity<ApiError> respuesta = handler.handleMissingParameter(ex);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody().codigo()).isEqualTo("VALIDACION_FALLIDA");
        assertThat(respuesta.getBody().detalles().get(0).campo()).isEqualTo("nombre");
    }
}
