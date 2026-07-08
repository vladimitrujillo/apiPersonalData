package mx.personas.api.profesion.mapper;

import mx.personas.api.profesion.dto.ProfesionResponseDTO;
import mx.personas.api.profesion.model.Profesion;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProfesionMapper {

    ProfesionResponseDTO toResponseDTO(Profesion profesion);
}
