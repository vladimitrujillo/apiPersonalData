package mx.personas.api.automovil.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import mx.personas.api.automovil.dto.AutomovilResponseDTO;
import mx.personas.api.automovil.dto.AutomovilUpdateDTO;
import mx.personas.api.automovil.dto.MantenimientoPageResponseDTO;
import mx.personas.api.automovil.dto.MantenimientoRequestDTO;
import mx.personas.api.automovil.dto.MantenimientoResponseDTO;
import mx.personas.api.automovil.mapper.AutomovilMapper;
import mx.personas.api.automovil.mapper.MantenimientoMapper;
import mx.personas.api.automovil.model.Automovil;
import mx.personas.api.automovil.model.Mantenimiento;
import mx.personas.api.automovil.service.AutomovilService;
import mx.personas.api.automovil.service.MantenimientoService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/automoviles")
@Tag(name = "Automóviles", description = "Automóviles de personas y su historial de mantenimientos")
public class AutomovilController {

    private static final int TAMANO_PAGINA_DEFECTO = 20;
    private static final int TAMANO_PAGINA_MAXIMO = 100;

    private final AutomovilService automovilService;
    private final AutomovilMapper automovilMapper;
    private final MantenimientoService mantenimientoService;
    private final MantenimientoMapper mantenimientoMapper;

    public AutomovilController(AutomovilService automovilService, AutomovilMapper automovilMapper,
                                MantenimientoService mantenimientoService, MantenimientoMapper mantenimientoMapper) {
        this.automovilService = automovilService;
        this.automovilMapper = automovilMapper;
        this.mantenimientoService = mantenimientoService;
        this.mantenimientoMapper = mantenimientoMapper;
    }

    @Operation(summary = "Consultar el detalle de un automóvil")
    @ApiResponse(responseCode = "200", description = "Automóvil encontrado")
    @ApiResponse(responseCode = "404", description = "No existe un automóvil activo con ese ID")
    @PreAuthorize("hasAnyRole('ADMIN','CAPTURISTA')")
    @GetMapping("/{id}")
    public ResponseEntity<AutomovilResponseDTO> obtenerPorId(@PathVariable UUID id) {
        Automovil automovil = automovilService.obtenerPorId(id);
        return ResponseEntity.ok(automovilMapper.toResponseDTO(automovil));
    }

    @Operation(summary = "Editar los datos de un automóvil",
            description = "El VIN no se puede editar. Solo los campos enviados se modifican.")
    @ApiResponse(responseCode = "200", description = "Automóvil actualizado")
    @ApiResponse(responseCode = "400", description = "Año fuera de rango")
    @ApiResponse(responseCode = "404", description = "No existe un automóvil con ese ID")
    @ApiResponse(responseCode = "409", description = "El automóvil está eliminado, o las placas ya están en uso")
    @PreAuthorize("hasAnyRole('ADMIN','CAPTURISTA')")
    @PatchMapping("/{id}")
    public ResponseEntity<AutomovilResponseDTO> editar(@PathVariable UUID id,
                                                         @RequestBody AutomovilUpdateDTO request) {
        Automovil automovil = automovilService.editar(id, request);
        return ResponseEntity.ok(automovilMapper.toResponseDTO(automovil));
    }

    @Operation(summary = "Registrar un mantenimiento con sus piezas",
            description = "008-automoviles-mantenimientos. Todo en una sola operación transaccional.")
    @ApiResponse(responseCode = "201", description = "Mantenimiento registrado")
    @ApiResponse(responseCode = "400", description = "Fecha futura, costo/kilometraje negativo, o mecánico inexistente")
    @ApiResponse(responseCode = "404", description = "No existe un automóvil con ese ID")
    @ApiResponse(responseCode = "409",
            description = "Automóvil o persona dueña eliminados, mecánico no elegible, o kilometraje inconsistente")
    @PreAuthorize("hasAnyRole('ADMIN','CAPTURISTA')")
    @PostMapping("/{id}/mantenimientos")
    public ResponseEntity<MantenimientoResponseDTO> registrarMantenimiento(
            @PathVariable UUID id, @Valid @RequestBody MantenimientoRequestDTO request) {
        Mantenimiento mantenimiento = mantenimientoService.registrar(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(mantenimientoMapper.toResponseDTO(mantenimiento));
    }

    @Operation(summary = "Consultar el historial de mantenimientos de un automóvil",
            description = "Paginado, ordenado de la fecha más reciente a la más antigua (FR-022).")
    @ApiResponse(responseCode = "200", description = "Página del historial")
    @ApiResponse(responseCode = "404", description = "No existe un automóvil activo con ese ID")
    @PreAuthorize("hasAnyRole('ADMIN','CAPTURISTA')")
    @GetMapping("/{id}/mantenimientos")
    public ResponseEntity<MantenimientoPageResponseDTO> historialDeMantenimientos(
            @PathVariable UUID id,
            @Parameter(description = "Página, base 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página (máx. 100, por defecto 20)")
            @RequestParam(required = false) Integer size) {
        int tamano = Math.min(size != null ? size : TAMANO_PAGINA_DEFECTO, TAMANO_PAGINA_MAXIMO);
        Pageable pageable = PageRequest.of(page, tamano);

        Page<Mantenimiento> pagina = mantenimientoService.listarHistorial(id, pageable);
        var contenido = pagina.getContent().stream().map(mantenimientoMapper::toResponseDTO).toList();
        return ResponseEntity.ok(new MantenimientoPageResponseDTO(
                contenido, pagina.getNumber(), pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages()));
    }

    @Operation(summary = "Eliminar lógicamente un automóvil",
            description = "Oculta también su historial de mantenimientos, sin borrarlo. Solo ADMIN.")
    @ApiResponse(responseCode = "204", description = "Automóvil eliminado lógicamente")
    @ApiResponse(responseCode = "403", description = "El rol autenticado no es ADMIN")
    @ApiResponse(responseCode = "404", description = "No existe un automóvil con ese ID")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        automovilService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Restaurar un automóvil eliminado lógicamente",
            description = "Devuelve también su historial de mantenimientos a su estado consultable previo. Solo ADMIN.")
    @ApiResponse(responseCode = "200", description = "Automóvil restaurado")
    @ApiResponse(responseCode = "403", description = "El rol autenticado no es ADMIN")
    @ApiResponse(responseCode = "404", description = "No existe un automóvil con ese ID")
    @ApiResponse(responseCode = "409", description = "El automóvil ya está activo")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/restaurar")
    public ResponseEntity<AutomovilResponseDTO> restaurar(@PathVariable UUID id) {
        Automovil automovil = automovilService.restaurar(id);
        return ResponseEntity.ok(automovilMapper.toResponseDTO(automovil));
    }
}
