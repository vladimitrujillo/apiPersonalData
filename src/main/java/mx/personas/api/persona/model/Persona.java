package mx.personas.api.persona.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import mx.personas.api.common.audit.Auditable;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "persona")
public class Persona extends Auditable {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String nombres;

    @Column(nullable = false)
    private String apellidos;

    @Column(name = "fecha_nacimiento", nullable = false)
    private LocalDate fechaNacimiento;

    @Column(nullable = false)
    private String sexo;

    @Column(nullable = false)
    private String curp;

    @Column(nullable = false)
    private String rfc;

    @Column(nullable = false)
    private String correo;

    @Column(nullable = false)
    private String telefono;

    @Column(nullable = false)
    private boolean activo = true;

    protected Persona() {
        // JPA
    }

    public Persona(String nombres, String apellidos, LocalDate fechaNacimiento, String sexo,
                    String curp, String rfc, String correo, String telefono) {
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.fechaNacimiento = fechaNacimiento;
        this.sexo = sexo;
        this.curp = curp;
        this.rfc = rfc;
        this.correo = correo;
        this.telefono = telefono;
    }

    public void eliminarLogicamente() {
        this.activo = false;
    }

    public void restaurar() {
        this.activo = true;
    }

    public UUID getId() {
        return id;
    }

    public String getNombres() {
        return nombres;
    }

    public void setNombres(String nombres) {
        this.nombres = nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public void setApellidos(String apellidos) {
        this.apellidos = apellidos;
    }

    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public void setFechaNacimiento(LocalDate fechaNacimiento) {
        this.fechaNacimiento = fechaNacimiento;
    }

    public String getSexo() {
        return sexo;
    }

    public void setSexo(String sexo) {
        this.sexo = sexo;
    }

    public String getCurp() {
        return curp;
    }

    public void setCurp(String curp) {
        this.curp = curp;
    }

    public String getRfc() {
        return rfc;
    }

    public void setRfc(String rfc) {
        this.rfc = rfc;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public boolean isActivo() {
        return activo;
    }
}
