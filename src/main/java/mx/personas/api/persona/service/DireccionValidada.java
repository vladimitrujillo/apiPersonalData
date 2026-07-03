package mx.personas.api.persona.service;

/**
 * Resultado de validar/completar una direccion contra el catalogo de codigos postales
 * (US5). {@code cpCatalogoId} es {@code null} cuando el pais no es Mexico (FR-022).
 */
public record DireccionValidada(
        String colonia,
        String municipio,
        String estado,
        String codigoPostal,
        String pais,
        Long cpCatalogoId
) {
}
