package mx.personas.api.profesion.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import mx.personas.api.profesion.dto.PersonaDirectorioDTO;
import mx.personas.api.profesion.dto.PersonaDirectorioPageResponseDTO;
import mx.personas.api.profesion.dto.ProfesionPageResponseDTO;
import mx.personas.api.profesion.dto.ProfesionRequestDTO;
import mx.personas.api.profesion.dto.ProfesionResponseDTO;
import mx.personas.api.profesion.dto.ProfesionUpdateDTO;
import mx.personas.api.profesion.mapper.ProfesionMapper;
import mx.personas.api.profesion.model.PersonaProfesion;
import mx.personas.api.profesion.model.Profesion;
import mx.personas.api.profesion.service.PersonaProfesionService;
import mx.personas.api.profesion.service.ProfesionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profesiones")
@Tag(name = "Profesiones", description = "Catálogo de profesiones y directorio de personas por profesión")
public class ProfesionController {

    private static final int TAMANO_PAGINA_DEFECTO = 20;
    private static final int TAMANO_PAGINA_MAXIMO = 100;

    private final ProfesionService profesionService;
    private final ProfesionMapper profesionMapper;
    private final PersonaProfesionService personaProfesionService;

    public ProfesionController(ProfesionService profesionService, ProfesionMapper profesionMapper,
                                PersonaProfesionService personaProfesionService) {
        this.profesionService = profesionService;
        this.profesionMapper = profesionMapper;
        this.personaProfesionService = personaProfesionService;
    }

    @Operation(summary = "Crear una profesión en el catálogo", description = "Solo ADMIN.")
    @ApiResponse(responseCode = "201", description = "Profesión creada")
    @ApiResponse(responseCode = "403", description = "El rol autenticado no es ADMIN")
    @ApiResponse(responseCode = "409", description = "Ya existe una profesión (activa o desactivada) con ese nombre")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ProfesionResponseDTO> crear(@Valid @RequestBody ProfesionRequestDTO request) {
        Profesion profesion = profesionService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(profesionMapper.toResponseDTO(profesion));
    }

    @Operation(summary = "Editar la descripción de una profesión", description = "El nombre no se puede editar. Solo ADMIN.")
    @ApiResponse(responseCode = "200", description = "Profesión actualizada")
    @ApiResponse(responseCode = "403", description = "El rol autenticado no es ADMIN")
    @ApiResponse(responseCode = "404", description = "No existe una profesión con ese ID")
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}")
    public ResponseEntity<ProfesionResponseDTO> editarDescripcion(@PathVariable Long id,
                                                                    @RequestBody ProfesionUpdateDTO request) {
        Profesion profesion = profesionService.editarDescripcion(id, request);
        return ResponseEntity.ok(profesionMapper.toResponseDTO(profesion));
    }

    @Operation(summary = "Desactivar una profesión", description = "No la elimina; bloquea asignaciones nuevas. Solo ADMIN.")
    @ApiResponse(responseCode = "200", description = "Profesión desactivada")
    @ApiResponse(responseCode = "403", description = "El rol autenticado no es ADMIN")
    @ApiResponse(responseCode = "404", description = "No existe una profesión con ese ID")
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/desactivar")
    public ResponseEntity<ProfesionResponseDTO> desactivar(@PathVariable Long id) {
        Profesion profesion = profesionService.desactivar(id);
        return ResponseEntity.ok(profesionMapper.toResponseDTO(profesion));
    }

    @Operation(summary = "Reactivar una profesión desactivada", description = "Solo ADMIN.")
    @ApiResponse(responseCode = "200", description = "Profesión reactivada")
    @ApiResponse(responseCode = "403", description = "El rol autenticado no es ADMIN")
    @ApiResponse(responseCode = "404", description = "No existe una profesión con ese ID")
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/reactivar")
    public ResponseEntity<ProfesionResponseDTO> reactivar(@PathVariable Long id) {
        Profesion profesion = profesionService.reactivar(id);
        return ResponseEntity.ok(profesionMapper.toResponseDTO(profesion));
    }

    @Operation(summary = "Listar el catálogo de profesiones",
            description = "Por defecto solo activas. `incluirInactivas` solo tiene efecto para ADMIN.")
    @ApiResponse(responseCode = "200", description = "Página del catálogo")
    @PreAuthorize("hasAnyRole('ADMIN','CAPTURISTA')")
    @GetMapping
    public ResponseEntity<ProfesionPageResponseDTO> listar(
            @Parameter(description = "Página, base 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página (máx. 100, por defecto 20)")
            @RequestParam(required = false) Integer size,
            @Parameter(description = "Incluir profesiones desactivadas (solo tiene efecto para ADMIN)")
            @RequestParam(defaultValue = "false") boolean incluirInactivas) {
        int tamano = Math.min(size != null ? size : TAMANO_PAGINA_DEFECTO, TAMANO_PAGINA_MAXIMO);
        Pageable pageable = PageRequest.of(page, tamano);
        boolean esAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        Page<Profesion> pagina = profesionService.listarCatalogo(pageable, incluirInactivas, esAdmin);
        var contenido = pagina.getContent().stream().map(profesionMapper::toResponseDTO).toList();
        return ResponseEntity.ok(new ProfesionPageResponseDTO(
                contenido, pagina.getNumber(), pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages()));
    }

    @Operation(summary = "Directorio de personas por profesión",
            description = "Solo personas activas con una asignación activa de esta profesión. DTO reducido: "
                    + "id, nombre completo, fecha desde y cédula — sin el resto de los datos personales.")
    @ApiResponse(responseCode = "200", description = "Página del directorio")
    @ApiResponse(responseCode = "404", description = "No existe una profesión con ese ID")
    @PreAuthorize("hasAnyRole('ADMIN','CAPTURISTA')")
    @GetMapping("/{id}/personas")
    public ResponseEntity<PersonaDirectorioPageResponseDTO> directorio(
            @PathVariable Long id,
            @Parameter(description = "Página, base 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página (máx. 100, por defecto 20)")
            @RequestParam(required = false) Integer size) {
        int tamano = Math.min(size != null ? size : TAMANO_PAGINA_DEFECTO, TAMANO_PAGINA_MAXIMO);
        Pageable pageable = PageRequest.of(page, tamano);

        Page<PersonaProfesion> pagina = personaProfesionService.directorio(id, pageable);
        var contenido = pagina.getContent().stream()
                .map(pp -> new PersonaDirectorioDTO(pp.getPersona().getId(),
                        pp.getPersona().getNombres() + " " + pp.getPersona().getApellidos(),
                        pp.getFechaDesde(), pp.getCedula()))
                .toList();
        return ResponseEntity.ok(new PersonaDirectorioPageResponseDTO(
                contenido, pagina.getNumber(), pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages()));
    }
}
