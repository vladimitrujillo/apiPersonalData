package mx.personas.api.automovil.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import mx.personas.api.automovil.dto.MantenimientoResponseDTO;
import mx.personas.api.automovil.dto.MantenimientoUpdateDTO;
import mx.personas.api.automovil.mapper.MantenimientoMapper;
import mx.personas.api.automovil.model.Mantenimiento;
import mx.personas.api.automovil.service.MantenimientoService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/mantenimientos")
@Tag(name = "Mantenimientos", description = "Consulta, edición, eliminación y restauración de un mantenimiento por su propio ID")
public class MantenimientoController {

    private final MantenimientoService mantenimientoService;
    private final MantenimientoMapper mantenimientoMapper;

    public MantenimientoController(MantenimientoService mantenimientoService,
                                    MantenimientoMapper mantenimientoMapper) {
        this.mantenimientoService = mantenimientoService;
        this.mantenimientoMapper = mantenimientoMapper;
    }

    @Operation(summary = "Consultar el detalle de un mantenimiento",
            description = "Incluye sus piezas y los datos básicos del mecánico, si tiene (FR-023).")
    @ApiResponse(responseCode = "200", description = "Mantenimiento encontrado")
    @ApiResponse(responseCode = "404", description = "No existe un mantenimiento activo con ese ID")
    @PreAuthorize("hasAnyRole('ADMIN','CAPTURISTA')")
    @GetMapping("/{id}")
    public ResponseEntity<MantenimientoResponseDTO> obtenerPorId(@PathVariable UUID id) {
        Mantenimiento mantenimiento = mantenimientoService.obtenerPorId(id);
        return ResponseEntity.ok(mantenimientoMapper.toResponseDTO(mantenimiento));
    }

    @Operation(summary = "Actualizar un mantenimiento existente",
            description = "Aplica las mismas validaciones que al registrarlo. Si se envían piezas, reemplazan "
                    + "el conjunto completo.")
    @ApiResponse(responseCode = "200", description = "Mantenimiento actualizado")
    @ApiResponse(responseCode = "400", description = "Fecha futura, costo/kilometraje negativo, o mecánico inexistente")
    @ApiResponse(responseCode = "404", description = "No existe un mantenimiento activo con ese ID")
    @ApiResponse(responseCode = "409",
            description = "Automóvil o persona dueña eliminados, mecánico no elegible, o kilometraje inconsistente")
    @PreAuthorize("hasAnyRole('ADMIN','CAPTURISTA')")
    @PatchMapping("/{id}")
    public ResponseEntity<MantenimientoResponseDTO> editar(@PathVariable UUID id,
                                                             @Valid @RequestBody MantenimientoUpdateDTO request) {
        Mantenimiento mantenimiento = mantenimientoService.editar(id, request);
        return ResponseEntity.ok(mantenimientoMapper.toResponseDTO(mantenimiento));
    }

    @Operation(summary = "Eliminar lógicamente un mantenimiento",
            description = "No afecta al automóvil ni a los demás mantenimientos de su historial. Solo ADMIN.")
    @ApiResponse(responseCode = "204", description = "Mantenimiento eliminado lógicamente")
    @ApiResponse(responseCode = "403", description = "El rol autenticado no es ADMIN")
    @ApiResponse(responseCode = "404", description = "No existe un mantenimiento con ese ID")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        mantenimientoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Restaurar un mantenimiento eliminado lógicamente", description = "Solo ADMIN.")
    @ApiResponse(responseCode = "200", description = "Mantenimiento restaurado")
    @ApiResponse(responseCode = "403", description = "El rol autenticado no es ADMIN")
    @ApiResponse(responseCode = "404", description = "No existe un mantenimiento con ese ID")
    @ApiResponse(responseCode = "409", description = "El mantenimiento ya está activo")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/restaurar")
    public ResponseEntity<MantenimientoResponseDTO> restaurar(@PathVariable UUID id) {
        Mantenimiento mantenimiento = mantenimientoService.restaurar(id);
        return ResponseEntity.ok(mantenimientoMapper.toResponseDTO(mantenimiento));
    }
}
