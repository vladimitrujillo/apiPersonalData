package mx.personas.api.persona;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.personas.api.persona.dto.CampoCambiadoDTO;
import mx.personas.api.persona.model.Direccion;
import mx.personas.api.persona.model.Persona;
import mx.personas.api.persona.service.HistorialDiffService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class HistorialDiffServiceTest {

    private final HistorialDiffService service = new HistorialDiffService(new ObjectMapper());

    private Persona personaDeEjemplo() {
        return new Persona("Juana", "Pérez López", LocalDate.of(1990, 5, 10), "F",
                "PELJ900510MDFRZN09", "PELJ900510AB1", "juana.perez@example.com", "5512345678");
    }

    private Direccion direccionDeEjemplo(Persona persona) {
        return new Direccion(persona, "Av. Insurgentes", "100", "Roma Norte", "Cuauhtémoc",
                "Ciudad de México", "06700", "MX", 1L);
    }

    @Test
    void serializarCreacionIncluyeTodosLosCamposConValorAnteriorNulo() {
        Persona persona = personaDeEjemplo();
        Direccion direccion = direccionDeEjemplo(persona);

        List<CampoCambiadoDTO> cambios = service.deserializar(service.serializarCreacion(persona, direccion));

        assertThat(cambios).allMatch(c -> c.valorAnterior() == null);
        assertThat(cambios).extracting(CampoCambiadoDTO::campo).contains(
                "nombres", "apellidos", "curp", "rfc", "correo", "telefono",
                "direccion.calle", "direccion.colonia", "direccion.pais");
    }

    @Test
    void serializarCreacionEnmascaraCurpRfcYTelefono() {
        Persona persona = personaDeEjemplo();
        Direccion direccion = direccionDeEjemplo(persona);

        List<CampoCambiadoDTO> cambios = service.deserializar(service.serializarCreacion(persona, direccion));

        String curp = cambios.stream().filter(c -> c.campo().equals("curp")).findFirst().orElseThrow().valorNuevo();
        String telefono = cambios.stream().filter(c -> c.campo().equals("telefono")).findFirst()
                .orElseThrow().valorNuevo();
        assertThat(curp).isEqualTo("PE**************09");
        assertThat(telefono).isEqualTo("55******78");
        String correo = cambios.stream().filter(c -> c.campo().equals("correo")).findFirst()
                .orElseThrow().valorNuevo();
        assertThat(correo).isEqualTo("juana.perez@example.com");
    }

    @Test
    void serializarModificacionSoloIncluyeCamposQueRealmenteCambiaron() {
        Persona persona = personaDeEjemplo();
        Direccion direccion = direccionDeEjemplo(persona);
        HistorialDiffService.PersonaSnapshot antesPersona = HistorialDiffService.PersonaSnapshot.de(persona);
        HistorialDiffService.DireccionSnapshot antesDireccion = HistorialDiffService.DireccionSnapshot.de(direccion);

        persona.setTelefono("5599988877");

        Optional<String> resultado = service.serializarModificacion(antesPersona, persona, antesDireccion, direccion);

        assertThat(resultado).isPresent();
        List<CampoCambiadoDTO> cambios = service.deserializar(resultado.get());
        assertThat(cambios).hasSize(1);
        assertThat(cambios.get(0).campo()).isEqualTo("telefono");
        assertThat(cambios.get(0).valorAnterior()).isEqualTo("55******78");
        assertThat(cambios.get(0).valorNuevo()).isEqualTo("55******77");
    }

    @Test
    void serializarModificacionIncluyeCamposDeDireccionConPrefijo() {
        Persona persona = personaDeEjemplo();
        Direccion direccion = direccionDeEjemplo(persona);
        HistorialDiffService.PersonaSnapshot antesPersona = HistorialDiffService.PersonaSnapshot.de(persona);
        HistorialDiffService.DireccionSnapshot antesDireccion = HistorialDiffService.DireccionSnapshot.de(direccion);

        direccion.actualizar("Otra Calle", "200", "Roma Norte", "Cuauhtémoc",
                "Ciudad de México", "06700", "MX", 1L);

        List<CampoCambiadoDTO> cambios = service.deserializar(
                service.serializarModificacion(antesPersona, persona, antesDireccion, direccion).orElseThrow());

        assertThat(cambios).extracting(CampoCambiadoDTO::campo).containsExactlyInAnyOrder(
                "direccion.calle", "direccion.numero");
    }

    @Test
    void serializarModificacionSinCambiosRealesRegresaVacio() {
        Persona persona = personaDeEjemplo();
        Direccion direccion = direccionDeEjemplo(persona);
        HistorialDiffService.PersonaSnapshot antesPersona = HistorialDiffService.PersonaSnapshot.de(persona);
        HistorialDiffService.DireccionSnapshot antesDireccion = HistorialDiffService.DireccionSnapshot.de(direccion);

        Optional<String> resultado = service.serializarModificacion(antesPersona, persona, antesDireccion, direccion);

        assertThat(resultado).isEmpty();
    }

    @Test
    void serializarCambioEstadoActivoProduceUnaSolaEntrada() {
        List<CampoCambiadoDTO> cambios = service.deserializar(service.serializarCambioEstadoActivo(true, false));

        assertThat(cambios).hasSize(1);
        assertThat(cambios.get(0)).isEqualTo(new CampoCambiadoDTO("activo", "true", "false"));
    }
}
