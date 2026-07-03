package mx.personas.api.persona.dto;

import java.util.List;

public record PersonaPageResponseDTO(
        List<PersonaResponseDTO> contenido,
        int pagina,
        int tamanoPagina,
        long totalElementos,
        int totalPaginas
) {
}
