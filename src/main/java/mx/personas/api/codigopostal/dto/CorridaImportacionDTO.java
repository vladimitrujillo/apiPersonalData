package mx.personas.api.codigopostal.dto;

import java.time.OffsetDateTime;

/** Una entrada de la bitácora de corridas de importación (US3). */
public record CorridaImportacionDTO(
        OffsetDateTime fecha,
        String origen,
        String usuario,
        String archivo,
        Long duracionMs,
        int insertados,
        int actualizados,
        int sinCambio,
        int rechazados,
        String estado,
        String detalleError
) {
}
