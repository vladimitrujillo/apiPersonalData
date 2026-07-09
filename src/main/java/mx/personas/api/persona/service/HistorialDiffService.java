package mx.personas.api.persona.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import mx.personas.api.common.audit.MaskingUtil;
import mx.personas.api.persona.dto.CampoCambiadoDTO;
import mx.personas.api.persona.model.Direccion;
import mx.personas.api.persona.model.Persona;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Calcula el diff campo-por-campo de cada operación sobre una persona/dirección y lo
 * serializa a JSON para persona_historial.cambios, aplicando enmascarado a CURP/RFC/
 * teléfono antes de serializar (FR-006 a FR-008, research.md §5-6).
 */
@Service
public class HistorialDiffService {

    private static final String PREFIJO_DIRECCION = "direccion.";

    private final ObjectMapper objectMapper;

    public HistorialDiffService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record PersonaSnapshot(String nombres, String apellidos, LocalDate fechaNacimiento, String sexo,
                                   String curp, String rfc, String correo, String telefono) {
        public static PersonaSnapshot de(Persona persona) {
            return new PersonaSnapshot(persona.getNombres(), persona.getApellidos(), persona.getFechaNacimiento(),
                    persona.getSexo(), persona.getCurp(), persona.getRfc(), persona.getCorreo(),
                    persona.getTelefono());
        }
    }

    public record DireccionSnapshot(String calle, String numero, String colonia, String municipio,
                                     String estado, String codigoPostal, String pais) {
        public static DireccionSnapshot de(Direccion direccion) {
            return new DireccionSnapshot(direccion.getCalle(), direccion.getNumero(), direccion.getColonia(),
                    direccion.getMunicipio(), direccion.getEstado(), direccion.getCodigoPostal(),
                    direccion.getPais());
        }
    }

    public String serializarCreacion(Persona persona, Direccion direccion) {
        List<CampoCambiadoDTO> cambios = new ArrayList<>();
        cambios.add(new CampoCambiadoDTO("nombres", null, persona.getNombres()));
        cambios.add(new CampoCambiadoDTO("apellidos", null, persona.getApellidos()));
        cambios.add(new CampoCambiadoDTO("fechaNacimiento", null, String.valueOf(persona.getFechaNacimiento())));
        cambios.add(new CampoCambiadoDTO("sexo", null, persona.getSexo()));
        cambios.add(new CampoCambiadoDTO("curp", null, MaskingUtil.enmascarar(persona.getCurp())));
        cambios.add(new CampoCambiadoDTO("rfc", null, MaskingUtil.enmascarar(persona.getRfc())));
        cambios.add(new CampoCambiadoDTO("correo", null, persona.getCorreo()));
        cambios.add(new CampoCambiadoDTO("telefono", null, MaskingUtil.enmascarar(persona.getTelefono())));
        cambios.add(new CampoCambiadoDTO(PREFIJO_DIRECCION + "calle", null, direccion.getCalle()));
        cambios.add(new CampoCambiadoDTO(PREFIJO_DIRECCION + "numero", null, direccion.getNumero()));
        cambios.add(new CampoCambiadoDTO(PREFIJO_DIRECCION + "colonia", null, direccion.getColonia()));
        cambios.add(new CampoCambiadoDTO(PREFIJO_DIRECCION + "municipio", null, direccion.getMunicipio()));
        cambios.add(new CampoCambiadoDTO(PREFIJO_DIRECCION + "estado", null, direccion.getEstado()));
        cambios.add(new CampoCambiadoDTO(PREFIJO_DIRECCION + "codigoPostal", null, direccion.getCodigoPostal()));
        cambios.add(new CampoCambiadoDTO(PREFIJO_DIRECCION + "pais", null, direccion.getPais()));
        return serializar(cambios);
    }

