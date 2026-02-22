package com.patternforge;

import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER = new PostgreSQLContainer<>("pgvector/pgvector:pg14")
            .withDatabaseName("patternforge")
            .withUsername("postgres")
            .withPassword("postgres")
            .withInitScript("db/schema.sql");

    @Autowired
    protected DSLContext dsl;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL_CONTAINER::getPassword);
    }

    @BeforeAll
    static void validateContainer() {
        if (!POSTGRESQL_CONTAINER.isRunning()) {
            throw new IllegalStateException("PostgreSQL container failed to start");
        }
    }

    @AfterAll
    static void cleanUp(@Autowired DataSource dataSource) {
        try {
            Connection connection = dataSource.getConnection();
            connection.close();
        } catch (SQLException e) {
            log.error("connection was not closed", e);
        }
    }

    @AfterEach
    void cleanDatabase() {
        if (Objects.nonNull(dsl)) {
            dsl.execute("TRUNCATE TABLE pattern_usage CASCADE");
            dsl.execute("TRUNCATE TABLE pattern_promotions CASCADE");
            dsl.execute("TRUNCATE TABLE pattern_quality_gates CASCADE");
            dsl.execute("TRUNCATE TABLE conversational_patterns CASCADE");
            dsl.execute("TRUNCATE TABLE patterns CASCADE");
            dsl.execute("TRUNCATE TABLE projects CASCADE");
        }
    }
}
