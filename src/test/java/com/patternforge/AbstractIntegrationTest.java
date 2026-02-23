package com.patternforge;

import com.patternforge.util.CleanUpUtil;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests using Testcontainers with PostgreSQL + pgvector.
 * 
 * <p>Uses a static singleton container that starts once and is shared across all test classes,
 * significantly improving test execution speed (2 hours → 5-10 minutes).
 * 
 * <p>Database is cleaned after each test using {@link CleanUpUtil} to ensure test isolation.
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    /**
     * Singleton PostgreSQL container with pgvector extension.
     * Starts once per JVM and shared across ALL test classes.
     */
    static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER;

    static {
        POSTGRESQL_CONTAINER = new PostgreSQLContainer<>("pgvector/pgvector:pg14")
                .withDatabaseName("patternforge")
                .withUsername("test")
                .withPassword("test")
                .withInitScript("db/schema.sql")
                .withReuse(false);  // Disable reuse for clean state per Maven run
        
        POSTGRESQL_CONTAINER.start();
        
        log.info("PostgreSQL container started: {}", POSTGRESQL_CONTAINER.getJdbcUrl());
    }

    @Autowired
    protected DSLContext dsl;

    @Autowired
    protected CleanUpUtil cleanUpUtil;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL_CONTAINER::getPassword);
    }

    /**
     * Cleans database after each test to ensure test isolation.
     * Truncates all tables and resets sequences to initial state.
     */
    @AfterEach
    void cleanDatabase() {
        cleanUpUtil.cleanupDatabase();
    }
}
