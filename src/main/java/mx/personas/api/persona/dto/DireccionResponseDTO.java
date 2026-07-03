package mx.personas.api.persona.dto;

public record DireccionResponseDTO(
        String calle,
        String numero,
        String colonia,
        String municipio,
        String estado,
        String codigoPostal,
        String pais
) {
}
