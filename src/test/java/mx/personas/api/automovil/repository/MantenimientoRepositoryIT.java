package mx.personas.api.automovil.repository;

import mx.personas.api.automovil.model.Automovil;
import mx.personas.api.automovil.model.Mantenimiento;
import mx.personas.api.automovil.model.PiezaCambiada;
import mx.personas.api.common.AbstractIntegrationTest;
import mx.personas.api.common.TestUniqueId;
import mx.personas.api.persona.model.Direccion;
import mx.personas.api.persona.model.Persona;
import mx.personas.api.persona.repository.DireccionRepository;
import mx.personas.api.persona.repository.PersonaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MantenimientoRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private AutomovilRepository automovilRepository;

    @Autowired
    private MantenimientoRepository mantenimientoRepository;

    @Autowired
    private PersonaRepository personaRepository;

    @Autowired
    private DireccionRepository direccionRepository;

    private Automovil crearAutomovil() {
        String h = TestUniqueId.homoclave();
        Persona persona = personaRepository.save(new Persona("Test", "Mantenimiento " + h,
                LocalDate.of(1990, 1, 1), "F", "MANT900101MDFRZN" + h, "MANT900101AB" + h.charAt(0),
                "mantenimiento." + System.nanoTime() + "@example.com", "5500099977"));
        direccionRepository.save(new Direccion(persona, "Calle", "1", "Centro", "Municipio", "Estado", "00000",
                "US", null));
        return automovilRepository.save(new Automovil(persona, "Nissan", "Versa", (short) 2020, "Rojo",
                "PLACA-" + h, null));
    }

    @Test
    void guardarUnMantenimientoConPiezasLasPersisteEnCascada() {
        Automovil automovil = crearAutomovil();
        Mantenimiento mantenimiento = new Mantenimiento(automovil, "Servicio", LocalDate.now(), 1000, null,
                new BigDecimal("500.00"));
        mantenimiento.actualizarPiezas(List.of(
                new PiezaCambiada(mantenimiento, "Filtro", null, new BigDecimal("100.00")),
                new PiezaCambiada(mantenimiento, "Aceite", "AC-1", new BigDecimal("400.00"))));

        Mantenimiento guardado = mantenimientoRepository.saveAndFlush(mantenimiento);

        Mantenimiento recargado = mantenimientoRepository.findById(guardado.getId()).orElseThrow();
        assertThat(recargado.getPiezas()).hasSize(2);
    }

    @Test
    void elHistorialSeOrdenaPorFechaDescendente() {
        Automovil automovil = crearAutomovil();
        mantenimientoRepository.save(new Mantenimiento(automovil, "Primero", LocalDate.now().minusDays(10), 1000,
                null, BigDecimal.ZERO));
        Mantenimiento masReciente = mantenimientoRepository.save(new Mantenimiento(automovil, "Segundo",
                LocalDate.now(), 2000, null, BigDecimal.ZERO));

        Page<Mantenimiento> pagina = mantenimientoRepository
                .findByAutomovilIdAndActivoTrueOrderByFechaDescCreatedAtDesc(automovil.getId(),
                        PageRequest.of(0, 20));

        assertThat(pagina.getContent()).hasSize(2);
        assertThat(pagina.getContent().get(0).getId()).isEqualTo(masReciente.getId());
    }

    @Test
    void findFirstRegresaElMantenimientoActivoConLaFechaMasReciente() {
        Automovil automovil = crearAutomovil();
        mantenimientoRepository.save(new Mantenimiento(automovil, "Viejo", LocalDate.now().minusDays(30), 1000,
                null, BigDecimal.ZERO));
        Mantenimiento reciente = mantenimientoRepository.save(new Mantenimiento(automovil, "Reciente",
                LocalDate.now(), 5000, null, BigDecimal.ZERO));

        Optional<Mantenimiento> masReciente = mantenimientoRepository
                .findFirstByAutomovilIdAndActivoTrueOrderByFechaDescCreatedAtDesc(automovil.getId());

        assertThat(masReciente).isPresent();
        assertThat(masReciente.get().getId()).isEqualTo(reciente.getId());
    }

    @Test
    void reemplazarLaColeccionDePiezasBorraLasHuerfanas() {
        Automovil automovil = crearAutomovil();
        Mantenimiento mantenimiento = new Mantenimiento(automovil, "Servicio", LocalDate.now(), 1000, null,
                BigDecimal.ZERO);
        mantenimiento.actualizarPiezas(List.of(new PiezaCambiada(mantenimiento, "Filtro", null, null)));
        Mantenimiento guardado = mantenimientoRepository.saveAndFlush(mantenimiento);
        assertThat(guardado.getPiezas()).hasSize(1);

        guardado.actualizarPiezas(List.of(new PiezaCambiada(guardado, "Bujías", null, null)));
        mantenimientoRepository.saveAndFlush(guardado);

        Mantenimiento recargado = mantenimientoRepository.findById(guardado.getId()).orElseThrow();
        assertThat(recargado.getPiezas()).hasSize(1);
        assertThat(recargado.getPiezas().get(0).getNombre()).isEqualTo("Bujías");
    }
}
