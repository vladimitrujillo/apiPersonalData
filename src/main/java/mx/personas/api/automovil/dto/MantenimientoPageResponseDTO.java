package mx.personas.api.automovil.dto;

import java.util.List;

public record MantenimientoPageResponseDTO(
        List<MantenimientoResponseDTO> contenido,
        int pagina,
        int tamanoPagina,
        long totalElementos,
        int totalPaginas
) {
}
