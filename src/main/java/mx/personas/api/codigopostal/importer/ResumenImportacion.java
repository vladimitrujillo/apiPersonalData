package mx.personas.api.codigopostal.importer;

import java.util.List;

/**
 * Resultado detallado de una corrida de importacion del catalogo SEPOMEX (FR-006,
 * research.md #1). Reemplaza el antiguo conteo total (int) que devolvia
 * SepomexImportService.importar(...).
 */
public record ResumenImportacion(
        int insertados,
        int actualizados,
        int sinCambio,
        int rechazados,
        List<String> detallesRechazados
) {

    /** Determina si la corrida amerita evict-ear el cache de codigos postales (research.md #6). */
    public boolean tuvoCambios() {
        return insertados > 0 || actualizados > 0;
    }
}
