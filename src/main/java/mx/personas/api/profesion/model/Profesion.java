package mx.personas.api.profesion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import mx.personas.api.common.audit.Auditable;

/**
 * Entrada del catalogo controlado de profesiones (FR-001). El nombre es
 * inmutable tras crearse (unicidad insensible a mayusculas/acentos via indice
 * de base de datos, ver V7); solo la descripcion y el estado activo se
 * pueden editar.
 */
@Entity
@Table(name = "profesion")
public class Profesion extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column
    private String descripcion;

    @Column(nullable = false)
    private boolean activo = true;

    protected Profesion() {
        // JPA
    }

    public Profesion(String nombre, String descripcion) {
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public void editarDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public void desactivar() {
        this.activo = false;
    }

    public void reactivar() {
        this.activo = true;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public boolean isActivo() {
        return activo;
    }
}
