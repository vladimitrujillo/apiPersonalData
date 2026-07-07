package mx.personas.api.persona.dto;

import java.time.OffsetDateTime;

public record DireccionResponseDTO(
        String calle,
        String numero,
        String colonia,
        String municipio,
        String estado,
        String codigoPostal,
        String pais,
        String creadoPor,
        OffsetDateTime creadoEn,
        String modificadoPor,
        OffsetDateTime modificadoEn
) {
}
