package mx.personas.api.persona.dto;

import java.util.List;

/**
 * Vista paginada de personas eliminadas logicamente (US4, solo ADMIN). A diferencia del
 * listado general (PersonaPageResponseDTO, que usa PersonaResumenDTO sin auditoría), aquí
 * cada elemento usa el PersonaResponseDTO completo (mismo shape que GET /api/personas/{id},
 * incluida su auditoría) — data-model.md.
 */
public record PersonaEliminadaPageResponseDTO(
        List<PersonaResponseDTO> contenido,
        int pagina,
        int tamanoPagina,
        long totalElementos,
        int totalPaginas
) {
}
