package mx.personas.api.profesion.dto;

import java.time.LocalDate;
import java.util.UUID;

public record AsignacionProfesionResponseDTO(
        UUID id,
        Long profesionId,
        String profesionNombre,
        LocalDate fechaDesde,
        String cedula,
        boolean activo
) {
}
