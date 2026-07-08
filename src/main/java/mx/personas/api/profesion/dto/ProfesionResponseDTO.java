package mx.personas.api.profesion.dto;

public record ProfesionResponseDTO(
        Long id,
        String nombre,
        String descripcion,
        boolean activo
) {
}
