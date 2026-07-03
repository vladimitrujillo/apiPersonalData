package mx.personas.api.codigopostal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Catalogo Nacional de Codigos Postales de SEPOMEX, modelado como tabla plana que refleja
 * la estructura del archivo fuente: una fila por cada combinacion de codigo postal y
 * asentamiento (ver data-model.md, research.md #3).
 */
@Entity
@Table(name = "cp_catalogo")
public class CpCatalogo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_postal", nullable = false)
    private String codigoPostal;

    @Column(nullable = false)
    private String estado;

    @Column(nullable = false)
    private String municipio;

    @Column(nullable = false)
    private String asentamiento;

    @Column(name = "tipo_asentamiento", nullable = false)
    private String tipoAsentamiento;

    @Column(name = "id_asenta_cpcons", nullable = false)
    private String idAsentaCpcons;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected CpCatalogo() {
        // JPA
    }

    public CpCatalogo(String codigoPostal, String estado, String municipio, String asentamiento,
                       String tipoAsentamiento, String idAsentaCpcons) {
        this.codigoPostal = codigoPostal;
        this.estado = estado;
        this.municipio = municipio;
        this.asentamiento = asentamiento;
        this.tipoAsentamiento = tipoAsentamiento;
        this.idAsentaCpcons = idAsentaCpcons;
        OffsetDateTime ahora = OffsetDateTime.now();
        this.createdAt = ahora;
        this.updatedAt = ahora;
    }

    public void actualizarDesde(String estado, String municipio, String asentamiento, String tipoAsentamiento) {
        this.estado = estado;
        this.municipio = municipio;
        this.asentamiento = asentamiento;
        this.tipoAsentamiento = tipoAsentamiento;
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getCodigoPostal() {
        return codigoPostal;
    }

    public String getEstado() {
        return estado;
    }

    public String getMunicipio() {
        return municipio;
    }

    public String getAsentamiento() {
        return asentamiento;
    }

    public String getTipoAsentamiento() {
        return tipoAsentamiento;
    }

    public String getIdAsentaCpcons() {
        return idAsentaCpcons;
    }
}
