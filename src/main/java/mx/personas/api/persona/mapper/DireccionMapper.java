package mx.personas.api.persona.mapper;

import mx.personas.api.persona.dto.DireccionResponseDTO;
import mx.personas.api.persona.model.Direccion;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DireccionMapper {

    DireccionResponseDTO toResponseDTO(Direccion direccion);
}
