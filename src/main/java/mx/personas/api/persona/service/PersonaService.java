package mx.personas.api.persona.service;

import mx.personas.api.common.error.DuplicateFieldException;
import mx.personas.api.common.error.ErrorCode;
import mx.personas.api.common.error.FormatoInvalidoException;
import mx.personas.api.common.error.RecursoNoEncontradoException;
import mx.personas.api.persona.dto.DireccionDTO;
import mx.personas.api.persona.dto.DireccionUpdateDTO;
import mx.personas.api.persona.dto.PersonaRequestDTO;
import mx.personas.api.persona.dto.PersonaResponseDTO;
import mx.personas.api.persona.dto.PersonaUpdateDTO;
import mx.personas.api.persona.mapper.PersonaMapper;
import mx.personas.api.persona.model.Direccion;
import mx.personas.api.persona.model.Persona;
import mx.personas.api.persona.repository.DireccionRepository;
import mx.personas.api.persona.repository.PersonaRepository;
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

    public PersonaService(PersonaRepository personaRepository, DireccionRepository direccionRepository,
                           PersonaMapper personaMapper) {
        this.personaRepository = personaRepository;
        this.direccionRepository = direccionRepository;
        this.personaMapper = personaMapper;
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
        return new Direccion(persona, dto.calle(), dto.numero(), dto.colonia(),
                valorOVacio(dto.municipio()), valorOVacio(dto.estado()), dto.codigoPostal(), dto.pais(), null);
    }

    private void actualizarDireccion(Direccion direccion, DireccionUpdateDTO dto) {
        direccion.actualizar(
                dto.calle() != null ? dto.calle() : direccion.getCalle(),
                dto.numero() != null ? dto.numero() : direccion.getNumero(),
                dto.colonia() != null ? dto.colonia() : direccion.getColonia(),
                dto.municipio() != null ? dto.municipio() : direccion.getMunicipio(),
                dto.estado() != null ? dto.estado() : direccion.getEstado(),
                dto.codigoPostal() != null ? dto.codigoPostal() : direccion.getCodigoPostal(),
                dto.pais() != null ? dto.pais() : direccion.getPais(),
                direccion.getCpCatalogoId());
    }

    private String valorOVacio(String valor) {
        return valor != null ? valor : "";
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
