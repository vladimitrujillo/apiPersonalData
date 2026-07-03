package mx.personas.api.persona.dto;

/**
 * Direccion enviada al actualizar parcialmente una persona: todos los campos son
 * opcionales; el service combina lo enviado con la direccion vigente.
 */
public record DireccionUpdateDTO(
        String calle,
        String numero,
        String colonia,
        String municipio,
        String estado,
        String codigoPostal,
        String pais
) {
}
