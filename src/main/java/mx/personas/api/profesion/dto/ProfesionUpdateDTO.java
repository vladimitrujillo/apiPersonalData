package mx.personas.api.profesion.dto;

/** El nombre no se puede editar tras crearse (FR-004); solo la descripción. */
public record ProfesionUpdateDTO(
        String descripcion
) {
}
