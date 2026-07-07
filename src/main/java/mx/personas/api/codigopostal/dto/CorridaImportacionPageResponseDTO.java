package mx.personas.api.codigopostal.dto;

import java.util.List;

public record CorridaImportacionPageResponseDTO(
        List<CorridaImportacionDTO> contenido,
        int pagina,
        int tamanoPagina,
        long totalElementos,
        int totalPaginas
) {
}
