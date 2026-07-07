package mx.personas.api.persona.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entrada inmutable del historial de cambios de una persona (FR-005 a FR-011). Solo se
 * inserta, nunca se actualiza ni se borra (FR-009).
 */
@Entity
@Table(name = "persona_historial")
public class PersonaHistorial {

    public enum TipoOperacion {
        CREACION,
        MODIFICACION,
        ELIMINACION,
        RESTAURACION
    }

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "persona_id", nullable = false)
    private Persona persona;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoOperacion operacion;

    @Column(nullable = false)
    private OffsetDateTime fecha;

    /** JSON serializado de List&lt;CampoCambiadoDTO&gt; (research.md §6); JSONB nativo. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String cambios;

    protected PersonaHistorial() {
        // JPA
    }

    public PersonaHistorial(Persona persona, UUID usuarioId, TipoOperacion operacion, String cambios) {
        this.persona = persona;
        this.usuarioId = usuarioId;
        this.operacion = operacion;
        this.fecha = OffsetDateTime.now();
        this.cambios = cambios;
    }

    public UUID getId() {
        return id;
    }

    public Persona getPersona() {
        return persona;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public TipoOperacion getOperacion() {
        return operacion;
    }

    public OffsetDateTime getFecha() {
        return fecha;
    }

    public String getCambios() {
        return cambios;
    }
}
