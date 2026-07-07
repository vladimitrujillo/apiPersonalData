package mx.personas.api.codigopostal.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import mx.personas.api.codigopostal.dto.CorridaImportacionDTO;
import mx.personas.api.codigopostal.dto.CorridaImportacionPageResponseDTO;
import mx.personas.api.codigopostal.dto.ResumenImportacionDTO;
import mx.personas.api.codigopostal.importer.ArchivoHashCalculator;
import mx.personas.api.codigopostal.importer.ResumenImportacion;
import mx.personas.api.codigopostal.model.CatalogoImportacion;
import mx.personas.api.codigopostal.model.CatalogoImportacion.OrigenImportacion;
import mx.personas.api.codigopostal.repository.CatalogoImportacionRepository;
import mx.personas.api.codigopostal.service.CatalogoImportacionOrchestrator;
import mx.personas.api.common.audit.SecurityAuditorAware;
import mx.personas.api.usuario.model.Usuario;
import mx.personas.api.usuario.repository.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@RestController
@RequestMapping("/api/codigos-postales/importaciones")
@Tag(name = "Catálogo SEPOMEX", description = "Disparo manual y bitácora de importaciones del catálogo de códigos postales")
public class CatalogoImportacionController {

    private static final int TAMANO_PAGINA_DEFECTO = 20;
    private static final int TAMANO_PAGINA_MAXIMO = 100;

    private final CatalogoImportacionOrchestrator orchestrator;
    private final SecurityAuditorAware securityAuditorAware;
    private final CatalogoImportacionRepository catalogoImportacionRepository;
    private final UsuarioRepository usuarioRepository;

    public CatalogoImportacionController(CatalogoImportacionOrchestrator orchestrator,
                                          SecurityAuditorAware securityAuditorAware,
                                          CatalogoImportacionRepository catalogoImportacionRepository,
                                          UsuarioRepository usuarioRepository) {
        this.orchestrator = orchestrator;
        this.securityAuditorAware = securityAuditorAware;
        this.catalogoImportacionRepository = catalogoImportacionRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Operation(summary = "Disparar una importación manual del catálogo",
            description = "Sube un archivo de catálogo SEPOMEX y ejecuta su importación de inmediato, sin "
                    + "esperar al ciclo programado. Solo ADMIN.")
    @ApiResponse(responseCode = "200", description = "Importación ejecutada; cuerpo con el resumen")
    @ApiResponse(responseCode = "400",
            description = "Archivo con estructura inválida, o excede el tamaño máximo permitido")
    @ApiResponse(responseCode = "403", description = "El rol autenticado no es ADMIN")
    @ApiResponse(responseCode = "409", description = "Ya hay una importación en curso")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ResumenImportacionDTO> importarManualmente(
            @RequestParam("archivo") MultipartFile archivo) {
        UUID usuarioId = securityAuditorAware.getCurrentAuditor()
                .orElseThrow(() -> new IllegalStateException("No hay un usuario autenticado para disparar la importación"));

        Path temporal = escribirATemporal(archivo);
        String hash = ArchivoHashCalculator.calcular(temporal);
        ResumenImportacion resumen = orchestrator.ejecutar(
                temporal, archivo.getOriginalFilename(), hash, OrigenImportacion.MANUAL, usuarioId);

        return ResponseEntity.ok(new ResumenImportacionDTO(
                resumen.insertados(), resumen.actualizados(), resumen.sinCambio(), resumen.rechazados(),
                resumen.detallesRechazados()));
    }

    @Operation(summary = "Consultar la bitácora de corridas de importación",
            description = "Historial paginado, de la más reciente a la más antigua, de corridas programadas y "
                    + "manuales. Solo ADMIN.")
    @ApiResponse(responseCode = "200", description = "Página de la bitácora")
    @ApiResponse(responseCode = "403", description = "El rol autenticado no es ADMIN")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<CorridaImportacionPageResponseDTO> bitacora(
            @Parameter(description = "Página, base 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página (máx. 100, por defecto 20)")
            @RequestParam(required = false) Integer size) {
        int tamano = Math.min(size != null ? size : TAMANO_PAGINA_DEFECTO, TAMANO_PAGINA_MAXIMO);
        Pageable pageable = PageRequest.of(page, tamano);
        Page<CatalogoImportacion> pagina = catalogoImportacionRepository.findAllByOrderByFechaInicioDesc(pageable);

        var contenido = pagina.getContent().stream().map(this::toCorridaImportacionDTO).toList();
        return ResponseEntity.ok(new CorridaImportacionPageResponseDTO(
                contenido, pagina.getNumber(), pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages()));
    }

    private CorridaImportacionDTO toCorridaImportacionDTO(CatalogoImportacion corrida) {
        String login = corrida.getUsuarioId() == null ? null
                : usuarioRepository.findById(corrida.getUsuarioId()).map(Usuario::getLogin).orElse(null);
        return new CorridaImportacionDTO(
                corrida.getFechaInicio(), corrida.getOrigen().name(), login, corrida.getArchivo(),
                corrida.getDuracionMs(), corrida.getInsertados(), corrida.getActualizados(),
                corrida.getSinCambio(), corrida.getRechazados(), corrida.getEstado().name(),
                corrida.getDetalleError());
    }

    private Path escribirATemporal(MultipartFile archivo) {
        try {
            Path temporal = Files.createTempFile("catalogo-sepomex-manual", ".csv");
            archivo.transferTo(temporal);
            return temporal;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo procesar el archivo subido", e);
        }
    }
}
