package mx.personas.api.persona.service;

import mx.personas.api.common.error.DuplicateFieldException;
import mx.personas.api.common.error.ErrorCode;
import mx.personas.api.common.error.FormatoInvalidoException;
import mx.personas.api.common.error.RecursoNoEncontradoException;
import mx.personas.api.persona.dto.DireccionDTO;
import mx.personas.api.persona.dto.DireccionUpdateDTO;
import mx.personas.api.persona.dto.PersonaPageResponseDTO;
import mx.personas.api.persona.dto.PersonaRequestDTO;
import mx.personas.api.persona.dto.PersonaResponseDTO;
import mx.personas.api.persona.dto.PersonaUpdateDTO;
import mx.personas.api.persona.mapper.PersonaMapper;
import mx.personas.api.persona.model.Direccion;
import mx.personas.api.persona.model.Persona;
import mx.personas.api.persona.repository.DireccionRepository;
import mx.personas.api.persona.repository.PersonaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@Transactional
public class PersonaService {

    private final PersonaRepository personaRepository;
    private final DireccionRepository direccionRepository;
    private final PersonaMapper personaMapper;
    private final DireccionValidationService direccionValidationService;

    public PersonaService(PersonaRepository personaRepository, DireccionRepository direccionRepository,
                           PersonaMapper personaMapper, DireccionValidationService direccionValidationService) {
        this.personaRepository = personaRepository;
        this.direccionRepository = direccionRepository;
        this.personaMapper = personaMapper;
        this.direccionValidationService = direccionValidationService;
    }

    public PersonaResponseDTO crear(PersonaRequestDTO dto) {
        validarFechaNacimiento(dto.fechaNacimiento());
        validarNoDuplicado(dto.correo(), dto.curp());

        Persona persona = personaMapper.toEntity(dto);
        personaRepository.save(persona);

        Direccion direccion = crearDireccion(persona, dto.direccion());
        direccionRepository.save(direccion);

        return personaMapper.toResponseDTO(persona, direccion);
    }

    @Transactional(readOnly = true)
    public PersonaResponseDTO obtenerPorId(UUID id) {
        Persona persona = obtenerActivaOFallar(id);
        Direccion direccion = obtenerDireccionVigente(persona);
        return personaMapper.toResponseDTO(persona, direccion);
    }

    @Transactional(readOnly = true)
    public PersonaPageResponseDTO listar(String nombre, String municipio, String estado, Pageable pageable) {
        Page<Persona> pagina = personaRepository.buscarActivas(
                normalizar(nombre), normalizar(municipio), normalizar(estado), pageable);

        var contenido = pagina.getContent().stream()
                .map(persona -> personaMapper.toResponseDTO(persona, obtenerDireccionVigente(persona)))
                .toList();

        return new PersonaPageResponseDTO(
                contenido, pagina.getNumber(), pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages());
    }

    private String normalizar(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor;
    }

    public PersonaResponseDTO actualizar(UUID id, PersonaUpdateDTO dto) {
        Persona persona = obtenerActivaOFallar(id);

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
            if (personaRepository.existsByCurpAndActivoTrueAndIdNot(dto.curp(), id)) {
                throw new DuplicateFieldException(ErrorCode.PERSONA_CURP_DUPLICADO, "curp",
                        "Ya existe una persona activa registrada con este CURP",
                        "Debe ser único entre personas activas");
            }
            persona.setCurp(dto.curp());
        }
        if (dto.correo() != null && !dto.correo().equals(persona.getCorreo())) {
            if (personaRepository.existsByCorreoAndActivoTrueAndIdNot(dto.correo(), id)) {
                throw new DuplicateFieldException(ErrorCode.PERSONA_CORREO_DUPLICADO, "correo",
                        "Ya existe una persona activa registrada con este correo electrónico",
                        "Debe ser único entre personas activas");
            }
            persona.setCorreo(dto.correo());
        }
        if (dto.telefono() != null) {
            persona.setTelefono(dto.telefono());
        }
        persona.marcarActualizada();

        if (dto.direccion() != null) {
            Direccion direccionVigente = obtenerDireccionVigente(persona);
            actualizarDireccion(direccionVigente, dto.direccion());
            direccionRepository.save(direccionVigente);
        }

        Direccion direccion = obtenerDireccionVigente(persona);
        return personaMapper.toResponseDTO(persona, direccion);
    }

    public void eliminar(UUID id) {
        Persona persona = obtenerActivaOFallar(id);
        persona.eliminarLogicamente();
    }

    private Persona obtenerActivaOFallar(UUID id) {
        return personaRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.PERSONA_NO_ENCONTRADA,
                        "No existe una persona activa con el identificador '" + id + "'"));
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

    private void validarNoDuplicado(String correo, String curp) {
        if (personaRepository.existsByCorreoAndActivoTrue(correo)) {
            throw new DuplicateFieldException(ErrorCode.PERSONA_CORREO_DUPLICADO, "correo",
                    "Ya existe una persona activa registrada con este correo electrónico",
                    "Debe ser único entre personas activas");
        }
        if (personaRepository.existsByCurpAndActivoTrue(curp)) {
            throw new DuplicateFieldException(ErrorCode.PERSONA_CURP_DUPLICADO, "curp",
                    "Ya existe una persona activa registrada con este CURP",
                    "Debe ser único entre personas activas");
        }
    }
}
