package mx.personas.api.persona.dto;

import jakarta.validation.constraints.Min;

import java.time.LocalDate;

/**
 * Filtro combinable de busqueda avanzada (FR-009: todos los campos opcionales,
 * combinados con AND). Poblado desde query params de GET /api/personas via
 * @Valid @ModelAttribute. estadoRegistro se agrega en US3 (T021), no aqui.
 */
public record PersonaBusquedaFiltroDTO(
        String nombre,
        String municipio,
        String estado,
        String curpPrefijo,
        @Min(0) Integer edadMinima,
        @Min(0) Integer edadMaxima,
        LocalDate fechaRegistroDesde,
        LocalDate fechaRegistroHasta,
        String sexo,
        String estadoRegistro,
        String ordenarPor,
        String direccionOrden
) {
}
