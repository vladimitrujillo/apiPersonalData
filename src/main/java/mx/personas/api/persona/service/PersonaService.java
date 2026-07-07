package mx.personas.api.persona.service;

import mx.personas.api.common.audit.SecurityAuditorAware;
import mx.personas.api.common.error.DuplicateFieldException;
import mx.personas.api.common.error.ErrorCode;
import mx.personas.api.common.error.FormatoInvalidoException;
import mx.personas.api.common.error.PersonaYaActivaException;
import mx.personas.api.common.error.RecursoNoEncontradoException;
import mx.personas.api.persona.dto.CampoCambiadoDTO;
import mx.personas.api.persona.dto.DireccionDTO;
import mx.personas.api.persona.dto.DireccionUpdateDTO;
import mx.personas.api.persona.dto.HistorialEntradaDTO;
import mx.personas.api.persona.dto.HistorialPageResponseDTO;
import mx.personas.api.persona.dto.PersonaBusquedaFiltroDTO;
import mx.personas.api.persona.dto.PersonaEliminadaPageResponseDTO;
import mx.personas.api.persona.dto.PersonaPageResponseDTO;
import mx.personas.api.persona.dto.PersonaRequestDTO;
import mx.personas.api.persona.dto.PersonaResponseDTO;
import mx.personas.api.persona.dto.PersonaUpdateDTO;
import mx.personas.api.persona.mapper.PersonaMapper;
import mx.personas.api.persona.model.Direccion;
import mx.personas.api.persona.model.Persona;
import mx.personas.api.persona.model.PersonaHistorial;
import mx.personas.api.persona.model.PersonaHistorial.TipoOperacion;
import mx.personas.api.persona.repository.DireccionRepository;
import mx.personas.api.persona.repository.PersonaHistorialRepository;
import mx.personas.api.persona.repository.PersonaRepository;
import mx.personas.api.persona.repository.PersonaSpecifications;
import mx.personas.api.usuario.model.Usuario;
import mx.personas.api.usuario.repository.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class PersonaService {

    private final PersonaRepository personaRepository;
    private final DireccionRepository direccionRepository;
    private final PersonaMapper personaMapper;
    private final DireccionValidationService direccionValidationService;
    private final PersonaHistorialRepository personaHistorialRepository;
    private final HistorialDiffService historialDiffService;
    private final SecurityAuditorAware securityAuditorAware;
    private final UsuarioRepository usuarioRepository;

    public PersonaService(PersonaRepository personaRepository, DireccionRepository direccionRepository,
                           PersonaMapper personaMapper, DireccionValidationService direccionValidationService,
                           PersonaHistorialRepository personaHistorialRepository,
                           HistorialDiffService historialDiffService, SecurityAuditorAware securityAuditorAware,
                           UsuarioRepository usuarioRepository) {
        this.personaRepository = personaRepository;
        this.direccionRepository = direccionRepository;
        this.personaMapper = personaMapper;
        this.direccionValidationService = direccionValidationService;
        this.personaHistorialRepository = personaHistorialRepository;
        this.historialDiffService = historialDiffService;
        this.securityAuditorAware = securityAuditorAware;
        this.usuarioRepository = usuarioRepository;
    }

    public PersonaResponseDTO crear(PersonaRequestDTO dto) {
        validarFechaNacimiento(dto.fechaNacimiento());
        validarCorreoDisponible(dto.correo(), null);
        validarCurpDisponible(dto.curp(), null);

        Persona persona = personaMapper.toEntity(dto);
        personaRepository.save(persona);

        Direccion direccion = crearDireccion(persona, dto.direccion());
        direccionRepository.save(direccion);

        registrarHistorial(persona, TipoOperacion.CREACION,
                historialDiffService.serializarCreacion(persona, direccion));

        return personaMapper.toResponseDTO(persona, direccion);
    }

    @Transactional(readOnly = true)
    public PersonaResponseDTO obtenerPorId(UUID id) {
        Persona persona = obtenerActivaOFallar(id);
        Direccion direccion = obtenerDireccionVigente(persona);
        return personaMapper.toResponseDTO(persona, direccion);
    }

    private static final Set<String> CAMPOS_ORDEN_VALIDOS = Set.of("NOMBRE", "FECHA_NACIMIENTO", "FECHA_REGISTRO");
    private static final Set<String> DIRECCIONES_ORDEN_VALIDAS = Set.of("ASC", "DESC");

    /**
     * Busqueda avanzada combinable (FR-001 a FR-011). {@code estadoRegistroEfectivo} ya
     * debe venir resuelto por el llamador segun el rol (FR-007/FR-008): el servicio no
     * conoce roles, solo aplica el valor que se le pasa (research.md §3).
     */
    @Transactional(readOnly = true)
    public PersonaPageResponseDTO listar(PersonaBusquedaFiltroDTO filtro, String estadoRegistroEfectivo,
                                          Pageable pageable) {
        validarRangoEdad(filtro.edadMinima(), filtro.edadMaxima());
        validarRangoFechas(filtro.fechaRegistroDesde(), filtro.fechaRegistroHasta());
        validarOrden(filtro.ordenarPor(), filtro.direccionOrden());

        LocalDate fechaNacimientoDesde = fechaNacimientoMinimaDesdeEdadMaxima(filtro.edadMaxima());
        LocalDate fechaNacimientoHasta = fechaNacimientoMaximaDesdeEdadMinima(filtro.edadMinima());

        Specification<Persona> spec = Specification.allOf(
                PersonaSpecifications.conEstadoRegistro(estadoRegistroEfectivo),
                PersonaSpecifications.conNombreParcial(normalizar(filtro.nombre())),
                PersonaSpecifications.conMunicipio(normalizar(filtro.municipio())),
                PersonaSpecifications.conEstadoGeografico(normalizar(filtro.estado())),
                PersonaSpecifications.conCurpPrefijo(normalizar(filtro.curpPrefijo())),
                PersonaSpecifications.conFechaNacimientoEntre(fechaNacimientoDesde, fechaNacimientoHasta),
                PersonaSpecifications.conFechaRegistroEntre(filtro.fechaRegistroDesde(), filtro.fechaRegistroHasta()),
                PersonaSpecifications.conSexo(normalizar(filtro.sexo())));

        Pageable paginaConOrden = aplicarOrden(filtro.ordenarPor(), filtro.direccionOrden(), pageable);
        Page<Persona> pagina = personaRepository.findAll(spec, paginaConOrden);

        var contenido = pagina.getContent().stream()
                .map(persona -> personaMapper.toResumenDTO(persona, obtenerDireccionVigente(persona)))
                .toList();

        return new PersonaPageResponseDTO(
                contenido, pagina.getNumber(), pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages());
    }

    /** edadMaxima=N -> nacido en o despues de (hoy - (N+1) años + 1 dia) - research.md §5. */
    private LocalDate fechaNacimientoMinimaDesdeEdadMaxima(Integer edadMaxima) {
        return edadMaxima == null ? null : LocalDate.now().minusYears(edadMaxima + 1L).plusDays(1);
    }

    /** edadMinima=N -> nacido en o antes de (hoy - N años) - research.md §5. */
    private LocalDate fechaNacimientoMaximaDesdeEdadMinima(Integer edadMinima) {
        return edadMinima == null ? null : LocalDate.now().minusYears(edadMinima);
    }

    private void validarRangoEdad(Integer edadMinima, Integer edadMaxima) {
        if (edadMinima != null && edadMaxima != null && edadMinima > edadMaxima) {
            throw new FormatoInvalidoException(ErrorCode.VALIDACION_FALLIDA, "edadMaxima",
                    "edadMaxima no puede ser menor que edadMinima");
        }
    }

    private void validarRangoFechas(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new FormatoInvalidoException(ErrorCode.VALIDACION_FALLIDA, "fechaRegistroHasta",
                    "fechaRegistroHasta no puede ser anterior a fechaRegistroDesde");
        }
    }

    private void validarOrden(String ordenarPor, String direccionOrden) {
        if (ordenarPor != null && !CAMPOS_ORDEN_VALIDOS.contains(ordenarPor.toUpperCase())) {
            throw new FormatoInvalidoException(ErrorCode.VALIDACION_FALLIDA, "ordenarPor",
                    "Debe ser uno de: " + CAMPOS_ORDEN_VALIDOS);
        }
        if (direccionOrden != null && !DIRECCIONES_ORDEN_VALIDAS.contains(direccionOrden.toUpperCase())) {
            throw new FormatoInvalidoException(ErrorCode.VALIDACION_FALLIDA, "direccionOrden",
                    "Debe ser uno de: " + DIRECCIONES_ORDEN_VALIDAS);
        }
    }

    /** Sin ordenarPor, no se agrega ningun Sort - identico al comportamiento actual (FR-011). */
    private Pageable aplicarOrden(String ordenarPor, String direccionOrden, Pageable pageable) {
        if (ordenarPor == null) {
            return pageable;
        }
        Sort.Direction direccion = "DESC".equalsIgnoreCase(direccionOrden) ? Sort.Direction.DESC : Sort.Direction.ASC;
        List<String> propiedades = switch (ordenarPor.toUpperCase()) {
            case "NOMBRE" -> List.of("apellidos", "nombres");
            case "FECHA_NACIMIENTO" -> List.of("fechaNacimiento");
            default -> List.of("createdAt");
        };
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(direccion, propiedades.toArray(new String[0])));
    }

    @Transactional(readOnly = true)
    public PersonaEliminadaPageResponseDTO listarEliminadas(Pageable pageable) {
        Page<Persona> pagina = personaRepository.findByActivoFalse(pageable);

        var contenido = pagina.getContent().stream()
                .map(persona -> personaMapper.toResponseDTO(persona, obtenerDireccionVigente(persona)))
                .toList();

        return new PersonaEliminadaPageResponseDTO(
                contenido, pagina.getNumber(), pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages());
    }

    private String normalizar(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor;
    }

    public PersonaResponseDTO actualizar(UUID id, PersonaUpdateDTO dto) {
        Persona persona = obtenerActivaOFallar(id);
        Direccion direccionVigente = obtenerDireccionVigente(persona);

        HistorialDiffService.PersonaSnapshot antesPersona = HistorialDiffService.PersonaSnapshot.de(persona);
        HistorialDiffService.DireccionSnapshot antesDireccion = HistorialDiffService.DireccionSnapshot.de(direccionVigente);

        if (dto.fechaNacimiento() != null) {
            validarFechaNacimiento(dto.fechaNacimiento());
            persona.setFechaNacimiento(dto.fechaNacimiento());
        }
        if (dto.nombres() != null) {
            persona.setNombres(dto.nombres());
        }
        if (dto.apellidos() != null) {
            persona.setApellidos(dto.apellidos());
        }
        if (dto.sexo() != null) {
            persona.setSexo(dto.sexo());
        }
        if (dto.curp() != null && !dto.curp().equals(persona.getCurp())) {
            validarCurpDisponible(dto.curp(), id);
            persona.setCurp(dto.curp());
        }
        if (dto.correo() != null && !dto.correo().equals(persona.getCorreo())) {
            validarCorreoDisponible(dto.correo(), id);
            persona.setCorreo(dto.correo());
        }
        if (dto.telefono() != null) {
            persona.setTelefono(dto.telefono());
        }

        if (dto.direccion() != null) {
            actualizarDireccion(direccionVigente, dto.direccion());
            direccionRepository.save(direccionVigente);
        }

        historialDiffService.serializarModificacion(antesPersona, persona, antesDireccion, direccionVigente)
                .ifPresent(cambios -> registrarHistorial(persona, TipoOperacion.MODIFICACION, cambios));

        Direccion direccion = obtenerDireccionVigente(persona);
        return personaMapper.toResponseDTO(persona, direccion);
    }

    public void eliminar(UUID id) {
        Persona persona = obtenerActivaOFallar(id);
        persona.eliminarLogicamente();
        registrarHistorial(persona, TipoOperacion.ELIMINACION,
                historialDiffService.serializarCambioEstadoActivo(true, false));
    }

    public PersonaResponseDTO restaurar(UUID id) {
        Persona persona = obtenerCualquieraOFallar(id);
        if (persona.isActivo()) {
            throw new PersonaYaActivaException(
                    "La persona con el identificador '" + id + "' ya está activa");
        }

        // La CURP nunca puede conflictuar al restaurar (FR-010): su unicidad global
        // (V4__globalizar_unicidad_curp.sql) hace imposible que otro registro la haya
        // tomado mientras esta estaba eliminada. Solo el correo puede conflictuar.
        personaRepository.findByCorreoAndActivoTrue(persona.getCorreo()).ifPresent(activaConMismoCorreo -> {
            throw new DuplicateFieldException(ErrorCode.PERSONA_CORREO_DUPLICADO, "correo",
                    "Ya existe una persona activa registrada con este correo electrónico",
                    "En uso por la persona activa con id " + activaConMismoCorreo.getId());
        });

        persona.restaurar();
        registrarHistorial(persona, TipoOperacion.RESTAURACION,
                historialDiffService.serializarCambioEstadoActivo(false, true));

        Direccion direccion = obtenerDireccionVigente(persona);
        return personaMapper.toResponseDTO(persona, direccion);
    }

    @Transactional(readOnly = true)
    public HistorialPageResponseDTO historial(UUID id, Pageable pageable) {
        Persona persona = obtenerCualquieraOFallar(id);
        Page<PersonaHistorial> pagina = personaHistorialRepository.findByPersonaOrderByFechaDesc(persona, pageable);

        var contenido = pagina.getContent().stream()
                .map(this::toHistorialEntradaDTO)
                .toList();

        return new HistorialPageResponseDTO(
                contenido, pagina.getNumber(), pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages());
    }

    private HistorialEntradaDTO toHistorialEntradaDTO(PersonaHistorial entrada) {
        String login = usuarioRepository.findById(entrada.getUsuarioId()).map(Usuario::getLogin).orElse(null);
        return new HistorialEntradaDTO(entrada.getFecha(), login, entrada.getOperacion().name(),
                historialDiffService.deserializar(entrada.getCambios()));
    }

    private void registrarHistorial(Persona persona, TipoOperacion operacion, String cambiosJson) {
        UUID usuarioId = securityAuditorAware.getCurrentAuditor()
                .orElseThrow(() -> new IllegalStateException(
                        "No hay un usuario autenticado para registrar el historial"));
        personaHistorialRepository.save(new PersonaHistorial(persona, usuarioId, operacion, cambiosJson));
    }

    private Persona obtenerActivaOFallar(UUID id) {
        return personaRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.PERSONA_NO_ENCONTRADA,
                        "No existe una persona activa con el identificador '" + id + "'"));
    }

    private Persona obtenerCualquieraOFallar(UUID id) {
        return personaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.PERSONA_NO_ENCONTRADA,
                        "No existe una persona con el identificador '" + id + "'"));
    }

    private Direccion obtenerDireccionVigente(Persona persona) {
        return direccionRepository.findFirstByPersonaOrderByUpdatedAtDesc(persona)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.PERSONA_NO_ENCONTRADA,
                        "La persona no tiene una dirección registrada"));
    }

    private Direccion crearDireccion(Persona persona, DireccionDTO dto) {
        DireccionValidada validada = direccionValidationService.validarYCompletar(
                dto.colonia(), dto.municipio(), dto.estado(), dto.codigoPostal(), dto.pais());
        return new Direccion(persona, dto.calle(), dto.numero(), validada.colonia(), validada.municipio(),
                validada.estado(), validada.codigoPostal(), validada.pais(), validada.cpCatalogoId());
    }

    private void actualizarDireccion(Direccion direccion, DireccionUpdateDTO dto) {
        String colonia = dto.colonia() != null ? dto.colonia() : direccion.getColonia();
        String municipio = dto.municipio() != null ? dto.municipio() : direccion.getMunicipio();
        String estado = dto.estado() != null ? dto.estado() : direccion.getEstado();
        String codigoPostal = dto.codigoPostal() != null ? dto.codigoPostal() : direccion.getCodigoPostal();
        String pais = dto.pais() != null ? dto.pais() : direccion.getPais();

        DireccionValidada validada = direccionValidationService.validarYCompletar(
                colonia, municipio, estado, codigoPostal, pais);

        direccion.actualizar(
                dto.calle() != null ? dto.calle() : direccion.getCalle(),
                dto.numero() != null ? dto.numero() : direccion.getNumero(),
                validada.colonia(), validada.municipio(), validada.estado(), validada.codigoPostal(),
                validada.pais(), validada.cpCatalogoId());
    }

    private void validarFechaNacimiento(LocalDate fechaNacimiento) {
        if (fechaNacimiento.isAfter(LocalDate.now())) {
            throw new FormatoInvalidoException(ErrorCode.FECHA_NACIMIENTO_FUTURA, "fechaNacimiento",
                    "La fecha de nacimiento no puede ser posterior a la fecha actual");
        }
    }

    /** Correo: unico solo entre activos (D3, sin cambio). idAExcluir es null al crear. */
    private void validarCorreoDisponible(String correo, UUID idAExcluir) {
        boolean enConflicto = idAExcluir == null
                ? personaRepository.existsByCorreoAndActivoTrue(correo)
                : personaRepository.existsByCorreoAndActivoTrueAndIdNot(correo, idAExcluir);
        if (enConflicto) {
            throw new DuplicateFieldException(ErrorCode.PERSONA_CORREO_DUPLICADO, "correo",
                    "Ya existe una persona activa registrada con este correo electrónico",
                    "Debe ser único entre personas activas");
        }
    }

    /**
     * CURP: unica de forma global y permanente (D2). Distingue si el registro en
     * conflicto esta activo (409 sin cambios) o eliminado logicamente (409 accionable,
     * con el id de ese registro, sin ningun otro dato personal - FR-002 a FR-004).
     */
    private void validarCurpDisponible(String curp, UUID idAExcluir) {
        personaRepository.findByCurp(curp)
                .filter(existente -> !Objects.equals(existente.getId(), idAExcluir))
                .ifPresent(existente -> {
                    if (existente.isActivo()) {
                        throw new DuplicateFieldException(ErrorCode.PERSONA_CURP_DUPLICADO, "curp",
                                "Ya existe una persona activa registrada con este CURP",
                                "Debe ser único entre personas activas");
                    }
                    throw new DuplicateFieldException(ErrorCode.PERSONA_CURP_ELIMINADA, "curp",
                            "Existe un registro eliminado con este CURP; un ADMIN puede restaurarlo",
                            "Registro eliminado con id " + existente.getId());
                });
    }
}
