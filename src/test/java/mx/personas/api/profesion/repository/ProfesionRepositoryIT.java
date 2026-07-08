package mx.personas.api.profesion.repository;

import mx.personas.api.common.AbstractIntegrationTest;
import mx.personas.api.common.TestUniqueId;
import mx.personas.api.profesion.model.Profesion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-004: unicidad de profesion.nombre insensible a mayusculas y acentos
 * (research.md §1). Testcontainers/PostgreSQL real - unaccent no existe en H2.
 */
class ProfesionRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private ProfesionRepository profesionRepository;

    @Test
    void findByNombreNormalizadoEncuentraLaSemillaSinAcentoYEnMinusculas() {
        Optional<Profesion> encontrada = profesionRepository.findByNombreNormalizado("mecanico");

        assertThat(encontrada).isPresent();
        assertThat(encontrada.get().getNombre()).isEqualTo("Mecánico");
    }

    @Test
    void elIndiceUnicoRechazaUnNombreDuplicadoInsensibleAMayusculasYAcentos() {
        String base = "Enfermero" + TestUniqueId.homoclave();
        profesionRepository.saveAndFlush(new Profesion(base, null));

        assertThatThrownBy(() -> profesionRepository.saveAndFlush(new Profesion(base.toUpperCase(), null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
