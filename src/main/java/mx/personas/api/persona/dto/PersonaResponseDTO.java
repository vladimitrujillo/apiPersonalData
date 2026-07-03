package mx.personas.api.persona.dto;

import java.time.LocalDate;
import java.util.UUID;

public record PersonaResponseDTO(
        UUID id,
        String nombres,
        String apellidos,
        LocalDate fechaNacimiento,
        String sexo,
        String curp,
        String rfc,
        String correo,
        String telefono,
        DireccionResponseDTO direccion
) {
}
