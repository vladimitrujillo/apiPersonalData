package mx.personas.api.automovil.dto;

import java.util.UUID;

public record AutomovilResponseDTO(
        UUID id,
        UUID personaId,
        String marca,
        String modelo,
        Short anio,
        String color,
        String placas,
        String vin,
        boolean activo
) {
}
