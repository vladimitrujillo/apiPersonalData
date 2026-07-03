package mx.personas.api.persona.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import mx.personas.api.persona.dto.PersonaPageResponseDTO;
import mx.personas.api.persona.dto.PersonaRequestDTO;
import mx.personas.api.persona.dto.PersonaResponseDTO;
import mx.personas.api.persona.dto.PersonaUpdateDTO;
import mx.personas.api.persona.service.PersonaService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
    @PostMapping
    public ResponseEntity<PersonaResponseDTO> crear(@Valid @RequestBody PersonaRequestDTO request) {
        PersonaResponseDTO creada = personaService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @Operation(summary = "Listar personas activas",
            description = "Listado paginado de personas activas, con filtros opcionales por nombre "
                    + "(coincidencia parcial), municipio y estado de su dirección vigente.")
    @ApiResponse(responseCode = "200", description = "Página de resultados")
    @GetMapping
    public ResponseEntity<PersonaPageResponseDTO> listar(
            @Parameter(description = "Coincidencia parcial contra nombres + apellidos")
            @RequestParam(required = false) String nombre,
            @Parameter(description = "Municipio de la dirección vigente")
            @RequestParam(required = false) String municipio,
            @Parameter(description = "Estado de la dirección vigente")
            @RequestParam(required = false) String estado,
            @Parameter(description = "Página, base 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página (máx. 100, por defecto 20)")
            @RequestParam(required = false) Integer size) {
        int tamano = Math.min(size != null ? size : TAMANO_PAGINA_DEFECTO, TAMANO_PAGINA_MAXIMO);
        Pageable pageable = PageRequest.of(page, tamano);
        return ResponseEntity.ok(personaService.listar(nombre, municipio, estado, pageable));
    }

    @Operation(summary = "Consultar una persona activa por ID")
    @ApiResponse(responseCode = "200", description = "Persona encontrada")
    @ApiResponse(responseCode = "404", description = "No existe una persona activa con ese ID")
    @GetMapping("/{id}")
    public ResponseEntity<PersonaResponseDTO> obtenerPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(personaService.obtenerPorId(id));
    }

    @Operation(summary = "Actualizar parcialmente una persona activa",
            description = "Solo los campos enviados se modifican; los demás conservan su valor actual.")
    @ApiResponse(responseCode = "200", description = "Persona actualizada")
    @ApiResponse(responseCode = "404", description = "No existe una persona activa con ese ID")
    @PatchMapping("/{id}")
    public ResponseEntity<PersonaResponseDTO> actualizar(@PathVariable UUID id,
                                                           @Valid @RequestBody PersonaUpdateDTO request) {
        return ResponseEntity.ok(personaService.actualizar(id, request));
    }

    @Operation(summary = "Eliminar lógicamente una persona",
            description = "El registro se conserva en la base de datos pero deja de aparecer en consultas y listados.")
    @ApiResponse(responseCode = "204", description = "Persona eliminada lógicamente")
    @ApiResponse(responseCode = "404", description = "No existe una persona activa con ese ID")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        personaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
