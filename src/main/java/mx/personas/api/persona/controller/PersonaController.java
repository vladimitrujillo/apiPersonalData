package mx.personas.api.persona.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import mx.personas.api.persona.dto.HistorialPageResponseDTO;
import mx.personas.api.persona.dto.PersonaBusquedaFiltroDTO;
import mx.personas.api.persona.dto.PersonaEliminadaPageResponseDTO;
import mx.personas.api.persona.dto.PersonaPageResponseDTO;
import mx.personas.api.persona.dto.PersonaRequestDTO;
import mx.personas.api.persona.dto.PersonaResponseDTO;
import mx.personas.api.persona.dto.PersonaUpdateDTO;
import mx.personas.api.persona.service.PersonaService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/personas")
@Tag(name = "Personas", description = "Gestión de personas: alta, consulta, listado, actualización parcial y borrado lógico")
public class PersonaController {

    private static final int TAMANO_PAGINA_DEFECTO = 20;
    private static final int TAMANO_PAGINA_MAXIMO = 100;

    private final PersonaService personaService;

    public PersonaController(PersonaService personaService) {
        this.personaService = personaService;
    }

    @Operation(summary = "Crear una persona",
            description = "Valida datos, verifica que correo y CURP sean únicos entre personas activas, "
                    + "y valida/autocompleta la dirección contra el catálogo de códigos postales si el país es México.")
    @ApiResponse(responseCode = "201", description = "Persona creada")
    @PreAuthorize("hasAnyRole('ADMIN','CAPTURISTA')")
    @PostMapping
    public ResponseEntity<PersonaResponseDTO> crear(@Valid @RequestBody PersonaRequestDTO request) {
        PersonaResponseDTO creada = personaService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @Operation(summary = "Buscar/listar personas",
            description = "Listado paginado de personas activas, con criterios combinables (AND) y opcionales: "
                    + "texto libre sobre nombres/apellidos (insensible a mayúsculas y acentos), prefijo de CURP, "
                    + "rango de edad, rango de fecha de registro, municipio y estado de la dirección vigente, "
                    + "sexo, estado activo/eliminado (solo ADMIN; ignorado para CAPTURISTA) y ordenamiento.")
    @ApiResponse(responseCode = "200", description = "Página de resultados")
    @ApiResponse(responseCode = "400", description = "Un parámetro de rango u ordenamiento es inválido")
    @PreAuthorize("hasAnyRole('ADMIN','CAPTURISTA')")
    @GetMapping
    public ResponseEntity<PersonaPageResponseDTO> listar(
            @Valid @ModelAttribute PersonaBusquedaFiltroDTO filtro,
            @Parameter(description = "Página, base 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página (máx. 100, por defecto 20)")
            @RequestParam(required = false) Integer size) {
        int tamano = Math.min(size != null ? size : TAMANO_PAGINA_DEFECTO, TAMANO_PAGINA_MAXIMO);
        Pageable pageable = PageRequest.of(page, tamano);
        String estadoRegistroEfectivo = esAdmin() ? filtro.estadoRegistro() : "ACTIVAS";
        return ResponseEntity.ok(personaService.listar(filtro, estadoRegistroEfectivo, pageable));
    }

    /** FR-007/FR-008: solo ADMIN puede pedir estadoRegistro distinto de "solo activas". */
    private boolean esAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(autoridad -> autoridad.getAuthority().equals("ROLE_ADMIN"));
    }

