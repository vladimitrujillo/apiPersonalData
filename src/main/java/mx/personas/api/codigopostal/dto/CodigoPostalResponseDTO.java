package mx.personas.api.codigopostal.dto;

import java.util.List;

public record CodigoPostalResponseDTO(
        String codigoPostal,
        String estado,
        String municipio,
        List<ColoniaDTO> colonias
) {
}
