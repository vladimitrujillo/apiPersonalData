package mx.personas.api.automovil.mapper;

import mx.personas.api.automovil.dto.AutomovilResponseDTO;
import mx.personas.api.automovil.model.Automovil;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AutomovilMapper {

    @Mapping(target = "personaId", source = "persona.id")
    AutomovilResponseDTO toResponseDTO(Automovil automovil);
}
