package mx.personas.api.codigopostal.dto;

public record ColoniaBusquedaDTO(
        String codigoPostal,
        String estado,
        String municipio,
        String nombre,
        String tipoAsentamiento
) {
}
