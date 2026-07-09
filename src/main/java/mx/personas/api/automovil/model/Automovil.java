package mx.personas.api.automovil.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import mx.personas.api.common.audit.Auditable;
import mx.personas.api.persona.model.Persona;

import java.util.UUID;

/**
 * Automovil perteneciente a una persona (FR-001). El VIN es inmutable tras crearse
 * (identidad del vehiculo, ver V8); marca/modelo/anio/color/placas se pueden editar.
 */
@Entity
@Table(name = "automovil")
public class Automovil extends Auditable {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "persona_id", nullable = false)
    private Persona persona;

    @Column(nullable = false, length = 60)
    private String marca;

    @Column(nullable = false, length = 60)
    private String modelo;

    @Column(nullable = false)
    private Short anio;

    @Column(length = 40)
    private String color;

    @Column(nullable = false, length = 10)
    private String placas;

    @Column(length = 17)
    private String vin;

    @Column(nullable = false)
    private boolean activo = true;

    protected Automovil() {
        // JPA
    }

    public Automovil(Persona persona, String marca, String modelo, Short anio, String color, String placas,
                      String vin) {
        this.persona = persona;
        this.marca = marca;
        this.modelo = modelo;
        this.anio = anio;
        this.color = color;
        this.placas = placas;
        this.vin = vin;
    }

    public void editar(String marca, String modelo, Short anio, String color, String placas) {
        this.marca = marca;
        this.modelo = modelo;
        this.anio = anio;
        this.color = color;
        this.placas = placas;
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

    public Persona getPersona() {
        return persona;
    }

    public String getMarca() {
        return marca;
    }

    public String getModelo() {
        return modelo;
    }

    public Short getAnio() {
        return anio;
    }

    public String getColor() {
        return color;
    }

    public String getPlacas() {
        return placas;
    }

    public String getVin() {
        return vin;
    }

    public boolean isActivo() {
        return activo;
    }
}
