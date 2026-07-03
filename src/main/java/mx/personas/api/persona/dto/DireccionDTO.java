package mx.personas.api.persona.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Direccion enviada al crear una persona. municipio/estado son opcionales: si se omiten
 * y el CP es valido para Mexico, el sistema los autocompleta (FR-020). El formato exacto
 * del codigo postal y su existencia en el catalogo se validan en
 * {@code DireccionValidationService} (US5), no aqui, para producir los codigos de error
 * especificos de contracts/error-format.md (CP_FORMATO_INVALIDO, CP_NO_ENCONTRADO,
 * COLONIA_NO_VALIDA_PARA_CP) en vez del generico VALIDACION_FALLIDA.
 */
public record DireccionDTO(
        @NotBlank(message = "La calle es requerida")
        String calle,

        @NotBlank(message = "El número es requerido")
        String numero,

        @NotBlank(message = "La colonia es requerida")
        String colonia,

        String municipio,

        String estado,

        @NotBlank(message = "El código postal es requerido")
        String codigoPostal,

        @NotBlank(message = "El país es requerido")
        String pais
) {
}
