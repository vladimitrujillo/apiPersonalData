package mx.personas.api.profesion.dto;

import jakarta.validation.constraints.NotBlank;

public record ProfesionRequestDTO(
        @NotBlank(message = "El nombre es requerido")
        String nombre,

        String descripcion
) {
}
