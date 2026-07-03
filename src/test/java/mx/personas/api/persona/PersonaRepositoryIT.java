package mx.personas.api.persona;

import mx.personas.api.common.AbstractIntegrationTest;
import mx.personas.api.common.TestUniqueId;
import mx.personas.api.persona.model.Persona;
import mx.personas.api.persona.repository.PersonaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica que el indice unico parcial (WHERE activo = true, research.md #2) permite
 * reutilizar correo/CURP de una persona eliminada logicamente, pero sigue impidiendo
 * duplicados entre personas activas a nivel de base de datos.
 */
class PersonaRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private PersonaRepository personaRepository;

    private Persona nuevaPersona(String correo, String curp) {
        return new Persona("Ana", "Gómez", LocalDate.of(1985, 1, 1), "F",
                curp, "GOAA850101AB1", correo, "5511122233");
    }

    @Test
    void permiteReutilizarCorreoYCurpDePersonaEliminadaLogicamente() {
        String correo = "reuso." + System.nanoTime() + "@example.com";
        String curp = "GOAA850101MDFMXN" + TestUniqueId.homoclave();

        Persona original = personaRepository.saveAndFlush(nuevaPersona(correo, curp));
        original.eliminarLogicamente();
        personaRepository.saveAndFlush(original);

        Persona nueva = personaRepository.saveAndFlush(nuevaPersona(correo, curp));

        assertThat(nueva.getId()).isNotEqualTo(original.getId());
        assertThat(personaRepository.existsByCorreoAndActivoTrue(correo)).isTrue();
        assertThat(personaRepository.findByIdAndActivoTrue(original.getId())).isEmpty();
        assertThat(personaRepository.findByIdAndActivoTrue(nueva.getId())).isPresent();
    }

    @Test
    void impideCorreoDuplicadoEntreDosPersonasActivas() {
        String correo = "duplicado." + System.nanoTime() + "@example.com";
        personaRepository.saveAndFlush(nuevaPersona(correo, "GOAA850101MDFMXN" + TestUniqueId.homoclave()));

        assertThatThrownBy(() -> personaRepository.saveAndFlush(
                nuevaPersona(correo, "GOAA850101MDFMXN" + TestUniqueId.homoclave())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
