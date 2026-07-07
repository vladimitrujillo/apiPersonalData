package mx.personas.api.usuario.service;

import mx.personas.api.common.error.DuplicateFieldException;
import mx.personas.api.common.error.ErrorCode;
import mx.personas.api.common.error.RecursoNoEncontradoException;
import mx.personas.api.usuario.dto.UsuarioCreateDTO;
import mx.personas.api.usuario.dto.UsuarioResponseDTO;
import mx.personas.api.usuario.mapper.UsuarioMapper;
import mx.personas.api.usuario.model.Usuario;
import mx.personas.api.usuario.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioMapper usuarioMapper;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, UsuarioMapper usuarioMapper,
                           PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioMapper = usuarioMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public UsuarioResponseDTO crear(UsuarioCreateDTO dto) {
        if (usuarioRepository.existsByLogin(dto.login())) {
            throw new DuplicateFieldException(ErrorCode.USUARIO_LOGIN_DUPLICADO, "login",
                    "Ya existe un usuario registrado con este login",
                    "Debe ser único de forma global y permanente, incluso si el usuario original fue desactivado");
        }
        Usuario usuario = new Usuario(dto.login(), passwordEncoder.encode(dto.password()), dto.nombre(), dto.rol());
        usuarioRepository.save(usuario);
        return usuarioMapper.toResponseDTO(usuario);
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponseDTO> listar() {
        return usuarioRepository.findAll().stream()
                .map(usuarioMapper::toResponseDTO)
                .toList();
    }

    public UsuarioResponseDTO desactivar(UUID id) {
        Usuario usuario = obtenerOFallar(id);
        usuario.desactivar();
        return usuarioMapper.toResponseDTO(usuario);
    }

    public void restablecerContrasena(UUID id, String nuevaContrasena) {
        Usuario usuario = obtenerOFallar(id);
        usuario.restablecerContrasena(passwordEncoder.encode(nuevaContrasena));
    }

    private Usuario obtenerOFallar(UUID id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.USUARIO_NO_ENCONTRADO,
                        "No existe un usuario con el identificador '" + id + "'"));
    }
}