    @Operation(summary = "Consultar una persona activa por ID")
    @ApiResponse(responseCode = "200", description = "Persona encontrada")
    @ApiResponse(responseCode = "404", description = "No existe una persona activa con ese ID")
    @PreAuthorize("hasAnyRole('ADMIN','CAPTURISTA')")
    @GetMapping("/{id}")
    public ResponseEntity<PersonaResponseDTO> obtenerPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(personaService.obtenerPorId(id));
    }

    @Operation(summary = "Actualizar parcialmente una persona activa",
            description = "Solo los campos enviados se modifican; los demás conservan su valor actual.")
    @ApiResponse(responseCode = "200", description = "Persona actualizada")
    @ApiResponse(responseCode = "404", description = "No existe una persona activa con ese ID")
    @PreAuthorize("hasAnyRole('ADMIN','CAPTURISTA')")
    @PatchMapping("/{id}")
    public ResponseEntity<PersonaResponseDTO> actualizar(@PathVariable UUID id,
                                                           @Valid @RequestBody PersonaUpdateDTO request) {
        return ResponseEntity.ok(personaService.actualizar(id, request));
    }

    @Operation(summary = "Eliminar lógicamente una persona",
            description = "El registro se conserva en la base de datos pero deja de aparecer en consultas y listados.")
    @ApiResponse(responseCode = "204", description = "Persona eliminada lógicamente")
    @ApiResponse(responseCode = "404", description = "No existe una persona activa con ese ID")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        personaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Consultar el historial de cambios de una persona",
            description = "Lista paginada de cada operación (creación, modificación, eliminación lógica, "
                    + "restauración) con autor, fecha y campos cambiados. Solo ADMIN.")
    @ApiResponse(responseCode = "200", description = "Página de entradas del historial")
    @ApiResponse(responseCode = "403", description = "El rol autenticado no es ADMIN")
    @ApiResponse(responseCode = "404", description = "No existe ninguna persona con ese ID")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}/historial")
    public ResponseEntity<HistorialPageResponseDTO> historial(
            @PathVariable UUID id,
            @Parameter(description = "Página, base 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página (máx. 100, por defecto 20)")
            @RequestParam(required = false) Integer size) {
        int tamano = Math.min(size != null ? size : TAMANO_PAGINA_DEFECTO, TAMANO_PAGINA_MAXIMO);
        Pageable pageable = PageRequest.of(page, tamano);
        return ResponseEntity.ok(personaService.historial(id, pageable));
    }

    @Operation(summary = "Restaurar una persona eliminada lógicamente",
            description = "Revierte una eliminación lógica; la persona vuelve a ser consultable y a aparecer "
                    + "en listados, con sus datos y dirección intactos. Solo ADMIN. La CURP nunca produce "
                    + "conflicto al restaurar (unicidad global); solo el correo puede hacerlo.")
    @ApiResponse(responseCode = "200", description = "Persona restaurada")
    @ApiResponse(responseCode = "403", description = "El rol autenticado no es ADMIN")
    @ApiResponse(responseCode = "404", description = "No existe ninguna persona con ese ID")
    @ApiResponse(responseCode = "409",
            description = "La persona ya está activa, o su correo ya pertenece a otra persona activa")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/restaurar")
    public ResponseEntity<PersonaResponseDTO> restaurar(@PathVariable UUID id) {
        return ResponseEntity.ok(personaService.restaurar(id));
    }

    @Operation(summary = "Listar personas eliminadas lógicamente",
            description = "Vista paginada y dedicada de personas con activo=false. Solo ADMIN; separada del "
                    + "listado general, que nunca incluye personas eliminadas.")
    @ApiResponse(responseCode = "200", description = "Página de personas eliminadas")
    @ApiResponse(responseCode = "403", description = "El rol autenticado no es ADMIN")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/eliminadas")
    public ResponseEntity<PersonaEliminadaPageResponseDTO> listarEliminadas(
            @Parameter(description = "Página, base 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página (máx. 100, por defecto 20)")
            @RequestParam(required = false) Integer size) {
        int tamano = Math.min(size != null ? size : TAMANO_PAGINA_DEFECTO, TAMANO_PAGINA_MAXIMO);
        Pageable pageable = PageRequest.of(page, tamano);
        return ResponseEntity.ok(personaService.listarEliminadas(pageable));
    }
}
