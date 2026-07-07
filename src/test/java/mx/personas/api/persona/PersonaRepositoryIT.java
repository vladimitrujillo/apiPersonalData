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
 * Verifica, a nivel de base de datos: el indice parcial de correo (WHERE activo = true)
 * permite reutilizar el correo de una persona eliminada logicamente (D3, sin cambio); la
 * restriccion UNIQUE global de curp (uq_persona_curp,
 * 004-restaurar-persona-curp/V4__globalizar_unicidad_curp.sql) impide reutilizar la CURP
 * de una persona eliminada logicamente, a diferencia del correo.
 */
class PersonaRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private PersonaRepository personaRepository;

    private Persona nuevaPersona(String correo, String curp) {
        return new Persona("Ana", "Gómez", LocalDate.of(1985, 1, 1), "F",
                curp, "GOAA850101AB1", correo, "5511122233");
    }

    @Test
    void permiteReutilizarCorreoDePersonaEliminadaLogicamentePeroNoLaCurp() {
        String correo = "reuso." + System.nanoTime() + "@example.com";
        String curpOriginal = "GOAA850101MDFMXN" + TestUniqueId.homoclave();

        Persona original = personaRepository.saveAndFlush(nuevaPersona(correo, curpOriginal));
        original.eliminarLogicamente();
        personaRepository.saveAndFlush(original);

        Persona nueva = personaRepository.saveAndFlush(
                nuevaPersona(correo, "GOAA850101MDFMXN" + TestUniqueId.homoclave()));

        assertThat(nueva.getId()).isNotEqualTo(original.getId());
        assertThat(personaRepository.existsByCorreoAndActivoTrue(correo)).isTrue();
        assertThat(personaRepository.findByIdAndActivoTrue(original.getId())).isEmpty();
        assertThat(personaRepository.findByIdAndActivoTrue(nueva.getId())).isPresent();
    }

    @Test
    void impideReutilizarCurpDePersonaEliminadaLogicamente() {
        String curp = "GOAA850101MDFMXN" + TestUniqueId.homoclave();
        Persona original = personaRepository.saveAndFlush(
                nuevaPersona("original." + System.nanoTime() + "@example.com", curp));
        original.eliminarLogicamente();
        personaRepository.saveAndFlush(original);

        assertThatThrownBy(() -> personaRepository.saveAndFlush(
                nuevaPersona("nueva." + System.nanoTime() + "@example.com", curp)))
                .isInstanceOf(DataIntegrityViolationException.class);
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
