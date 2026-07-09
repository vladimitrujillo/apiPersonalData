package mx.personas.api.automovil.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MantenimientoRequestDTO(
        @NotBlank(message = "La descripción es requerida")
        String descripcion,

        @NotNull(message = "La fecha es requerida")
        LocalDate fecha,

        @NotNull(message = "El kilometraje es requerido")
        @PositiveOrZero(message = "El kilometraje no puede ser negativo")
        Integer kilometraje,

        @NotNull(message = "El costo total es requerido")
        @PositiveOrZero(message = "El costo total no puede ser negativo")
        BigDecimal costoTotal,

        UUID mecanicoId,

        @Valid
        List<PiezaCambiadaDTO> piezas
) {
    public MantenimientoRequestDTO {
        if (piezas == null) {
            piezas = List.of();
        }
    }
}
