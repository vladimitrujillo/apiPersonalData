package mx.personas.api.codigopostal.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import mx.personas.api.codigopostal.dto.ColoniaBusquedaDTO;
import mx.personas.api.codigopostal.service.CodigoPostalService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/colonias")
@Tag(name = "Colonias", description = "Autocompletado de colonias/asentamientos por coincidencia parcial de nombre")
public class ColoniaController {

    private static final long CACHE_MAX_AGE_SEGUNDOS = TimeUnit.DAYS.toSeconds(1);

    private final CodigoPostalService codigoPostalService;

    public ColoniaController(CodigoPostalService codigoPostalService) {
        this.codigoPostalService = codigoPostalService;
    }

    @Operation(summary = "Buscar colonias por coincidencia parcial de nombre",
            description = "Opcionalmente acotada a un estado y/o municipio. Sin coincidencias regresa una lista "
                    + "vacía (nunca error). Pensado para autocompletado.")
    @ApiResponse(responseCode = "200", description = "Lista de colonias coincidentes (puede estar vacía)")
    @ApiResponse(responseCode = "400", description = "Falta el parámetro requerido 'nombre'")
    @PreAuthorize("hasAnyRole('ADMIN','CAPTURISTA')")
    @GetMapping
    public ResponseEntity<List<ColoniaBusquedaDTO>> buscar(
            @Parameter(description = "Fragmento de nombre a buscar (requerido)")
            @RequestParam String nombre,
            @Parameter(description = "Acota la búsqueda a un estado")
            @RequestParam(required = false) String estado,
            @Parameter(description = "Acota la búsqueda a un municipio")
            @RequestParam(required = false) String municipio) {
        List<ColoniaBusquedaDTO> resultado = codigoPostalService.buscarColonias(nombre, estado, municipio);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(CACHE_MAX_AGE_SEGUNDOS, TimeUnit.SECONDS))
                .body(resultado);
    }
}
