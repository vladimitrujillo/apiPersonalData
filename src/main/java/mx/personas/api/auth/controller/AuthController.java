package mx.personas.api.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import mx.personas.api.auth.dto.LoginRequestDTO;
import mx.personas.api.auth.dto.RefreshRequestDTO;
import mx.personas.api.auth.dto.TokenResponseDTO;
import mx.personas.api.auth.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Autenticación", description = "Login y renovación de tokens de acceso")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Iniciar sesión",
            description = "Devuelve un token de acceso (JWT, corta duración) y un token de refresco "
                    + "(opaco, mayor duración) si las credenciales son válidas.")
    @ApiResponse(responseCode = "200", description = "Login exitoso")
    @ApiResponse(responseCode = "401", description = "Credenciales incorrectas, usuario inexistente o desactivado")
    @PostMapping("/login")
    public ResponseEntity<TokenResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(authService.login(request.login(), request.password()));
    }

    @Operation(summary = "Renovar el token de acceso",
            description = "Usa un token de refresco vigente para emitir un nuevo par de tokens (rotación de un "
                    + "solo uso); el token de refresco recibido queda invalidado tras esta llamada.")
    @ApiResponse(responseCode = "200", description = "Renovación exitosa")
    @ApiResponse(responseCode = "401",
            description = "Token de refresco inválido, expirado, ya usado, o de un usuario desactivado")
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponseDTO> refresh(@Valid @RequestBody RefreshRequestDTO request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }
}
