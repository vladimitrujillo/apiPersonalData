package mx.personas.api.persona.dto;

import java.util.List;

public record HistorialPageResponseDTO(
        List<HistorialEntradaDTO> contenido,
        int pagina,
        int tamanoPagina,
        long totalElementos,
        int totalPaginas
) {
}
