package mx.personas.api.persona;

import mx.personas.api.persona.dto.PersonaResponseDTO;
import mx.personas.api.persona.dto.PersonaResumenDTO;
import mx.personas.api.persona.mapper.DireccionMapperImpl;
import mx.personas.api.persona.mapper.PersonaMapperImpl;
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

/**
 * Ejerce directamente los mappers generados por MapStruct (PersonaMapperImpl,
 * DireccionMapperImpl) con un UsuarioRepository simulado: es el mecanismo real detrás de
 * FR-003/FR-004 (US1), que ningún @WebMvcTest ejercita (ahí PersonaService está mockeado).
 */
@ExtendWith(MockitoExtension.class)
class PersonaMapperTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    private PersonaMapperImpl personaMapper;

    @BeforeEach
    void construirMapper() {
        DireccionMapperImpl direccionMapper = new DireccionMapperImpl();
        ReflectionTestUtils.setField(direccionMapper, "usuarioRepository", usuarioRepository);

        personaMapper = new PersonaMapperImpl();
        ReflectionTestUtils.setField(personaMapper, "usuarioRepository", usuarioRepository);
        ReflectionTestUtils.setField(personaMapper, "direccionMapper", direccionMapper);
    }

    private Persona personaDeEjemplo() {
        return new Persona("Juana", "Pérez López", LocalDate.of(1990, 5, 10), "F",
                "PELJ900510MDFRZN09", "PELJ900510AB1", "juana.perez@example.com", "5512345678");
    }

    private Direccion direccionDeEjemplo(Persona persona) {
        return new Direccion(persona, "Av. Insurgentes", "100", "Roma Norte", "Cuauhtémoc",
                "Ciudad de México", "06700", "MX", 1L);
    }

    @Test
    void toResponseDTOResuelveLoginsDeCreadoPorYActualizadoPor() {
        Persona persona = personaDeEjemplo();
        Direccion direccion = direccionDeEjemplo(persona);
        UUID creadorId = UUID.randomUUID();
        UUID modificadorId = UUID.randomUUID();
        ReflectionTestUtils.setField(persona, "creadoPor", creadorId);
        ReflectionTestUtils.setField(persona, "actualizadoPor", modificadorId);
        when(usuarioRepository.findById(creadorId)).thenReturn(
                Optional.of(new Usuario("admin", "hash", "Admin", Rol.ADMIN)));
        when(usuarioRepository.findById(modificadorId)).thenReturn(
                Optional.of(new Usuario("jperez", "hash", "Juan Pérez", Rol.CAPTURISTA)));

        PersonaResponseDTO respuesta = personaMapper.toResponseDTO(persona, direccion);

        assertThat(respuesta.creadoPor()).isEqualTo("admin");
        assertThat(respuesta.modificadoPor()).isEqualTo("jperez");
    }

    @Test
    void toResponseDTORegresaNullCuandoElUsuarioIdEsNull() {
        Persona persona = personaDeEjemplo();
        Direccion direccion = direccionDeEjemplo(persona);

        PersonaResponseDTO respuesta = personaMapper.toResponseDTO(persona, direccion);

        assertThat(respuesta.creadoPor()).isNull();
        assertThat(respuesta.modificadoPor()).isNull();
        assertThat(respuesta.direccion().creadoPor()).isNull();
    }

    @Test
    void toResponseDTORegresaNullCuandoElUsuarioIdNoSeEncuentra() {
        Persona persona = personaDeEjemplo();
        Direccion direccion = direccionDeEjemplo(persona);
        UUID idInexistente = UUID.randomUUID();
        ReflectionTestUtils.setField(persona, "creadoPor", idInexistente);
        when(usuarioRepository.findById(idInexistente)).thenReturn(Optional.empty());

        PersonaResponseDTO respuesta = personaMapper.toResponseDTO(persona, direccion);

        assertThat(respuesta.creadoPor()).isNull();
    }

    @Test
    void toResumenDTONuncaExponeDatosDeAuditoria() {
        Persona persona = personaDeEjemplo();
        Direccion direccion = direccionDeEjemplo(persona);
        ReflectionTestUtils.setField(persona, "creadoPor", UUID.randomUUID());

        PersonaResumenDTO resumen = personaMapper.toResumenDTO(persona, direccion);

        // PersonaResumenDTO/DireccionResumenDTO no declaran campos de auditoria en
        // absoluto (FR-004): que este test compile contra ese shape ya es parte de la
        // garantia; aquí solo confirmamos que el resto del contenido se mapeó bien.
        assertThat(resumen.nombres()).isEqualTo("Juana");
        assertThat(resumen.direccion().calle()).isEqualTo("Av. Insurgentes");
    }
}
