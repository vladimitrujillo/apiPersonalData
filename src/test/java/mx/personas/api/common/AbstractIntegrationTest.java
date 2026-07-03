package mx.personas.api.common;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Patron de "contenedor singleton": el contenedor se inicia manualmente en un bloque
 * estatico y NUNCA se detiene explicitamente (lo limpia Ryuk al terminar la JVM). A
 * proposito NO se usan las anotaciones @Testcontainers/@Container, ya que esas hacen que
 * JUnit detenga el contenedor en el afterAll() de CADA clase de prueba, rompiendo el
 * intento de compartir un solo contenedor entre PersonaLifecycleIT, PersonaListIT,
 * PersonaRepositoryIT, CodigoPostalIT, etc. (research.md #9).
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("personas_test")
                    .withUsername("app")
                    .withPassword("app");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
