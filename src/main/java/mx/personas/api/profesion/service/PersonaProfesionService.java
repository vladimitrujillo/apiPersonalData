package mx.personas.api.profesion.service;

import mx.personas.api.common.audit.SecurityAuditorAware;
import mx.personas.api.common.error.ErrorCode;
import mx.personas.api.common.error.PersonaEliminadaException;
import mx.personas.api.common.error.PersonaProfesionYaAsignadaException;
import mx.personas.api.common.error.PersonaProfesionYaRetiradaException;
import mx.personas.api.common.error.ProfesionDesactivadaException;
import mx.personas.api.common.error.RecursoNoEncontradoException;
import mx.personas.api.persona.model.Persona;
import mx.personas.api.persona.model.PersonaHistorial;
import mx.personas.api.persona.model.PersonaHistorial.TipoOperacion;
import mx.personas.api.persona.repository.PersonaHistorialRepository;
import mx.personas.api.persona.repository.PersonaRepository;
import mx.personas.api.persona.service.HistorialDiffService;
import mx.personas.api.profesion.dto.AsignacionProfesionRequestDTO;
import mx.personas.api.profesion.model.PersonaProfesion;
import mx.personas.api.profesion.model.Profesion;
import mx.personas.api.profesion.repository.PersonaProfesionRepository;
import mx.personas.api.profesion.repository.ProfesionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Asignar/retirar profesiones a personas y consultarlas (US2, US3, US5).
 * Vive en el dominio profesion por depender fuertemente del catálogo, pero se
 * inyecta desde PersonaController — igual que historial/restaurar.
 */
@Service
public class PersonaProfesionService {

    private final PersonaRepository personaRepository;
    private final ProfesionRepository profesionRepository;
    private final PersonaProfesionRepository personaProfesionRepository;
    private final PersonaHistorialRepository personaHistorialRepository;
    private final HistorialDiffService historialDiffService;
    private final SecurityAuditorAware securityAuditorAware;

    public PersonaProfesionService(PersonaRepository personaRepository, ProfesionRepository profesionRepository,
                                    PersonaProfesionRepository personaProfesionRepository,
                                    PersonaHistorialRepository personaHistorialRepository,
                                    HistorialDiffService historialDiffService,
                                    SecurityAuditorAware securityAuditorAware) {
        this.personaRepository = personaRepository;
        this.profesionRepository = profesionRepository;
        this.personaProfesionRepository = personaProfesionRepository;
        this.personaHistorialRepository = personaHistorialRepository;
        this.historialDiffService = historialDiffService;
        this.securityAuditorAware = securityAuditorAware;
    }

    @Transactional
    public PersonaProfesion asignar(UUID personaId, AsignacionProfesionRequestDTO request) {
        Persona persona = personaRepository.findById(personaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.PERSONA_NO_ENCONTRADA,
                        "No existe una persona con el identificador '" + personaId + "'"));
        if (!persona.isActivo()) {
            throw new PersonaEliminadaException(
                    "La persona con el identificador '" + personaId + "' está eliminada lógicamente");
        }

        Profesion profesion = profesionRepository.findById(request.profesionId())
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.PROFESION_NO_ENCONTRADA,
                        "No existe una profesión con el identificador '" + request.profesionId() + "'"));
        if (!profesion.isActivo()) {
            throw new ProfesionDesactivadaException(
                    "La profesión '" + profesion.getNombre() + "' está desactivada");
        }

        if (personaProfesionRepository.existsByPersonaIdAndProfesionIdAndActivoTrue(personaId,
                request.profesionId())) {
            throw new PersonaProfesionYaAsignadaException(
                    "La persona ya tiene la profesión '" + profesion.getNombre() + "' asignada de forma activa");
        }

        PersonaProfesion asignacion = personaProfesionRepository.save(
                new PersonaProfesion(persona, profesion, request.fechaDesde(), request.cedula()));

        registrarHistorial(persona, historialDiffService.serializarAsignacionProfesion(asignacion));
        return asignacion;
    }

    /**
     * Profesiones de una persona (US3, FR-016/FR-017). Por defecto solo activas;
     * {@code incluirRetiradas} solo tiene efecto si {@code esAdmin}.
     */
    public java.util.List<PersonaProfesion> listarPorPersona(UUID personaId, boolean incluirRetiradas,
                                                               boolean esAdmin) {
        if (!personaRepository.existsById(personaId)) {
            throw new RecursoNoEncontradoException(ErrorCode.PERSONA_NO_ENCONTRADA,
                    "No existe una persona con el identificador '" + personaId + "'");
        }
        if (incluirRetiradas && esAdmin) {
            return personaProfesionRepository.findByPersonaId(personaId);
        }
        return personaProfesionRepository.findByPersonaIdAndActivoTrue(personaId);
    }

    /**
     * Directorio de personas activas con una asignación activa de una
     * profesión (US4, FR-018/FR-019).
     */
    public org.springframework.data.domain.Page<PersonaProfesion> directorio(
            Long profesionId, org.springframework.data.domain.Pageable pageable) {
        if (!profesionRepository.existsById(profesionId)) {
            throw new RecursoNoEncontradoException(ErrorCode.PROFESION_NO_ENCONTRADA,
                    "No existe una profesión con el identificador '" + profesionId + "'");
        }
        return personaProfesionRepository.findDirectorioByProfesionId(profesionId, pageable);
    }

    /**
     * Retira (desactiva) una asignación activa; no la elimina (US5, FR-015).
     * Nunca crea una fila nueva ni reactiva una ya retirada — eso solo ocurre
     * al reasignar (research.md §4).
     */
    @Transactional
    public PersonaProfesion retirar(UUID personaId, UUID asignacionId) {
        PersonaProfesion asignacion = personaProfesionRepository.findByIdAndPersonaId(asignacionId, personaId)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.PERSONA_PROFESION_NO_ENCONTRADA,
                        "No existe una asignación con el identificador '" + asignacionId
                                + "' para la persona '" + personaId + "'"));
        if (!asignacion.isActivo()) {
            throw new PersonaProfesionYaRetiradaException(
                    "La asignación con el identificador '" + asignacionId + "' ya estaba retirada");
        }
        asignacion.retirar();
        registrarHistorial(asignacion.getPersona(), historialDiffService.serializarRetiroProfesion(asignacion));
        return asignacion;
    }

    private void registrarHistorial(Persona persona, String cambiosJson) {
        UUID usuarioId = securityAuditorAware.getCurrentAuditor()
                .orElseThrow(() -> new IllegalStateException(
                        "No hay un usuario autenticado para registrar el historial"));
        personaHistorialRepository.save(new PersonaHistorial(persona, usuarioId, TipoOperacion.MODIFICACION,
                cambiosJson));
    }
}
