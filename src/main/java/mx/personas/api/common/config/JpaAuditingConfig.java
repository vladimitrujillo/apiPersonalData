package mx.personas.api.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * El DateTimeProvider por defecto de Spring Data (CurrentDateTimeProvider) entrega
 * LocalDateTime; los campos @CreatedDate/@LastModifiedDate de Auditable son
 * OffsetDateTime, y esa conversion no esta soportada, causando un
 * InvalidDataAccessApiUsageException en cada persist/update de una entidad Auditable.
 * Se provee un DateTimeProvider propio que entrega directamente OffsetDateTime.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "securityAuditorAware", dateTimeProviderRef = "offsetDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public DateTimeProvider offsetDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now());
    }
}
