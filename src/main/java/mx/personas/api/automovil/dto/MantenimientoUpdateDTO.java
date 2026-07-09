package mx.personas.api.automovil.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** No incluye automovilId (no reparentable). Si se envían piezas, reemplazan el conjunto completo. */
public record MantenimientoUpdateDTO(
        String descripcion,

        LocalDate fecha,

        @PositiveOrZero(message = "El kilometraje no puede ser negativo")
        Integer kilometraje,

        @PositiveOrZero(message = "El costo total no puede ser negativo")
        BigDecimal costoTotal,

        UUID mecanicoId,

        @Valid
        List<PiezaCambiadaDTO> piezas
) {
}
