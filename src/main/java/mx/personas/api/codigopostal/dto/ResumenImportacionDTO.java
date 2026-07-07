package mx.personas.api.codigopostal.dto;

import java.util.List;

/** Resumen de una corrida de importación disparada manualmente (FR-006, US2). */
public record ResumenImportacionDTO(
        int insertados,
        int actualizados,
        int sinCambio,
        int rechazados,
        List<String> detallesRechazados
) {
}
