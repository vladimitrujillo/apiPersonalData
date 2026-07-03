package mx.personas.api.common.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Habilita el abstraction de cache de Spring (backend en memoria, spring.cache.type=simple
 * en application.yml) para las respuestas del catalogo de codigos postales (FR-018,
 * research.md #7).
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
