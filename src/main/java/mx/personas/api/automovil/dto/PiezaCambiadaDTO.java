package mx.personas.api.automovil.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

public record PiezaCambiadaDTO(
        UUID id,

        @NotBlank(message = "El nombre de la pieza es requerido")
        String nombre,

        String numeroParte,

        @PositiveOrZero(message = "El costo de la pieza no puede ser negativo")
        BigDecimal costo
) {
}
