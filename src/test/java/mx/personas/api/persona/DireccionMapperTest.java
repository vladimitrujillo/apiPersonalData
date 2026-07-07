package mx.personas.api.persona;

import mx.personas.api.persona.dto.DireccionResponseDTO;
import mx.personas.api.persona.mapper.DireccionMapperImpl;
import mx.personas.api.persona.model.Direccion;
import mx.personas.api.persona.model.Persona;
import mx.personas.api.usuario.model.Rol;
import mx.personas.api.usuario.model.Usuario;
import mx.personas.api.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Ejerce directamente DireccionMapperImpl (generado por MapStruct) — FR-002/FR-003. */
@ExtendWith(MockitoExtension.class)
class DireccionMapperTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    private DireccionMapperImpl direccionMapper;

    @BeforeEach
    void construirMapper() {
        direccionMapper = new DireccionMapperImpl();
        ReflectionTestUtils.setField(direccionMapper, "usuarioRepository", usuarioRepository);
    }

    private Direccion direccionDeEjemplo() {
        Persona persona = new Persona("Juana", "Pérez López", LocalDate.of(1990, 5, 10), "F",
                "PELJ900510MDFRZN09", "PELJ900510AB1", "juana.perez@example.com", "5512345678");
        return new Direccion(persona, "Av. Insurgentes", "100", "Roma Norte", "Cuauhtémoc",
                "Ciudad de México", "06700", "MX", 1L);
    }

    @Test
    void toResponseDTOResuelveLoginDelCreadorYDelUltimoModificador() {
        Direccion direccion = direccionDeEjemplo();
        UUID creadorId = UUID.randomUUID();
        ReflectionTestUtils.setField(direccion, "creadoPor", creadorId);
        ReflectionTestUtils.setField(direccion, "actualizadoPor", creadorId);
        when(usuarioRepository.findById(creadorId)).thenReturn(
                Optional.of(new Usuario("admin", "hash", "Admin", Rol.ADMIN)));

        DireccionResponseDTO respuesta = direccionMapper.toResponseDTO(direccion);

        assertThat(respuesta.creadoPor()).isEqualTo("admin");
        assertThat(respuesta.modificadoPor()).isEqualTo("admin");
        assertThat(respuesta.calle()).isEqualTo("Av. Insurgentes");
    }

    @Test
    void toResponseDTORegresaNullCuandoNoHayAutorConocido() {
        Direccion direccion = direccionDeEjemplo();

        DireccionResponseDTO respuesta = direccionMapper.toResponseDTO(direccion);

        assertThat(respuesta.creadoPor()).isNull();
        assertThat(respuesta.modificadoPor()).isNull();
    }
}
