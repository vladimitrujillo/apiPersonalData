package mx.personas.api.usuario.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import mx.personas.api.usuario.dto.UsuarioCreateDTO;
import mx.personas.api.usuario.dto.UsuarioResetPasswordDTO;
import mx.personas.api.usuario.dto.UsuarioResponseDTO;
import mx.personas.api.usuario.service.UsuarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/usuarios")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Usuarios", description = "Gestión de usuarios del sistema (solo ADMIN): alta, listado, desactivación y restablecimiento de contraseña")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Operation(summary = "Crear un usuario del sistema")
    @ApiResponse(responseCode = "201", description = "Usuario creado")
    @ApiResponse(responseCode = "409", description = "El login ya pertenece a otro usuario, activo o desactivado")
    @PostMapping
    public ResponseEntity<UsuarioResponseDTO> crear(@Valid @RequestBody UsuarioCreateDTO request) {
        UsuarioResponseDTO creado = usuarioService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @Operation(summary = "Listar usuarios del sistema")
    @ApiResponse(responseCode = "200", description = "Listado de usuarios (sin contraseñas ni hashes)")
    @GetMapping
    public ResponseEntity<List<UsuarioResponseDTO>> listar() {
        return ResponseEntity.ok(usuarioService.listar());
    }

    @Operation(summary = "Desactivar un usuario",
            description = "El usuario deja de poder iniciar sesión y cualquier refresh token suyo deja de ser válido.")
    @ApiResponse(responseCode = "200", description = "Usuario desactivado")
    @ApiResponse(responseCode = "404", description = "No existe un usuario con ese ID")
    @PatchMapping("/{id}/desactivar")
    public ResponseEntity<UsuarioResponseDTO> desactivar(@PathVariable UUID id) {
        return ResponseEntity.ok(usuarioService.desactivar(id));
    }

    @Operation(summary = "Restablecer la contraseña de un usuario",
            description = "La contraseña anterior queda invalidada de inmediato.")
    @ApiResponse(responseCode = "204", description = "Contraseña restablecida")
    @ApiResponse(responseCode = "404", description = "No existe un usuario con ese ID")
    @PatchMapping("/{id}/contrasena")
    public ResponseEntity<Void> restablecerContrasena(@PathVariable UUID id,
                                                        @Valid @RequestBody UsuarioResetPasswordDTO request) {
        usuarioService.restablecerContrasena(id, request.nuevaContrasena());
        return ResponseEntity.noContent().build();
    }
}
