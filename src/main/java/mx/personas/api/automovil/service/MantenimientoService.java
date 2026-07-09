package mx.personas.api.automovil.service;

import mx.personas.api.automovil.dto.MantenimientoRequestDTO;
import mx.personas.api.automovil.dto.MantenimientoUpdateDTO;
import mx.personas.api.automovil.dto.PiezaCambiadaDTO;
import mx.personas.api.automovil.model.Automovil;
import mx.personas.api.automovil.model.Mantenimiento;
import mx.personas.api.automovil.model.PiezaCambiada;
import mx.personas.api.automovil.repository.AutomovilRepository;
import mx.personas.api.automovil.repository.MantenimientoRepository;
import mx.personas.api.common.audit.SecurityAuditorAware;
import mx.personas.api.common.error.AutomovilEliminadoException;
import mx.personas.api.common.error.ErrorCode;
import mx.personas.api.common.error.KilometrajeInconsistenteException;
import mx.personas.api.common.error.MecanicoEliminadoException;
import mx.personas.api.common.error.MecanicoNoEncontradoException;
import mx.personas.api.common.error.MantenimientoYaActivoException;
import mx.personas.api.common.error.MecanicoSinProfesionActivaException;
import mx.personas.api.common.error.PersonaEliminadaException;
import mx.personas.api.common.error.RecursoNoEncontradoException;
import mx.personas.api.persona.model.Persona;
import mx.personas.api.persona.model.PersonaHistorial;
import mx.personas.api.persona.model.PersonaHistorial.TipoOperacion;
import mx.personas.api.persona.repository.PersonaHistorialRepository;
import mx.personas.api.persona.repository.PersonaRepository;
import mx.personas.api.persona.service.HistorialDiffService;
import mx.personas.api.profesion.repository.PersonaProfesionRepository;
import mx.personas.api.profesion.repository.ProfesionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Registrar/consultar/editar/eliminar/restaurar mantenimientos de un automóvil (US3, US4, US6).
 */
@Service
public class MantenimientoService {

    private static final String NOMBRE_PROFESION_MECANICO = "Mecánico";

    private final AutomovilRepository automovilRepository;
    private final MantenimientoRepository mantenimientoRepository;
    private final PersonaRepository personaRepository;
    private final ProfesionRepository profesionRepository;
    private final PersonaProfesionRepository personaProfesionRepository;
    private final PersonaHistorialRepository personaHistorialRepository;
    private final HistorialDiffService historialDiffService;
    private final SecurityAuditorAware securityAuditorAware;

    public MantenimientoService(AutomovilRepository automovilRepository,
                                 MantenimientoRepository mantenimientoRepository,
                                 PersonaRepository personaRepository,
                                 ProfesionRepository profesionRepository,
                                 PersonaProfesionRepository personaProfesionRepository,
                                 PersonaHistorialRepository personaHistorialRepository,
                                 HistorialDiffService historialDiffService,
                                 SecurityAuditorAware securityAuditorAware) {
        this.automovilRepository = automovilRepository;
        this.mantenimientoRepository = mantenimientoRepository;
        this.personaRepository = personaRepository;
        this.profesionRepository = profesionRepository;
        this.personaProfesionRepository = personaProfesionRepository;
        this.personaHistorialRepository = personaHistorialRepository;
        this.historialDiffService = historialDiffService;
        this.securityAuditorAware = securityAuditorAware;
    }

    @Transactional
    public Mantenimiento registrar(UUID automovilId, MantenimientoRequestDTO request) {
        Automovil automovil = obtenerAutomovilActivoOFallar(automovilId);
        Persona duenio = automovil.getPersona();
        if (!duenio.isActivo()) {
            throw new PersonaEliminadaException(
                    "La persona dueña del automóvil '" + automovilId + "' está eliminada lógicamente");
        }

        validarFechaCostosKilometraje(request.fecha(), request.costoTotal(), request.kilometraje());
        validarConsistenciaKilometraje(automovilId, request.kilometraje(), null);
        if (request.mecanicoId() != null) {
            validarMecanico(request.mecanicoId());
        }

        Mantenimiento mantenimiento = new Mantenimiento(automovil, request.descripcion(), request.fecha(),
                request.kilometraje(), request.mecanicoId(), request.costoTotal());
        mantenimiento.actualizarPiezas(construirPiezas(mantenimiento, request.piezas()));

        Mantenimiento guardado = mantenimientoRepository.save(mantenimiento);
        registrarHistorial(duenio, historialDiffService.serializarRegistroMantenimiento(guardado));
        return guardado;
    }

