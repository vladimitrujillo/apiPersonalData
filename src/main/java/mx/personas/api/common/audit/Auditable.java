package mx.personas.api.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Auditoria "quien/cuando" gestionada declarativamente por Spring Data JPA Auditing
 * (research.md §2 de specs/003-auditoria-personas). creadoPor/actualizadoPor son
 * nullable: filas creadas antes de este feature no tienen autor conocido.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class Auditable {

    @CreatedBy
    @Column(name = "creado_por")
    private UUID creadoPor;

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @LastModifiedBy
    @Column(name = "actualizado_por")
    private UUID actualizadoPor;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getCreadoPor() {
        return creadoPor;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public UUID getActualizadoPor() {
        return actualizadoPor;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
