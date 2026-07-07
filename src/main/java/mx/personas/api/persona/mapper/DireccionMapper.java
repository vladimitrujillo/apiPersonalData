package mx.personas.api.persona.mapper;

import mx.personas.api.persona.dto.DireccionResponseDTO;
import mx.personas.api.persona.dto.DireccionResumenDTO;
import mx.personas.api.persona.model.Direccion;
import mx.personas.api.usuario.model.Usuario;
import mx.personas.api.usuario.repository.UsuarioRepository;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

@Mapper(componentModel = "spring")
public abstract class DireccionMapper {

    @Autowired
    protected UsuarioRepository usuarioRepository;

    @Mapping(target = "creadoPor", expression = "java(resolverLogin(direccion.getCreadoPor()))")
    @Mapping(target = "creadoEn", source = "createdAt")
    @Mapping(target = "modificadoPor", expression = "java(resolverLogin(direccion.getActualizadoPor()))")
    @Mapping(target = "modificadoEn", source = "updatedAt")
    public abstract DireccionResponseDTO toResponseDTO(Direccion direccion);

    /** Sin datos de auditoria, usada solo por el listado paginado (FR-004). */
    public abstract DireccionResumenDTO toResumenDTO(Direccion direccion);

    protected String resolverLogin(UUID usuarioId) {
        return usuarioId == null ? null : usuarioRepository.findById(usuarioId).map(Usuario::getLogin).orElse(null);
    }
}
