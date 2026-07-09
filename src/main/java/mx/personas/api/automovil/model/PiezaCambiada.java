package mx.personas.api.automovil.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Pieza cambiada durante un mantenimiento (composicion pura; su ciclo de vida esta
 * gobernado por el Mantenimiento al que pertenece, sin auditoria propia).
 */
@Entity
@Table(name = "pieza_cambiada")
public class PiezaCambiada {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "mantenimiento_id", nullable = false)
    private Mantenimiento mantenimiento;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(name = "numero_parte", length = 40)
    private String numeroParte;

    private BigDecimal costo;

    protected PiezaCambiada() {
        // JPA
    }

    public PiezaCambiada(Mantenimiento mantenimiento, String nombre, String numeroParte, BigDecimal costo) {
        this.mantenimiento = mantenimiento;
        this.nombre = nombre;
        this.numeroParte = numeroParte;
        this.costo = costo;
    }

    public UUID getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getNumeroParte() {
        return numeroParte;
    }

    public BigDecimal getCosto() {
        return costo;
    }
}
