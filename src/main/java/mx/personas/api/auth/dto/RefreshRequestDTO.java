package mx.personas.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequestDTO(
        @NotBlank(message = "El token de refresco es requerido")
        String refreshToken
) {
}
