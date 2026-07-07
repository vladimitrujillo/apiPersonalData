package mx.personas.api.persona.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Persona sin datos de auditoria, usada solo por el listado paginado (GET /api/personas
 * no cambia de schema — FR-004). Evita ademas resolver el login del autor por cada fila
 * (N+1) ya que el listado no lo necesita.
 */
public record PersonaResumenDTO(
        UUID id,
        String nombres,
        String apellidos,
        LocalDate fechaNacimiento,
        String sexo,
        String curp,
        String rfc,
        String correo,
        String telefono,
        DireccionResumenDTO direccion
) {
}
