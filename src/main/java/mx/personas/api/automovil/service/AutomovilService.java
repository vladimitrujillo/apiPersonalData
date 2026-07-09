package mx.personas.api.automovil.service;

import mx.personas.api.automovil.dto.AutomovilRequestDTO;
import mx.personas.api.automovil.dto.AutomovilUpdateDTO;
import mx.personas.api.automovil.model.Automovil;
import mx.personas.api.automovil.repository.AutomovilRepository;
import mx.personas.api.common.audit.SecurityAuditorAware;
import mx.personas.api.common.error.AutomovilEliminadoException;
import mx.personas.api.common.error.AutomovilYaActivoException;
import mx.personas.api.common.error.DuplicateFieldException;
import mx.personas.api.common.error.ErrorCode;
import mx.personas.api.common.error.FormatoInvalidoException;
import mx.personas.api.common.error.PersonaEliminadaException;
import mx.personas.api.common.error.RecursoNoEncontradoException;
import mx.personas.api.persona.model.Persona;
import mx.personas.api.persona.model.PersonaHistorial;
import mx.personas.api.persona.model.PersonaHistorial.TipoOperacion;
import mx.personas.api.persona.repository.PersonaHistorialRepository;
import mx.personas.api.persona.repository.PersonaRepository;
import mx.personas.api.persona.service.HistorialDiffService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.UUID;

/**
 * Automóviles pertenecientes a una persona (US1, US2, US5, US7).
 */
@Service
public class AutomovilService {

    private static final short ANIO_MINIMO = 1900;

    private final PersonaRepository personaRepository;
    private final AutomovilRepository automovilRepository;
    private final PersonaHistorialRepository personaHistorialRepository;
    private final HistorialDiffService historialDiffService;
    private final SecurityAuditorAware securityAuditorAware;

    public AutomovilService(PersonaRepository personaRepository, AutomovilRepository automovilRepository,
                             PersonaHistorialRepository personaHistorialRepository,
                             HistorialDiffService historialDiffService,
                             SecurityAuditorAware securityAuditorAware) {
        this.personaRepository = personaRepository;
        this.automovilRepository = automovilRepository;
        this.personaHistorialRepository = personaHistorialRepository;
        this.historialDiffService = historialDiffService;
        this.securityAuditorAware = securityAuditorAware;
    }

    @Transactional
    public Automovil crear(UUID personaId, AutomovilRequestDTO request) {
        Persona persona = personaRepository.findById(personaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.PERSONA_NO_ENCONTRADA,
                        "No existe una persona con el identificador '" + personaId + "'"));
        if (!persona.isActivo()) {
            throw new PersonaEliminadaException(
                    "La persona con el identificador '" + personaId + "' está eliminada lógicamente");
        }

        validarAnio(request.anio());

        if (automovilRepository.existsByPlacasAndActivoTrue(request.placas())) {
            throw new DuplicateFieldException(ErrorCode.AUTOMOVIL_PLACAS_DUPLICADAS, "placas",
                    "Ya existe un automóvil activo con estas placas",
                    "Debe ser único entre automóviles activos");
        }
        if (request.vin() != null && automovilRepository.existsByVin(request.vin())) {
            throw new DuplicateFieldException(ErrorCode.AUTOMOVIL_VIN_DUPLICADO, "vin",
                    "Ya existe un automóvil con este VIN", "El VIN es identidad única del vehículo");
        }

        Automovil automovil = automovilRepository.save(new Automovil(persona, request.marca(), request.modelo(),
                request.anio(), request.color(), request.placas(), request.vin()));

