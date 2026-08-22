package com.mirkamolcode;

import org.flywaydb.core.Flyway;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class SharedPostgresContainer extends PostgreSQLContainer {

    private static final String DATABASE_NAME = "subscription_master_db";

    private static final DockerImageName IMAGE_NAME
            = DockerImageName.parse("postgres:17-alpine");

    private static volatile SharedPostgresContainer sharedPostgresContainer;


    public SharedPostgresContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
        this.withReuse(true)
                .withUsername("mirkamol")
                .withDatabaseName(DATABASE_NAME)
                .withPassword("password");
    }

    public static SharedPostgresContainer getInstance() {
        if(sharedPostgresContainer == null) {
            synchronized (SharedPostgresContainer.class) {
                sharedPostgresContainer = new SharedPostgresContainer(
                        IMAGE_NAME
                );
                sharedPostgresContainer.start();
                ensureDatabaseExists(sharedPostgresContainer);
                Flyway flyway = Flyway.configure()
                        .dataSource(
                                sharedPostgresContainer.getJdbcUrl(),
                                sharedPostgresContainer.getUsername(),
                                sharedPostgresContainer.getPassword()
                        )
                        .load();
                flyway.migrate();
                System.out.println("flyway applied migrations");
            }
        }
        return sharedPostgresContainer;
    }

    /**
     * A reusable Testcontainers instance may have been created before the
     * database name was configured (or with a different name). PostgreSQL
     * only creates POSTGRES_DB during initialisation, so the configured JDBC
     * URL can otherwise point at a database that is not present.
     */
    private static void ensureDatabaseExists(SharedPostgresContainer container) {
        String adminUrl = String.format(
                "jdbc:postgresql://%s:%d/postgres",
                container.getHost(),
                container.getMappedPort(5432)
        );

        try (Connection connection = DriverManager.getConnection(
                adminUrl, container.getUsername(), container.getPassword());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT 1 FROM pg_database WHERE datname = '" + DATABASE_NAME + "'")) {

            if (!resultSet.next()) {
                try (Statement createStatement = connection.createStatement()) {
                    createStatement.executeUpdate("CREATE DATABASE \"" + DATABASE_NAME + "\"");
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not create or verify PostgreSQL database '" + DATABASE_NAME + "'",
                    exception
            );
        }
    }
}
