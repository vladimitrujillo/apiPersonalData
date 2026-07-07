package mx.personas.api.persona.dto;

/** Direccion sin datos de auditoria, usada solo por el listado paginado (FR-004). */
public record DireccionResumenDTO(
        String calle,
        String numero,
        String colonia,
        String municipio,
        String estado,
        String codigoPostal,
        String pais
) {
}
