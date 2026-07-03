package mx.personas.api.persona.mapper;

import mx.personas.api.persona.dto.PersonaRequestDTO;
import mx.personas.api.persona.dto.PersonaResponseDTO;
import mx.personas.api.persona.model.Direccion;
import mx.personas.api.persona.model.Persona;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = DireccionMapper.class)
public interface PersonaMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "activo", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    default Persona toEntity(PersonaRequestDTO dto) {
        if (dto == null) {
            return null;
        }
        return new Persona(dto.nombres(), dto.apellidos(), dto.fechaNacimiento(), dto.sexo(),
                dto.curp(), dto.rfc(), dto.correo(), dto.telefono());
    }

    @Mapping(target = "id", source = "persona.id")
    @Mapping(target = "direccion", source = "direccion")
    PersonaResponseDTO toResponseDTO(Persona persona, Direccion direccion);
}
