package mx.personas.api.profesion.dto;

import java.util.List;

public record PersonaDirectorioPageResponseDTO(
        List<PersonaDirectorioDTO> contenido,
        int pagina,
        int tamanoPagina,
        long totalElementos,
        int totalPaginas
) {
}