    /**
     * FR-022: historial paginado, más reciente primero. FR-010: un automóvil eliminado
     * lógicamente responde 404 aquí (igual que {@code AutomovilService.obtenerPorId}), no 409 —
     * a diferencia de {@code registrar}, esto es una consulta, no una escritura sobre el recurso.
     */
    public Page<Mantenimiento> listarHistorial(UUID automovilId, Pageable pageable) {
        boolean automovilActivo = automovilRepository.findById(automovilId)
                .map(Automovil::isActivo)
                .orElse(false);
        if (!automovilActivo) {
            throw new RecursoNoEncontradoException(ErrorCode.AUTOMOVIL_NO_ENCONTRADO,
                    "No existe un automóvil activo con el identificador '" + automovilId + "'");
        }
        return mantenimientoRepository.findByAutomovilIdAndActivoTrueOrderByFechaDescCreatedAtDesc(automovilId,
                pageable);
    }

    /**
     * FR-023. Hallazgo F1 CRITICAL de /speckit-analyze: FR-010 exige ocultar el historial de un
     * automóvil eliminado de "cualquier consulta", no solo del listado paginado — por eso aquí se
     * valida tanto el activo del propio mantenimiento como el de su automóvil.
     */
    public Mantenimiento obtenerPorId(UUID mantenimientoId) {
        Mantenimiento mantenimiento = mantenimientoRepository.findById(mantenimientoId)
                .filter(Mantenimiento::isActivo)
                .filter(m -> m.getAutomovil().isActivo())
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.MANTENIMIENTO_NO_ENCONTRADO,
                        "No existe un mantenimiento activo con el identificador '" + mantenimientoId + "'"));
        return mantenimiento;
    }

    /**
     * FR-024, resuelto explícitamente tras hallazgo F3 de /speckit-analyze: editar también rechaza
     * si el automóvil o su persona dueña están eliminados lógicamente (mismo criterio que registrar).
     */
    @Transactional
    public Mantenimiento editar(UUID mantenimientoId, MantenimientoUpdateDTO request) {
        Mantenimiento mantenimiento = mantenimientoRepository.findById(mantenimientoId)
                .filter(Mantenimiento::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.MANTENIMIENTO_NO_ENCONTRADO,
                        "No existe un mantenimiento activo con el identificador '" + mantenimientoId + "'"));
        Automovil automovil = mantenimiento.getAutomovil();
        if (!automovil.isActivo()) {
            throw new AutomovilEliminadoException(
                    "El automóvil del mantenimiento '" + mantenimientoId + "' está eliminado lógicamente");
        }
        Persona duenio = automovil.getPersona();
        if (!duenio.isActivo()) {
            throw new PersonaEliminadaException(
                    "La persona dueña del automóvil del mantenimiento '" + mantenimientoId
                            + "' está eliminada lógicamente");
        }

        var antes = HistorialDiffService.MantenimientoSnapshot.de(mantenimiento);

        String descripcion = request.descripcion() != null ? request.descripcion() : mantenimiento.getDescripcion();
        LocalDate fecha = request.fecha() != null ? request.fecha() : mantenimiento.getFecha();
        Integer kilometraje = request.kilometraje() != null ? request.kilometraje() : mantenimiento.getKilometraje();
        java.math.BigDecimal costoTotal = request.costoTotal() != null
                ? request.costoTotal() : mantenimiento.getCostoTotal();
        UUID mecanicoId = request.mecanicoId() != null ? request.mecanicoId() : mantenimiento.getMecanicoId();

        validarFechaCostosKilometraje(fecha, costoTotal, kilometraje);
        validarConsistenciaKilometraje(automovil.getId(), kilometraje, mantenimientoId);
        if (mecanicoId != null) {
            validarMecanico(mecanicoId);
        }

        mantenimiento.editar(descripcion, fecha, kilometraje, mecanicoId, costoTotal);
        if (request.piezas() != null) {
            mantenimiento.actualizarPiezas(construirPiezas(mantenimiento, request.piezas()));
        }

        historialDiffService.serializarEdicionMantenimiento(antes, mantenimiento)
                .ifPresent(cambios -> registrarHistorial(duenio, cambios));
        return mantenimiento;
    }

    /** Solo ADMIN (aplicado vía @PreAuthorize en el controller). */
    @Transactional
    public void eliminar(UUID mantenimientoId) {
        Mantenimiento mantenimiento = obtenerCualquieraOFallar(mantenimientoId);
        mantenimiento.desactivar();
        registrarHistorial(mantenimiento.getAutomovil().getPersona(),
                historialDiffService.serializarBajaMantenimiento(mantenimiento));
    }

    /** Solo ADMIN. FR-025a. */
    @Transactional
    public Mantenimiento restaurar(UUID mantenimientoId) {
        Mantenimiento mantenimiento = obtenerCualquieraOFallar(mantenimientoId);
        if (mantenimiento.isActivo()) {
            throw new MantenimientoYaActivoException(
                    "El mantenimiento con el identificador '" + mantenimientoId + "' ya está activo");
        }
        mantenimiento.reactivar();
        registrarHistorial(mantenimiento.getAutomovil().getPersona(),
                historialDiffService.serializarRestauracionMantenimiento(mantenimiento));
        return mantenimiento;
    }

    private Mantenimiento obtenerCualquieraOFallar(UUID mantenimientoId) {
        return mantenimientoRepository.findById(mantenimientoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.MANTENIMIENTO_NO_ENCONTRADO,
                        "No existe un mantenimiento con el identificador '" + mantenimientoId + "'"));
    }

    private List<PiezaCambiada> construirPiezas(Mantenimiento mantenimiento, List<PiezaCambiadaDTO> piezas) {
        return piezas.stream()
                .map(p -> new PiezaCambiada(mantenimiento, p.nombre(), p.numeroParte(), p.costo()))
                .toList();
    }

    private void validarFechaCostosKilometraje(LocalDate fecha, java.math.BigDecimal costoTotal, Integer kilometraje) {
        if (fecha.isAfter(LocalDate.now())) {
            throw new mx.personas.api.common.error.FormatoInvalidoException(ErrorCode.VALIDACION_FALLIDA, "fecha",
                    "La fecha del mantenimiento no puede ser futura");
        }
        if (costoTotal.signum() < 0) {
            throw new mx.personas.api.common.error.FormatoInvalidoException(ErrorCode.VALIDACION_FALLIDA,
                    "costoTotal", "El costo total no puede ser negativo");
        }
        if (kilometraje < 0) {
            throw new mx.personas.api.common.error.FormatoInvalidoException(ErrorCode.VALIDACION_FALLIDA,
                    "kilometraje", "El kilometraje no puede ser negativo");
        }
    }

    /** research.md #6: solo contra el mantenimiento activo con la fecha mas reciente, globalmente. */
    private void validarConsistenciaKilometraje(UUID automovilId, Integer kilometraje, UUID excluirMantenimientoId) {
        var masReciente = excluirMantenimientoId == null
                ? mantenimientoRepository.findFirstByAutomovilIdAndActivoTrueOrderByFechaDescCreatedAtDesc(
                        automovilId)
                : mantenimientoRepository
                        .findFirstByAutomovilIdAndActivoTrueAndIdNotOrderByFechaDescCreatedAtDesc(automovilId,
                                excluirMantenimientoId);

        masReciente.ifPresent(anterior -> {
            if (kilometraje < anterior.getKilometraje()) {
                throw new KilometrajeInconsistenteException(
                        "El kilometraje debe ser mayor o igual a " + anterior.getKilometraje()
                                + ", el registrado el " + anterior.getFecha());
            }
        });
    }

    /** research.md #2-3: compone los repositorios de 007 sin duplicar queries. */
    private void validarMecanico(UUID mecanicoId) {
        Persona mecanico = personaRepository.findById(mecanicoId)
                .orElseThrow(() -> new MecanicoNoEncontradoException(
                        "No existe una persona con el identificador '" + mecanicoId + "'"));
        if (!mecanico.isActivo()) {
            throw new MecanicoEliminadoException(
                    "La persona con el identificador '" + mecanicoId + "' está eliminada lógicamente");
        }
        Long profesionMecanicoId = profesionRepository.findByNombreNormalizado(NOMBRE_PROFESION_MECANICO)
                .orElseThrow(() -> new IllegalStateException(
                        "La profesión semilla 'Mecánico' no existe en el catálogo"))
                .getId();
        if (!personaProfesionRepository.existsByPersonaIdAndProfesionIdAndActivoTrue(mecanicoId,
                profesionMecanicoId)) {
            throw new MecanicoSinProfesionActivaException(
                    "La persona con el identificador '" + mecanicoId
                            + "' no está registrada como mecánico (sin la profesión activa)");
        }
    }

    private Automovil obtenerAutomovilActivoOFallar(UUID automovilId) {
        Automovil automovil = automovilRepository.findById(automovilId)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.AUTOMOVIL_NO_ENCONTRADO,
                        "No existe un automóvil con el identificador '" + automovilId + "'"));
        if (!automovil.isActivo()) {
            throw new AutomovilEliminadoException(
                    "El automóvil con el identificador '" + automovilId + "' está eliminado lógicamente");
        }
        return automovil;
    }

    private void registrarHistorial(Persona persona, String cambiosJson) {
        UUID usuarioId = securityAuditorAware.getCurrentAuditor()
                .orElseThrow(() -> new IllegalStateException(
                        "No hay un usuario autenticado para registrar el historial"));
        personaHistorialRepository.save(new PersonaHistorial(persona, usuarioId, TipoOperacion.MODIFICACION,
                cambiosJson));
    }
}
