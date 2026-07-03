package mx.personas.api.persona.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record PersonaRequestDTO(
        @NotBlank(message = "El nombre es requerido")
        String nombres,

        @NotBlank(message = "Los apellidos son requeridos")
        String apellidos,

        // La regla "no puede ser futura" se valida explícitamente en PersonaService para
        // producir el código de error específico FECHA_NACIMIENTO_FUTURA (FR-008), en vez
        // del genérico VALIDACION_FALLIDA que produciría @PastOrPresent.
        @NotNull(message = "La fecha de nacimiento es requerida")
        LocalDate fechaNacimiento,

        @NotBlank(message = "El sexo es requerido")
        String sexo,

        @NotBlank(message = "El CURP es requerido")
        @Pattern(regexp = "^[A-Z]{4}\\d{6}[HM][A-Z]{5}[A-Z0-9]{2}$", message = "El CURP no tiene un formato válido")
        String curp,

        @NotBlank(message = "El RFC es requerido")
        @Pattern(regexp = "^[A-ZÑ&]{3,4}\\d{6}[A-Z0-9]{3}$", message = "El RFC no tiene un formato válido")
        String rfc,

        @NotBlank(message = "El correo es requerido")
        @Email(message = "El formato de correo electrónico no es válido")
        String correo,

        @NotBlank(message = "El teléfono es requerido")
        @Pattern(regexp = "^\\d{10}$", message = "El teléfono debe tener exactamente 10 dígitos numéricos")
        String telefono,

        @NotNull(message = "La dirección es requerida")
        @Valid
        DireccionDTO direccion
) {
}
