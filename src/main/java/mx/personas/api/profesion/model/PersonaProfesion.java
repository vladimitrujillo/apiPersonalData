package mx.personas.api.profesion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import mx.personas.api.common.audit.Auditable;
import mx.personas.api.persona.model.Persona;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Asignacion de una Profesion del catalogo a una Persona (FR-011 a FR-020).
 * Retirar solo desactiva (activo: true -> false); reasignar la misma
 * profesion tras retirarla SIEMPRE crea una fila nueva, nunca reactiva una
 * existente (research.md §4, decision de /speckit-clarify).
 */
@Entity
@Table(name = "persona_profesion")
public class PersonaProfesion extends Auditable {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "persona_id", nullable = false)
    private Persona persona;

    @ManyToOne(optional = false)
    @JoinColumn(name = "profesion_id", nullable = false)
    private Profesion profesion;

    @Column(name = "fecha_desde", nullable = false)
    private LocalDate fechaDesde;

    @Column
    private String cedula;

    @Column(nullable = false)
    private boolean activo = true;

    protected PersonaProfesion() {
        // JPA
    }

    public PersonaProfesion(Persona persona, Profesion profesion, LocalDate fechaDesde, String cedula) {
        this.persona = persona;
        this.profesion = profesion;
        this.fechaDesde = fechaDesde != null ? fechaDesde : LocalDate.now();
        this.cedula = cedula;
    }

    public void retirar() {
        this.activo = false;
    }

    public UUID getId() {
        return id;
    }

    public Persona getPersona() {
        return persona;
    }

    public Profesion getProfesion() {
        return profesion;
    }

    public LocalDate getFechaDesde() {
        return fechaDesde;
    }

    public String getCedula() {
        return cedula;
    }

    public boolean isActivo() {
        return activo;
    }
}
