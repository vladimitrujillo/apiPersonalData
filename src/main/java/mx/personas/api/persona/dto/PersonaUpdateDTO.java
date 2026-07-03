package mx.personas.api.persona.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

/**
 * Actualizacion parcial de una persona: solo los campos no nulos se modifican (FR-004).
 */
public record PersonaUpdateDTO(
        String nombres,
        String apellidos,
        LocalDate fechaNacimiento,
        String sexo,

        @Pattern(regexp = "^[A-Z]{4}\\d{6}[HM][A-Z]{5}[A-Z0-9]{2}$", message = "El CURP no tiene un formato válido")
        String curp,

        @Pattern(regexp = "^[A-ZÑ&]{3,4}\\d{6}[A-Z0-9]{3}$", message = "El RFC no tiene un formato válido")
        String rfc,

        @Email(message = "El formato de correo electrónico no es válido")
        String correo,

        @Pattern(regexp = "^\\d{10}$", message = "El teléfono debe tener exactamente 10 dígitos numéricos")
        String telefono,

        @Valid
        DireccionUpdateDTO direccion
) {
}
