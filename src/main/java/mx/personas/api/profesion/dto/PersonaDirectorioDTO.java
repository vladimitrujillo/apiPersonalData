package mx.personas.api.profesion.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Fila del directorio de personas por profesión (FR-018) — DTO reducido:
 * nunca correo, teléfono, CURP, RFC ni dirección.
 */
public record PersonaDirectorioDTO(
        UUID id,
        String nombreCompleto,
        LocalDate fechaDesde,
        String cedula
) {
}
