package mx.personas.api.automovil.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MantenimientoResponseDTO(
        UUID id,
        UUID automovilId,
        String descripcion,
        LocalDate fecha,
        Integer kilometraje,
        BigDecimal costoTotal,
        MecanicoResumenDTO mecanico,
        List<PiezaCambiadaDTO> piezas,
        boolean activo
) {
}
