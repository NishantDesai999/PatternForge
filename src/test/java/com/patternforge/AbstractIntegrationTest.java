package com.patternforge;

import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Objects;

/**
 * Abstract base class for integration tests using Testcontainers with PostgreSQL 14.
 * Provides automatic database setup, cleanup, and shared DSLContext access.
 * All integration tests should extend this class.
 */
@SpringBootTest
@Testcontainers
public abstract class AbstractIntegrationTest {
    
    @Container
    protected static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER = 
        new PostgreSQLContainer<>("pgvector/pgvector:pg14")
            .withDatabaseName("patternforge_test")
            .withUsername("test_user")
            .withPassword("test_password")
            .withInitScript("db/schema.sql");
    
    @Autowired
    protected DSLContext dsl;
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL_CONTAINER::getPassword);
        registry.add("spring.jooq.sql-dialect", () -> "POSTGRES");
    }
    
    @BeforeAll
    static void validateContainer() {
        if (!POSTGRESQL_CONTAINER.isRunning()) {
            throw new IllegalStateException("PostgreSQL container failed to start");
        }
    }
    
    @AfterEach
    void cleanDatabase() {
        if (Objects.nonNull(dsl)) {
            // Clean tables in correct order to respect foreign key constraints
            dsl.execute("TRUNCATE TABLE pattern_usage CASCADE");
            dsl.execute("TRUNCATE TABLE pattern_promotions CASCADE");
            dsl.execute("TRUNCATE TABLE pattern_quality_gates CASCADE");
            dsl.execute("TRUNCATE TABLE conversational_patterns CASCADE");
            dsl.execute("TRUNCATE TABLE patterns CASCADE");
            dsl.execute("TRUNCATE TABLE projects CASCADE");
        }
    }
}
