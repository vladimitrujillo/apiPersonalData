package mx.personas.api.automovil.dto;

import java.util.UUID;

/** Proyección mínima del mecánico (research.md #9) — nunca la entidad Persona completa. */
public record MecanicoResumenDTO(
        UUID id,
        String nombreCompleto
) {
}
