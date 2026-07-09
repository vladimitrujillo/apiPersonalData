package mx.personas.api.automovil.mapper;

import mx.personas.api.automovil.dto.MantenimientoResponseDTO;
import mx.personas.api.automovil.dto.MecanicoResumenDTO;
import mx.personas.api.automovil.dto.PiezaCambiadaDTO;
import mx.personas.api.automovil.model.Mantenimiento;
import mx.personas.api.automovil.model.PiezaCambiada;
import mx.personas.api.persona.repository.PersonaRepository;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

@Mapper(componentModel = "spring")
public abstract class MantenimientoMapper {

    @Autowired
    protected PersonaRepository personaRepository;

    @Mapping(target = "automovilId", source = "automovil.id")
    @Mapping(target = "mecanico", expression = "java(resolverMecanico(mantenimiento.getMecanicoId()))")
    public abstract MantenimientoResponseDTO toResponseDTO(Mantenimiento mantenimiento);

    public abstract PiezaCambiadaDTO toPiezaDTO(PiezaCambiada pieza);

    protected MecanicoResumenDTO resolverMecanico(UUID mecanicoId) {
        if (mecanicoId == null) {
            return null;
        }
        return personaRepository.findById(mecanicoId)
                .map(persona -> new MecanicoResumenDTO(persona.getId(),
                        persona.getNombres() + " " + persona.getApellidos()))
                .orElse(null);
    }
}
