package mx.personas.api.usuario.dto;

import mx.personas.api.usuario.model.Rol;

import java.util.UUID;

/** Nunca incluye la contraseña ni su hash (FR-016). */
public record UsuarioResponseDTO(
        UUID id,
        String login,
        String nombre,
        Rol rol,
        boolean activo
) {
}
