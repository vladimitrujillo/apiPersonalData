package mx.personas.api.automovil.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import mx.personas.api.common.audit.Auditable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mantenimiento de un automovil, con sus piezas cambiadas (FR-012). El mecanico se
 * referencia solo por id (sin relacion JPA a Persona) para no acoplar el agregado;
 * su nombre se resuelve en el mapper (research.md #9 de 008).
 */
@Entity
@Table(name = "mantenimiento")
public class Mantenimiento extends Auditable {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "automovil_id", nullable = false)
    private Automovil automovil;

    @Column(nullable = false)
    private String descripcion;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private Integer kilometraje;

    @Column(name = "mecanico_id")
    private UUID mecanicoId;

    @Column(name = "costo_total", nullable = false)
    private BigDecimal costoTotal;

    @Column(nullable = false)
    private boolean activo = true;

    @OneToMany(mappedBy = "mantenimiento", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    private List<PiezaCambiada> piezas = new ArrayList<>();

    protected Mantenimiento() {
        // JPA
    }

    public Mantenimiento(Automovil automovil, String descripcion, LocalDate fecha, Integer kilometraje,
                          UUID mecanicoId, BigDecimal costoTotal) {
        this.automovil = automovil;
        this.descripcion = descripcion;
        this.fecha = fecha;
        this.kilometraje = kilometraje;
        this.mecanicoId = mecanicoId;
        this.costoTotal = costoTotal;
    }

    public void editar(String descripcion, LocalDate fecha, Integer kilometraje, UUID mecanicoId,
                        BigDecimal costoTotal) {
        this.descripcion = descripcion;
        this.fecha = fecha;
        this.kilometraje = kilometraje;
        this.mecanicoId = mecanicoId;
        this.costoTotal = costoTotal;
    }

    /** Reemplaza la coleccion completa de piezas (research.md #5): borra huerfanas e inserta las nuevas. */
    public void actualizarPiezas(List<PiezaCambiada> nuevas) {
        this.piezas.clear();
        this.piezas.addAll(nuevas);
    }

    public void desactivar() {
        this.activo = false;
    }

    public void reactivar() {
        this.activo = true;
    }

    public UUID getId() {
        return id;
    }

    public Automovil getAutomovil() {
        return automovil;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public Integer getKilometraje() {
        return kilometraje;
    }

    public UUID getMecanicoId() {
        return mecanicoId;
    }

    public BigDecimal getCostoTotal() {
        return costoTotal;
    }

    public boolean isActivo() {
        return activo;
    }

    public List<PiezaCambiada> getPiezas() {
        return piezas;
    }
}
