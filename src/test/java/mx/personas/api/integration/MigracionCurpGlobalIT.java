package mx.personas.api.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Constitution (Principio III / plan.md § Constitution Check): la migracion V4 DEBE
 * fallar explicitamente si existen CURP duplicados entre cualquier combinacion de
 * registros (activos y/o eliminados). No extiende AbstractIntegrationTest a proposito:
 * ese arranca el contexto completo de Spring y aplica TODAS las migraciones
 * automaticamente; aqui se necesita control manual de Flyway para aplicar solo hasta V3,
 * sembrar el duplicado, y luego intentar V4 por separado.
 */
class MigracionCurpGlobalIT {

    @Test
    @Tag("integration")
    void migracionV4FallaSiExistenCurpDuplicadosEntreActivosYEliminados() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("personas_migracion_test")
                .withUsername("app")
                .withPassword("app")) {
            postgres.start();

            String url = postgres.getJdbcUrl();
            String usuario = postgres.getUsername();
            String password = postgres.getPassword();

            Flyway.configure()
                    .dataSource(url, usuario, password)
                    .locations("classpath:db/migration")
                    .target("3")
                    .load()
                    .migrate();

            String curpDuplicada = "DUPL900101HDFRRN01";
            try (Connection conexion = DriverManager.getConnection(url, usuario, password);
                 Statement statement = conexion.createStatement()) {
                statement.execute("""
                        INSERT INTO persona (nombres, apellidos, fecha_nacimiento, sexo, curp, rfc, correo,
                                              telefono, activo)
                        VALUES ('Uno', 'Prueba', '1990-01-01', 'F', '%s', 'DUPL900101AB1', 'uno@example.com',
                                '5500000001', true)
                        """.formatted(curpDuplicada));
                statement.execute("""
                        INSERT INTO persona (nombres, apellidos, fecha_nacimiento, sexo, curp, rfc, correo,
                                              telefono, activo)
                        VALUES ('Dos', 'Prueba', '1990-01-01', 'F', '%s', 'DUPL900101AB2', 'dos@example.com',
                                '5500000002', false)
                        """.formatted(curpDuplicada));
            }

            Flyway flywayHastaV4 = Flyway.configure()
                    .dataSource(url, usuario, password)
                    .locations("classpath:db/migration")
                    .load();

            assertThatThrownBy(flywayHastaV4::migrate)
                    .hasMessageContaining("CURP duplicados");
        }
    }
}