    public Optional<String> serializarModificacion(PersonaSnapshot antes, Persona actual,
                                                     DireccionSnapshot antesDireccion, Direccion actualDireccion) {
        List<CampoCambiadoDTO> cambios = new ArrayList<>();
        agregarSiCambio(cambios, "nombres", antes.nombres(), actual.getNombres());
        agregarSiCambio(cambios, "apellidos", antes.apellidos(), actual.getApellidos());
        agregarSiCambio(cambios, "fechaNacimiento",
                String.valueOf(antes.fechaNacimiento()), String.valueOf(actual.getFechaNacimiento()));
        agregarSiCambio(cambios, "sexo", antes.sexo(), actual.getSexo());
        agregarSiCambioEnmascarado(cambios, "curp", antes.curp(), actual.getCurp());
        agregarSiCambioEnmascarado(cambios, "rfc", antes.rfc(), actual.getRfc());
        agregarSiCambio(cambios, "correo", antes.correo(), actual.getCorreo());
        agregarSiCambioEnmascarado(cambios, "telefono", antes.telefono(), actual.getTelefono());

        agregarSiCambio(cambios, PREFIJO_DIRECCION + "calle", antesDireccion.calle(), actualDireccion.getCalle());
        agregarSiCambio(cambios, PREFIJO_DIRECCION + "numero", antesDireccion.numero(), actualDireccion.getNumero());
        agregarSiCambio(cambios, PREFIJO_DIRECCION + "colonia",
                antesDireccion.colonia(), actualDireccion.getColonia());
        agregarSiCambio(cambios, PREFIJO_DIRECCION + "municipio",
                antesDireccion.municipio(), actualDireccion.getMunicipio());
        agregarSiCambio(cambios, PREFIJO_DIRECCION + "estado", antesDireccion.estado(), actualDireccion.getEstado());
        agregarSiCambio(cambios, PREFIJO_DIRECCION + "codigoPostal",
                antesDireccion.codigoPostal(), actualDireccion.getCodigoPostal());
        agregarSiCambio(cambios, PREFIJO_DIRECCION + "pais", antesDireccion.pais(), actualDireccion.getPais());

        if (cambios.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(serializar(cambios));
    }

    public String serializarCambioEstadoActivo(boolean valorAnterior, boolean valorNuevo) {
        return serializar(List.of(
                new CampoCambiadoDTO("activo", String.valueOf(valorAnterior), String.valueOf(valorNuevo))));
    }

    /** Registra la asignación de una profesión a una persona (007-profesiones-personas, FR-024). */
    public String serializarAsignacionProfesion(mx.personas.api.profesion.model.PersonaProfesion asignacion) {
        List<CampoCambiadoDTO> cambios = new ArrayList<>();
        cambios.add(new CampoCambiadoDTO("profesion", null, asignacion.getProfesion().getNombre()));
        cambios.add(new CampoCambiadoDTO("profesion.fechaDesde", null, String.valueOf(asignacion.getFechaDesde())));
        if (asignacion.getCedula() != null) {
            cambios.add(new CampoCambiadoDTO("profesion.cedula", null, asignacion.getCedula()));
        }
        return serializar(cambios);
    }

    /** Registra el retiro de una asignación de profesión (007-profesiones-personas, FR-024). */
    public String serializarRetiroProfesion(mx.personas.api.profesion.model.PersonaProfesion asignacion) {
        List<CampoCambiadoDTO> cambios = new ArrayList<>();
        cambios.add(new CampoCambiadoDTO("profesion.retirada", null, asignacion.getProfesion().getNombre()));
        return serializar(cambios);
    }

    public record AutomovilSnapshot(String marca, String modelo, Short anio, String color, String placas) {
        public static AutomovilSnapshot de(mx.personas.api.automovil.model.Automovil automovil) {
            return new AutomovilSnapshot(automovil.getMarca(), automovil.getModelo(), automovil.getAnio(),
                    automovil.getColor(), automovil.getPlacas());
        }
    }

    /** Registra la edición de un automóvil (008-automoviles-mantenimientos, FR-028). */
    public Optional<String> serializarEdicionAutomovil(AutomovilSnapshot antes,
                                                          mx.personas.api.automovil.model.Automovil actual) {
        List<CampoCambiadoDTO> cambios = new ArrayList<>();
        agregarSiCambio(cambios, "automovil.marca", antes.marca(), actual.getMarca());
        agregarSiCambio(cambios, "automovil.modelo", antes.modelo(), actual.getModelo());
        agregarSiCambio(cambios, "automovil.anio", String.valueOf(antes.anio()), String.valueOf(actual.getAnio()));
        agregarSiCambio(cambios, "automovil.color", antes.color(), actual.getColor());
        agregarSiCambio(cambios, "automovil.placas", antes.placas(), actual.getPlacas());
        if (cambios.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(serializar(cambios));
    }

    /** Registra la baja lógica de un automóvil (008-automoviles-mantenimientos, FR-028). */
    public String serializarBajaAutomovil(mx.personas.api.automovil.model.Automovil automovil) {
        return serializar(List.of(new CampoCambiadoDTO("automovil.activo", "true", "false")));
    }

    /** Registra la restauración de un automóvil (008-automoviles-mantenimientos, FR-028). */
    public String serializarRestauracionAutomovil(mx.personas.api.automovil.model.Automovil automovil) {
        return serializar(List.of(new CampoCambiadoDTO("automovil.activo", "false", "true")));
    }

    /** Registra el alta de un automóvil (008-automoviles-mantenimientos, FR-028). */
    public String serializarAltaAutomovil(mx.personas.api.automovil.model.Automovil automovil) {
        List<CampoCambiadoDTO> cambios = new ArrayList<>();
        cambios.add(new CampoCambiadoDTO("automovil.marca", null, automovil.getMarca()));
        cambios.add(new CampoCambiadoDTO("automovil.modelo", null, automovil.getModelo()));
        cambios.add(new CampoCambiadoDTO("automovil.anio", null, String.valueOf(automovil.getAnio())));
        cambios.add(new CampoCambiadoDTO("automovil.color", null, automovil.getColor()));
        cambios.add(new CampoCambiadoDTO("automovil.placas", null, automovil.getPlacas()));
        if (automovil.getVin() != null) {
            cambios.add(new CampoCambiadoDTO("automovil.vin", null, automovil.getVin()));
        }
        return serializar(cambios);
    }

    public record MantenimientoSnapshot(String descripcion, java.time.LocalDate fecha, Integer kilometraje,
                                          java.math.BigDecimal costoTotal,
                                          java.util.UUID mecanicoId) {
        public static MantenimientoSnapshot de(mx.personas.api.automovil.model.Mantenimiento mantenimiento) {
            return new MantenimientoSnapshot(mantenimiento.getDescripcion(), mantenimiento.getFecha(),
                    mantenimiento.getKilometraje(), mantenimiento.getCostoTotal(), mantenimiento.getMecanicoId());
        }
    }

    /** Registra la edición de un mantenimiento (008-automoviles-mantenimientos, FR-028). */
    public Optional<String> serializarEdicionMantenimiento(MantenimientoSnapshot antes,
            mx.personas.api.automovil.model.Mantenimiento actual) {
        List<CampoCambiadoDTO> cambios = new ArrayList<>();
        agregarSiCambio(cambios, "mantenimiento.descripcion", antes.descripcion(), actual.getDescripcion());
        agregarSiCambio(cambios, "mantenimiento.fecha", String.valueOf(antes.fecha()),
                String.valueOf(actual.getFecha()));
        agregarSiCambio(cambios, "mantenimiento.kilometraje", String.valueOf(antes.kilometraje()),
                String.valueOf(actual.getKilometraje()));
        agregarSiCambio(cambios, "mantenimiento.costoTotal", String.valueOf(antes.costoTotal()),
                String.valueOf(actual.getCostoTotal()));
        agregarSiCambio(cambios, "mantenimiento.mecanicoId", String.valueOf(antes.mecanicoId()),
                String.valueOf(actual.getMecanicoId()));
        if (cambios.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(serializar(cambios));
    }

    /** Registra la baja lógica de un mantenimiento (008-automoviles-mantenimientos, FR-028). */
    public String serializarBajaMantenimiento(mx.personas.api.automovil.model.Mantenimiento mantenimiento) {
        return serializar(List.of(new CampoCambiadoDTO("mantenimiento.activo", "true", "false")));
    }

    /** Registra la restauración de un mantenimiento (008-automoviles-mantenimientos, FR-028). */
    public String serializarRestauracionMantenimiento(mx.personas.api.automovil.model.Mantenimiento mantenimiento) {
        return serializar(List.of(new CampoCambiadoDTO("mantenimiento.activo", "false", "true")));
    }

    /** Registra el alta de un mantenimiento (008-automoviles-mantenimientos, FR-028). */
    public String serializarRegistroMantenimiento(mx.personas.api.automovil.model.Mantenimiento mantenimiento) {
        List<CampoCambiadoDTO> cambios = new ArrayList<>();
        cambios.add(new CampoCambiadoDTO("mantenimiento.descripcion", null, mantenimiento.getDescripcion()));
        cambios.add(new CampoCambiadoDTO("mantenimiento.fecha", null, String.valueOf(mantenimiento.getFecha())));
        cambios.add(new CampoCambiadoDTO("mantenimiento.kilometraje", null,
                String.valueOf(mantenimiento.getKilometraje())));
        cambios.add(new CampoCambiadoDTO("mantenimiento.costoTotal", null,
                String.valueOf(mantenimiento.getCostoTotal())));
        if (mantenimiento.getMecanicoId() != null) {
            cambios.add(new CampoCambiadoDTO("mantenimiento.mecanicoId", null,
                    String.valueOf(mantenimiento.getMecanicoId())));
        }
        return serializar(cambios);
    }

    public List<CampoCambiadoDTO> deserializar(String cambiosJson) {
        try {
            return objectMapper.readValue(cambiosJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, CampoCambiadoDTO.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo leer una entrada del historial de cambios", e);
        }
    }

    private void agregarSiCambio(List<CampoCambiadoDTO> cambios, String campo, String antes, String despues) {
        if (!Objects.equals(antes, despues)) {
            cambios.add(new CampoCambiadoDTO(campo, antes, despues));
        }
    }

    private void agregarSiCambioEnmascarado(List<CampoCambiadoDTO> cambios, String campo, String antes,
                                             String despues) {
        if (!Objects.equals(antes, despues)) {
            cambios.add(new CampoCambiadoDTO(campo, MaskingUtil.enmascarar(antes), MaskingUtil.enmascarar(despues)));
        }
    }

    private String serializar(List<CampoCambiadoDTO> cambios) {
        try {
            return objectMapper.writeValueAsString(cambios);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar una entrada del historial de cambios", e);
        }
    }
}
