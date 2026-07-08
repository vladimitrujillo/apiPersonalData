package mx.personas.api.profesion.repository;

import mx.personas.api.common.AbstractIntegrationTest;
import mx.personas.api.common.TestUniqueId;
import mx.personas.api.persona.model.Persona;
import mx.personas.api.persona.repository.PersonaRepository;
import mx.personas.api.profesion.model.PersonaProfesion;
import mx.personas.api.profesion.model.Profesion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-013: a lo sumo una asignacion activa por par persona-profesion, pero
 * varios episodios retirados del mismo par sí conviven (research.md §3-4).
 * Testcontainers/PostgreSQL real (indice parcial).
 */
class PersonaProfesionRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private PersonaRepository personaRepository;

    @Autowired
    private ProfesionRepository profesionRepository;

    @Autowired
    private PersonaProfesionRepository personaProfesionRepository;

    private Persona nuevaPersona() {
        String h = TestUniqueId.homoclave();
        return personaRepository.saveAndFlush(new Persona("Test", "Repo" + h, LocalDate.of(1990, 1, 1), "F",
                "REPO900101MDFXXX" + h, "REPO900101AB" + h.charAt(0), "repo." + System.nanoTime() + "@example.com",
                "5500000000"));
    }

    private Profesion nuevaProfesion() {
        return profesionRepository.saveAndFlush(new Profesion("Profesión Repo " + TestUniqueId.homoclave(), null));
    }

    @Test
    void dosAsignacionesActivasDelMismoParViolanElIndiceUnicoParcial() {
        Persona persona = nuevaPersona();
        Profesion profesion = nuevaProfesion();
        personaProfesionRepository.saveAndFlush(
                new PersonaProfesion(persona, profesion, LocalDate.now(), null));

        assertThatThrownBy(() -> personaProfesionRepository.saveAndFlush(
                new PersonaProfesion(persona, profesion, LocalDate.now(), null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void dosAsignacionesRetiradasDelMismoParNoViolanNada() {
        Persona persona = nuevaPersona();
        Profesion profesion = nuevaProfesion();

        PersonaProfesion primera = new PersonaProfesion(persona, profesion, LocalDate.now(), null);
        primera.retirar();
        personaProfesionRepository.saveAndFlush(primera);

        PersonaProfesion segunda = new PersonaProfesion(persona, profesion, LocalDate.now(), null);
        segunda.retirar();
        personaProfesionRepository.saveAndFlush(segunda);

        assertThat(personaProfesionRepository.findByPersonaId(persona.getId())).hasSize(2);
    }
}
