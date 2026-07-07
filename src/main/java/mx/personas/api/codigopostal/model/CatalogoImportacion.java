package mx.personas.api.codigopostal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entrada inmutable de la bitacora de corridas de importacion del catalogo SEPOMEX
 * (FR-007, data-model.md). Solo se inserta, ya con su estado final; nunca se actualiza
 * ni se borra (a diferencia de persona, aqui no hay una corrida "en progreso" visible).
 */
@Entity
@Table(name = "catalogo_importacion")
public class CatalogoImportacion {

    public enum OrigenImportacion {
        PROGRAMADA,
        MANUAL
    }

    public enum EstadoImportacion {
        EXITO,
        ERROR,
        RECHAZADA_CONCURRENCIA
    }

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrigenImportacion origen;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(nullable = false)
    private String archivo;

    @Column(name = "archivo_hash", nullable = false)
    private String archivoHash;

    @Column(name = "fecha_inicio", nullable = false)
    private OffsetDateTime fechaInicio;

    @Column(name = "duracion_ms")
    private Long duracionMs;

    @Column(nullable = false)
    private int insertados;

    @Column(nullable = false)
    private int actualizados;

    @Column(name = "sin_cambio", nullable = false)
    private int sinCambio;

    @Column(nullable = false)
    private int rechazados;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoImportacion estado;

    @Column(name = "detalle_error")
    private String detalleError;

    protected CatalogoImportacion() {
        // JPA
    }

    private CatalogoImportacion(OrigenImportacion origen, UUID usuarioId, String archivo, String archivoHash,
                                 long duracionMs, int insertados, int actualizados, int sinCambio, int rechazados,
                                 EstadoImportacion estado, String detalleError) {
        this.origen = origen;
        this.usuarioId = usuarioId;
        this.archivo = archivo;
        this.archivoHash = archivoHash;
        this.fechaInicio = OffsetDateTime.now();
        this.duracionMs = duracionMs;
        this.insertados = insertados;
        this.actualizados = actualizados;
        this.sinCambio = sinCambio;
        this.rechazados = rechazados;
        this.estado = estado;
        this.detalleError = detalleError;
    }

    public static CatalogoImportacion exitosa(OrigenImportacion origen, UUID usuarioId, String archivo,
                                               String archivoHash, long duracionMs, int insertados, int actualizados,
                                               int sinCambio, int rechazados) {
        return new CatalogoImportacion(origen, usuarioId, archivo, archivoHash, duracionMs, insertados, actualizados,
                sinCambio, rechazados, EstadoImportacion.EXITO, null);
    }

    public static CatalogoImportacion fallida(OrigenImportacion origen, UUID usuarioId, String archivo,
                                               String archivoHash, long duracionMs, String detalleError) {
        return new CatalogoImportacion(origen, usuarioId, archivo, archivoHash, duracionMs, 0, 0, 0, 0,
                EstadoImportacion.ERROR, detalleError);
    }

    public static CatalogoImportacion rechazadaPorConcurrencia(OrigenImportacion origen, UUID usuarioId,
                                                                String archivo, String archivoHash) {
        return new CatalogoImportacion(origen, usuarioId, archivo, archivoHash, 0, 0, 0, 0, 0,
                EstadoImportacion.RECHAZADA_CONCURRENCIA,
                "Ya hay una importación en curso; esta corrida se rechazó de inmediato");
    }

    public UUID getId() {
        return id;
    }

    public OrigenImportacion getOrigen() {
        return origen;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getArchivo() {
        return archivo;
    }

    public String getArchivoHash() {
        return archivoHash;
    }

    public OffsetDateTime getFechaInicio() {
        return fechaInicio;
    }

    public Long getDuracionMs() {
        return duracionMs;
    }

    public int getInsertados() {
        return insertados;
    }

    public int getActualizados() {
        return actualizados;
    }

    public int getSinCambio() {
        return sinCambio;
    }

    public int getRechazados() {
        return rechazados;
    }

    public EstadoImportacion getEstado() {
        return estado;
    }

    public String getDetalleError() {
        return detalleError;
    }
}