        registrarHistorial(persona, TipoOperacion.MODIFICACION,
                historialDiffService.serializarAltaAutomovil(automovil));
        return automovil;
    }

    public List<Automovil> listarPorPersona(UUID personaId) {
        if (!personaRepository.existsById(personaId)) {
            throw new RecursoNoEncontradoException(ErrorCode.PERSONA_NO_ENCONTRADA,
                    "No existe una persona con el identificador '" + personaId + "'");
        }
        return automovilRepository.findByPersonaId(personaId).stream().filter(Automovil::isActivo).toList();
    }

    public Automovil obtenerPorId(UUID automovilId) {
        return automovilRepository.findById(automovilId)
                .filter(Automovil::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.AUTOMOVIL_NO_ENCONTRADO,
                        "No existe un automóvil activo con el identificador '" + automovilId + "'"));
    }

    @Transactional
    public Automovil editar(UUID automovilId, AutomovilUpdateDTO request) {
        Automovil automovil = obtenerActivoOFallar(automovilId);
        var antes = HistorialDiffService.AutomovilSnapshot.de(automovil);

        String marca = request.marca() != null ? request.marca() : automovil.getMarca();
        String modelo = request.modelo() != null ? request.modelo() : automovil.getModelo();
        Short anio = request.anio() != null ? request.anio() : automovil.getAnio();
        String color = request.color() != null ? request.color() : automovil.getColor();
        String placas = request.placas() != null ? request.placas() : automovil.getPlacas();

        if (request.anio() != null) {
            validarAnio(anio);
        }
        if (request.placas() != null && !request.placas().equals(automovil.getPlacas())
                && automovilRepository.existsByPlacasAndActivoTrueAndIdNot(placas, automovilId)) {
            throw new DuplicateFieldException(ErrorCode.AUTOMOVIL_PLACAS_DUPLICADAS, "placas",
                    "Ya existe un automóvil activo con estas placas",
                    "Debe ser único entre automóviles activos");
        }

        automovil.editar(marca, modelo, anio, color, placas);

        historialDiffService.serializarEdicionAutomovil(antes, automovil)
                .ifPresent(cambios -> registrarHistorial(automovil.getPersona(), TipoOperacion.MODIFICACION,
                        cambios));
        return automovil;
    }

    /** Solo ADMIN. FR-009/FR-010: oculta también su historial de mantenimientos (por query, sin tocarlos). */
    @Transactional
    public void eliminar(UUID automovilId) {
        Automovil automovil = obtenerActivoOFallar(automovilId);
        automovil.desactivar();
        registrarHistorial(automovil.getPersona(), TipoOperacion.MODIFICACION,
                historialDiffService.serializarBajaAutomovil(automovil));
    }

    /** Solo ADMIN. FR-011. */
    @Transactional
    public Automovil restaurar(UUID automovilId) {
        Automovil automovil = automovilRepository.findById(automovilId)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.AUTOMOVIL_NO_ENCONTRADO,
                        "No existe un automóvil con el identificador '" + automovilId + "'"));
        if (automovil.isActivo()) {
            throw new AutomovilYaActivoException(
                    "El automóvil con el identificador '" + automovilId + "' ya está activo");
        }
        automovil.reactivar();
        registrarHistorial(automovil.getPersona(), TipoOperacion.MODIFICACION,
                historialDiffService.serializarRestauracionAutomovil(automovil));
        return automovil;
    }

    private Automovil obtenerActivoOFallar(UUID automovilId) {
        Automovil automovil = automovilRepository.findById(automovilId)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.AUTOMOVIL_NO_ENCONTRADO,
                        "No existe un automóvil con el identificador '" + automovilId + "'"));
        if (!automovil.isActivo()) {
            throw new AutomovilEliminadoException(
                    "El automóvil con el identificador '" + automovilId + "' está eliminado lógicamente");
        }
        return automovil;
    }

    private void validarAnio(Short anio) {
        short anioMaximo = (short) (Year.now().getValue() + 1);
        if (anio < ANIO_MINIMO || anio > anioMaximo) {
            throw new FormatoInvalidoException(ErrorCode.VALIDACION_FALLIDA, "anio",
                    "El año debe estar entre " + ANIO_MINIMO + " y " + anioMaximo);
        }
    }

    private void registrarHistorial(Persona persona, TipoOperacion operacion, String cambiosJson) {
        UUID usuarioId = securityAuditorAware.getCurrentAuditor()
                .orElseThrow(() -> new IllegalStateException(
                        "No hay un usuario autenticado para registrar el historial"));
        personaHistorialRepository.save(new PersonaHistorial(persona, usuarioId, operacion, cambiosJson));
    }
}
