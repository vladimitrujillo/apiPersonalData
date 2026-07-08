package mx.personas.api.profesion.service;

import mx.personas.api.common.error.DuplicateFieldException;
import mx.personas.api.common.error.ErrorCode;
import mx.personas.api.common.error.RecursoNoEncontradoException;
import mx.personas.api.profesion.dto.ProfesionRequestDTO;
import mx.personas.api.profesion.dto.ProfesionUpdateDTO;
import mx.personas.api.profesion.model.Profesion;
import mx.personas.api.profesion.repository.ProfesionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestión del catálogo de profesiones (US1): crear, editar descripción,
 * desactivar/reactivar y listar. La unicidad de nombre se valida aquí con la
 * misma normalización (unaccent+lower) que el índice de base de datos
 * (research.md §6), para que el 409 de negocio y el índice nunca discrepen.
 */
@Service
public class ProfesionService {

    private final ProfesionRepository profesionRepository;

    public ProfesionService(ProfesionRepository profesionRepository) {
        this.profesionRepository = profesionRepository;
    }

    @Transactional
    public Profesion crear(ProfesionRequestDTO request) {
        profesionRepository.findByNombreNormalizado(request.nombre()).ifPresent(existente -> {
            if (existente.isActivo()) {
                throw new DuplicateFieldException(ErrorCode.PROFESION_NOMBRE_DUPLICADO, "nombre",
                        "Ya existe una profesión activa con este nombre",
                        "Debe ser único entre profesiones (sin distinguir mayúsculas ni acentos)");
            }
            throw new DuplicateFieldException(ErrorCode.PROFESION_NOMBRE_DESACTIVADA, "nombre",
                    "Existe una profesión desactivada con este nombre; un ADMIN puede reactivarla",
                    "Profesión desactivada con id " + existente.getId());
        });
        return profesionRepository.save(new Profesion(request.nombre(), request.descripcion()));
    }

    @Transactional
    public Profesion editarDescripcion(Long id, ProfesionUpdateDTO request) {
        Profesion profesion = obtenerOFallar(id);
        profesion.editarDescripcion(request.descripcion());
        return profesion;
    }

    @Transactional
    public Profesion desactivar(Long id) {
        Profesion profesion = obtenerOFallar(id);
        profesion.desactivar();
        return profesion;
    }

    @Transactional
    public Profesion reactivar(Long id) {
        Profesion profesion = obtenerOFallar(id);
        profesion.reactivar();
        return profesion;
    }

    public Page<Profesion> listarCatalogo(Pageable pageable, boolean incluirInactivas, boolean esAdmin) {
        if (incluirInactivas && esAdmin) {
            return profesionRepository.findAll(pageable);
        }
        return profesionRepository.findByActivoTrue(pageable);
    }

    private Profesion obtenerOFallar(Long id) {
        return profesionRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(ErrorCode.PROFESION_NO_ENCONTRADA,
                        "No existe una profesión con el identificador '" + id + "'"));
    }
}
