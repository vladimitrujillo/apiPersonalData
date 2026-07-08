package mx.personas.api.profesion.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AsignacionProfesionRequestDTO(
        @NotNull(message = "El identificador de la profesión es requerido")
        Long profesionId,

        LocalDate fechaDesde,

        String cedula
) {
}
