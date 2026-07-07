package mx.personas.api.persona.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import mx.personas.api.common.audit.Auditable;

import java.util.UUID;

/**
 * Direccion de una persona. Tabla propia (1:N a nivel de esquema respecto a persona); en
 * el alcance de este feature el service mantiene una unica direccion vigente por persona
 * (ver research.md #3c y data-model.md).
 *
 * colonia/municipio/estado son un snapshot de texto tomado al momento de guardar; cuando
 * el pais es MX y el CP existe en el catalogo, cpCatalogoId referencia la fila exacta
 * usada para validar/autocompletar (FR-019 a FR-021). Es NULL cuando pais != MX (FR-022).
 */
@Entity
@Table(name = "direccion")
public class Direccion extends Auditable {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "persona_id", nullable = false)
    private Persona persona;

    @Column(nullable = false)
    private String calle;

    @Column(nullable = false)
    private String numero;

    @Column(nullable = false)
    private String colonia;

    @Column(nullable = false)
    private String municipio;

    @Column(nullable = false)
    private String estado;

    @Column(name = "codigo_postal", nullable = false)
    private String codigoPostal;

    @Column(nullable = false)
    private String pais;

    @Column(name = "cp_catalogo_id")
    private Long cpCatalogoId;

    protected Direccion() {
        // JPA
    }

    public Direccion(Persona persona, String calle, String numero, String colonia, String municipio,
                      String estado, String codigoPostal, String pais, Long cpCatalogoId) {
        this.persona = persona;
        this.calle = calle;
        this.numero = numero;
        this.colonia = colonia;
        this.municipio = municipio;
        this.estado = estado;
        this.codigoPostal = codigoPostal;
        this.pais = pais;
        this.cpCatalogoId = cpCatalogoId;
    }

    public void actualizar(String calle, String numero, String colonia, String municipio,
                            String estado, String codigoPostal, String pais, Long cpCatalogoId) {
        this.calle = calle;
        this.numero = numero;
        this.colonia = colonia;
        this.municipio = municipio;
        this.estado = estado;
        this.codigoPostal = codigoPostal;
        this.pais = pais;
        this.cpCatalogoId = cpCatalogoId;
    }

    public UUID getId() {
        return id;
    }

    public Persona getPersona() {
        return persona;
    }

    public String getCalle() {
        return calle;
    }

    public String getNumero() {
        return numero;
    }

    public String getColonia() {
        return colonia;
    }

    public String getMunicipio() {
        return municipio;
    }

    public String getEstado() {
        return estado;
    }

    public String getCodigoPostal() {
        return codigoPostal;
    }

    public String getPais() {
        return pais;
    }

    public Long getCpCatalogoId() {
        return cpCatalogoId;
    }
}
