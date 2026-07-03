package mx.personas.api.codigopostal.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import mx.personas.api.codigopostal.dto.CodigoPostalResponseDTO;
import mx.personas.api.codigopostal.service.CodigoPostalService;
import mx.personas.api.common.error.ErrorCode;
import mx.personas.api.common.error.FormatoInvalidoException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/codigos-postales")
@Tag(name = "Códigos Postales", description = "Consulta del catálogo nacional de códigos postales (SEPOMEX)")
public class CodigoPostalController {

    private static final Pattern FORMATO_CP = Pattern.compile("^\\d{5}$");
    private static final long CACHE_MAX_AGE_SEGUNDOS = TimeUnit.DAYS.toSeconds(1);

    private final CodigoPostalService codigoPostalService;

    public CodigoPostalController(CodigoPostalService codigoPostalService) {
        this.codigoPostalService = codigoPostalService;
    }

    @Operation(summary = "Consultar un código postal exacto",
            description = "Regresa el estado, el municipio y la lista de colonias/asentamientos asociadas a un "
                    + "código postal de 5 dígitos. Respuesta cacheable (el catálogo cambia con poca frecuencia).")
    @ApiResponse(responseCode = "200", description = "Código postal encontrado")
    @ApiResponse(responseCode = "400", description = "El código postal no tiene exactamente 5 dígitos numéricos")
    @ApiResponse(responseCode = "404", description = "El código postal no existe en el catálogo")
    @GetMapping("/{codigoPostal}")
    public ResponseEntity<CodigoPostalResponseDTO> consultarPorCodigoPostal(@PathVariable String codigoPostal) {
        validarFormato(codigoPostal);
        CodigoPostalResponseDTO respuesta = codigoPostalService.consultarPorCodigoPostal(codigoPostal);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(CACHE_MAX_AGE_SEGUNDOS, TimeUnit.SECONDS))
                .body(respuesta);
    }

    private void validarFormato(String codigoPostal) {
        if (!FORMATO_CP.matcher(codigoPostal).matches()) {
            throw new FormatoInvalidoException(ErrorCode.CP_FORMATO_INVALIDO, "codigoPostal",
                    "El código postal debe tener exactamente 5 dígitos numéricos");
        }
    }
}
