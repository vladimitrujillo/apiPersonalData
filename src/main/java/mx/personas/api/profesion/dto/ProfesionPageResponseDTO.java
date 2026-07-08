package mx.personas.api.profesion.dto;

import java.util.List;

public record ProfesionPageResponseDTO(
        List<ProfesionResponseDTO> contenido,
        int pagina,
        int tamanoPagina,
        long totalElementos,
        int totalPaginas
) {
}
