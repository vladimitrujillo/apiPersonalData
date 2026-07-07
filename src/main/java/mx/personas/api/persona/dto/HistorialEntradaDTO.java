package mx.personas.api.persona.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record HistorialEntradaDTO(
        OffsetDateTime fecha,
        String usuario,
        String operacion,
        List<CampoCambiadoDTO> cambios
) {
}
