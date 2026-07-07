package mx.personas.api.usuario.mapper;

import mx.personas.api.usuario.dto.UsuarioResponseDTO;
import mx.personas.api.usuario.model.Usuario;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UsuarioMapper {

    UsuarioResponseDTO toResponseDTO(Usuario usuario);
}
