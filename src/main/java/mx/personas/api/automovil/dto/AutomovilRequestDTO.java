package mx.personas.api.automovil.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AutomovilRequestDTO(
        @NotBlank(message = "La marca es requerida")
        String marca,

        @NotBlank(message = "El modelo es requerido")
        String modelo,

        @NotNull(message = "El año es requerido")
        Short anio,

        String color,

        @NotBlank(message = "Las placas son requeridas")
        String placas,

        String vin
) {
}
