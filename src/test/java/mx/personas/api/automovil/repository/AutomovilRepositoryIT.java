package mx.personas.api.automovil.repository;

import mx.personas.api.automovil.model.Automovil;
import mx.personas.api.common.AbstractIntegrationTest;
import mx.personas.api.common.TestUniqueId;
import mx.personas.api.persona.model.Direccion;
import mx.personas.api.persona.model.Persona;
import mx.personas.api.persona.repository.DireccionRepository;
import mx.personas.api.persona.repository.PersonaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutomovilRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private AutomovilRepository automovilRepository;

    @Autowired
    private PersonaRepository personaRepository;

    @Autowired
    private DireccionRepository direccionRepository;

    private Persona crearPersona() {
        String h = TestUniqueId.homoclave();
        Persona persona = personaRepository.save(new Persona("Test", "Automovil " + h,
                LocalDate.of(1990, 1, 1), "F", "AUTO900101MDFRZN" + h, "AUTO900101AB" + h.charAt(0),
                "automovil." + System.nanoTime() + "@example.com", "5500099988"));
        direccionRepository.save(new Direccion(persona, "Calle", "1", "Centro", "Municipio", "Estado", "00000",
                "US", null));
        return persona;
    }

    @Test
    void dosAutomovilesActivosConLasMismasPlacasViolanElIndiceUnico() {
        Persona persona = crearPersona();
        automovilRepository.saveAndFlush(new Automovil(persona, "Nissan", "Versa", (short) 2020, "Rojo",
                "PLACA-DUP", null));

        assertThatThrownBy(() -> automovilRepository.saveAndFlush(new Automovil(persona, "Toyota", "Corolla",
                (short) 2021, "Azul", "PLACA-DUP", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unAutomovilActivoYUnoEliminadoConLasMismasPlacasNoViolanNada() {
        Persona persona = crearPersona();
        Automovil eliminado = automovilRepository.saveAndFlush(new Automovil(persona, "Nissan", "Versa",
                (short) 2020, "Rojo", "PLACA-RES", null));
        eliminado.desactivar();
        automovilRepository.saveAndFlush(eliminado);

        Automovil nuevo = automovilRepository.saveAndFlush(new Automovil(persona, "Toyota", "Corolla",
                (short) 2021, "Azul", "PLACA-RES", null));

        assertThat(nuevo.getId()).isNotEqualTo(eliminado.getId());
    }

    @Test
    void dosAutomovilesConElMismoVinViolanElIndiceUnicoSinImportarSuEstado() {
        Persona persona = crearPersona();
        // VIN unico por ejecucion (no un literal compartido con otras clases de test:
        // el VIN tiene unicidad GLOBAL en la BD compartida entre clases IT).
        String vin = "1HGCM8263" + TestUniqueId.homoclave() + "004354";
        Automovil primero = automovilRepository.saveAndFlush(new Automovil(persona, "Nissan", "Versa",
                (short) 2020, "Rojo", "PLACA-VN1", vin));
        primero.desactivar();
        automovilRepository.saveAndFlush(primero);

        assertThatThrownBy(() -> automovilRepository.saveAndFlush(new Automovil(persona, "Toyota", "Corolla",
                (short) 2021, "Azul", "PLACA-VN2", vin)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
