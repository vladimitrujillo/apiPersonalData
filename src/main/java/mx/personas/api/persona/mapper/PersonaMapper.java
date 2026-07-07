package mx.personas.api.persona.mapper;

import mx.personas.api.persona.dto.PersonaRequestDTO;
import mx.personas.api.persona.dto.PersonaResponseDTO;
import mx.personas.api.persona.dto.PersonaResumenDTO;
import mx.personas.api.persona.model.Direccion;
import mx.personas.api.persona.model.Persona;
import mx.personas.api.usuario.model.Usuario;
import mx.personas.api.usuario.repository.UsuarioRepository;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

@Mapper(componentModel = "spring", uses = DireccionMapper.class)
public abstract class PersonaMapper {

    @Autowired
    protected UsuarioRepository usuarioRepository;

    public Persona toEntity(PersonaRequestDTO dto) {
        if (dto == null) {
            return null;
        }
        return new Persona(dto.nombres(), dto.apellidos(), dto.fechaNacimiento(), dto.sexo(),
                dto.curp(), dto.rfc(), dto.correo(), dto.telefono());
    }

    @Mapping(target = "id", source = "persona.id")
    @Mapping(target = "direccion", source = "direccion")
    @Mapping(target = "creadoPor", expression = "java(persona == null ? null : resolverLogin(persona.getCreadoPor()))")
    @Mapping(target = "creadoEn", source = "persona.createdAt")
    @Mapping(target = "modificadoPor",
            expression = "java(persona == null ? null : resolverLogin(persona.getActualizadoPor()))")
    @Mapping(target = "modificadoEn", source = "persona.updatedAt")
    public abstract PersonaResponseDTO toResponseDTO(Persona persona, Direccion direccion);

    /** Sin datos de auditoria, usada solo por el listado paginado (FR-004): evita N+1. */
    @Mapping(target = "id", source = "persona.id")
    @Mapping(target = "direccion", source = "direccion")
    public abstract PersonaResumenDTO toResumenDTO(Persona persona, Direccion direccion);

    protected String resolverLogin(UUID usuarioId) {
        return usuarioId == null ? null : usuarioRepository.findById(usuarioId).map(Usuario::getLogin).orElse(null);
    }
}
