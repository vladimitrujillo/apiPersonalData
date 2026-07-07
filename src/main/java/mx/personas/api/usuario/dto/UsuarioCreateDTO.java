package mx.personas.api.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import mx.personas.api.usuario.model.Rol;

public record UsuarioCreateDTO(
        @NotBlank(message = "El login es requerido")
        String login,

        @NotBlank(message = "La contraseña es requerida")
        @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
        String password,

        @NotBlank(message = "El nombre es requerido")
        String nombre,

        @NotNull(message = "El rol es requerido")
        Rol rol
) {
}
