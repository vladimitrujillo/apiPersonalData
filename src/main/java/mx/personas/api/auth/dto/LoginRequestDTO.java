package mx.personas.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequestDTO(
        @NotBlank(message = "El usuario es requerido")
        String login,

        @NotBlank(message = "La contraseña es requerida")
        String password
) {
}
