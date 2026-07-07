package mx.personas.api.usuario;

import mx.personas.api.common.error.ApiException;
import mx.personas.api.common.error.DuplicateFieldException;
import mx.personas.api.common.error.ErrorCode;
import mx.personas.api.common.error.RecursoNoEncontradoException;
import mx.personas.api.usuario.dto.UsuarioCreateDTO;
import mx.personas.api.usuario.dto.UsuarioResponseDTO;
import mx.personas.api.usuario.mapper.UsuarioMapper;
import mx.personas.api.usuario.model.Rol;
import mx.personas.api.usuario.model.Usuario;
import mx.personas.api.usuario.repository.UsuarioRepository;
import mx.personas.api.usuario.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private UsuarioMapper usuarioMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UsuarioService usuarioService() {
        return new UsuarioService(usuarioRepository, usuarioMapper, passwordEncoder);
    }

    @Test
    void crearConLoginNuevoHasheaLaContrasenaYPersiste() {
        when(usuarioRepository.existsByLogin("jperez")).thenReturn(false);
        when(passwordEncoder.encode("clave-en-claro")).thenReturn("hash-bcrypt");
        lenient().when(usuarioMapper.toResponseDTO(any())).thenReturn(
                new UsuarioResponseDTO(UUID.randomUUID(), "jperez", "Juan Pérez", Rol.CAPTURISTA, true));

        usuarioService().crear(new UsuarioCreateDTO("jperez", "clave-en-claro", "Juan Pérez", Rol.CAPTURISTA));

        verify(passwordEncoder).encode("clave-en-claro");
        verify(usuarioRepository).save(argThatUsuarioConHash("hash-bcrypt"));
    }

    private Usuario argThatUsuarioConHash(String hashEsperado) {
        return org.mockito.ArgumentMatchers.argThat(u -> u.getPasswordHash().equals(hashEsperado));
    }

    @Test
    void crearConLoginYaUsadoPorUsuarioActivoRegresa409() {
        when(usuarioRepository.existsByLogin("jperez")).thenReturn(true);

        assertThatThrownBy(() -> usuarioService().crear(
                new UsuarioCreateDTO("jperez", "clave-en-claro", "Juan Pérez", Rol.CAPTURISTA)))
                .isInstanceOf(DuplicateFieldException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USUARIO_LOGIN_DUPLICADO);
    }

    @Test
    void crearConLoginYaUsadoPorUsuarioDesactivadoTambienRegresa409() {
        // existsByLogin no filtra por activo (research.md #9): un login desactivado
        // sigue contando como "ya usado" para siempre (FR-011, FR-012).
        when(usuarioRepository.existsByLogin("jperez")).thenReturn(true);

        assertThatThrownBy(() -> usuarioService().crear(
                new UsuarioCreateDTO("jperez", "otra-clave", "Otro Nombre", Rol.CAPTURISTA)))
                .isInstanceOf(DuplicateFieldException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USUARIO_LOGIN_DUPLICADO);
    }

    @Test
    void desactivarUsuarioInexistenteRegresa404() {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService().desactivar(id))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USUARIO_NO_ENCONTRADO);
    }

    @Test
    void restablecerContrasenaHasheaLaNuevaContrasenaEInvalidaLaAnterior() {
        UUID id = UUID.randomUUID();
        Usuario usuario = new Usuario("jperez", "hash-anterior", "Juan Pérez", Rol.CAPTURISTA);
        when(usuarioRepository.findById(id)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.encode("clave-nueva")).thenReturn("hash-nuevo");

        usuarioService().restablecerContrasena(id, "clave-nueva");

        assertThat(usuario.getPasswordHash()).isEqualTo("hash-nuevo");
        assertThat(usuario.getPasswordHash()).isNotEqualTo("hash-anterior");
    }

    @Test
    void restablecerContrasenaDeUsuarioInexistenteRegresa404() {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService().restablecerContrasena(id, "clave-nueva"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USUARIO_NO_ENCONTRADO);
    }
}
