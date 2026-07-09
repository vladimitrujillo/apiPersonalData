package mx.personas.api.automovil.dto;

/** El VIN no se puede editar tras crearse (FR-008). */
public record AutomovilUpdateDTO(
        String marca,
        String modelo,
        Short anio,
        String color,
        String placas
) {
}
