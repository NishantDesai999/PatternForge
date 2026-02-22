package com.patternforge.retrieval;

import com.patternforge.retrieval.model.TaskContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TaskAnalyzer — keyword-based task classification.
 */
class TaskAnalyzerTest {

    private TaskAnalyzer taskAnalyzer;

    @BeforeEach
    void setUp() {
        taskAnalyzer = new TaskAnalyzer();
    }

    // ==================== Task Type Inference ====================

    @Test
    void shouldInferFixTestWhenDescriptionContainsTest() {
        TaskContext context = taskAnalyzer.analyze("fix failing test in UserServiceTest", "java", null);
        assertThat(context.getTaskType()).isEqualTo("fix_test");
    }

    @Test
    void shouldInferAddEndpointWhenDescriptionContainsEndpoint() {
        TaskContext context = taskAnalyzer.analyze("add new REST endpoint for user registration", "java", null);
        assertThat(context.getTaskType()).isEqualTo("add_endpoint");
    }

    @Test
    void shouldInferAddEndpointWhenDescriptionContainsController() {
        TaskContext context = taskAnalyzer.analyze("create controller method for payment", "java", null);
        assertThat(context.getTaskType()).isEqualTo("add_endpoint");
    }

    @Test
    void shouldInferRefactorWhenDescriptionContainsRefactor() {
        TaskContext context = taskAnalyzer.analyze("refactor the service layer to use patterns", "java", null);
        assertThat(context.getTaskType()).isEqualTo("refactor");
    }

    @Test
    void shouldInferAddFeatureWhenDescriptionContainsAdd() {
        TaskContext context = taskAnalyzer.analyze("add user authentication feature", "java", null);
        assertThat(context.getTaskType()).isEqualTo("add_feature");
    }

    @Test
    void shouldInferAddFeatureWhenDescriptionContainsCreate() {
        TaskContext context = taskAnalyzer.analyze("create new payment service", "java", null);
        assertThat(context.getTaskType()).isEqualTo("add_feature");
    }

    @Test
    void shouldInferAddFeatureWhenDescriptionContainsImplement() {
        TaskContext context = taskAnalyzer.analyze("implement the caching layer", "java", null);
        assertThat(context.getTaskType()).isEqualTo("add_feature");
    }

    @Test
    void shouldInferFixBugWhenDescriptionContainsFix() {
        TaskContext context = taskAnalyzer.analyze("fix the null pointer exception in service", "java", null);
        assertThat(context.getTaskType()).isEqualTo("fix_bug");
    }

    @Test
    void shouldInferFixBugWhenDescriptionContainsBug() {
        TaskContext context = taskAnalyzer.analyze("resolve the bug in payment processing", "java", null);
        assertThat(context.getTaskType()).isEqualTo("fix_bug");
    }

    @Test
    void shouldInferGeneralWhenNoKeywordMatches() {
        TaskContext context = taskAnalyzer.analyze("update the documentation", "java", null);
        assertThat(context.getTaskType()).isEqualTo("general");
    }

    @Test
    void shouldPrioritizeTestOverOtherKeywords() {
        // "test" keyword takes priority even if "add" also present
        TaskContext context = taskAnalyzer.analyze("add test cases for the controller", "java", null);
        assertThat(context.getTaskType()).isEqualTo("fix_test");
    }

    @Test
    void shouldHandleCaseInsensitiveMatching() {
        TaskContext context = taskAnalyzer.analyze("Fix the ENDPOINT configuration", "java", null);
        // "Fix" doesn't trigger fix_bug first — "endpoint" in uppercase still matches add_endpoint
        // because the description is lowercased before processing
        assertThat(context.getTaskType()).isEqualTo("add_endpoint");
    }

    // ==================== Component Extraction ====================

    @Test
    void shouldExtractTestComponent() {
        TaskContext context = taskAnalyzer.analyze("fix test setup", "java", null);
        assertThat(context.getComponents()).contains("test");
    }

    @Test
    void shouldExtractControllerComponent() {
        TaskContext context = taskAnalyzer.analyze("update endpoint in UserController", "java", null);
        assertThat(context.getComponents()).contains("controller");
    }

    @Test
    void shouldExtractControllerComponentForEndpointKeyword() {
        TaskContext context = taskAnalyzer.analyze("add REST endpoint", "java", null);
        assertThat(context.getComponents()).contains("controller");
    }

    @Test
    void shouldExtractServiceComponent() {
        TaskContext context = taskAnalyzer.analyze("implement service layer logic", "java", null);
        assertThat(context.getComponents()).contains("service");
    }

    @Test
    void shouldExtractRepositoryComponent() {
        TaskContext context = taskAnalyzer.analyze("fix query in repository class", "java", null);
        assertThat(context.getComponents()).contains("repository");
    }

    @Test
    void shouldExtractRepositoryComponentForDaoKeyword() {
        TaskContext context = taskAnalyzer.analyze("update DAO access object", "java", null);
        assertThat(context.getComponents()).contains("repository");
    }

    @Test
    void shouldExtractModelComponent() {
        TaskContext context = taskAnalyzer.analyze("add new model class", "java", null);
        assertThat(context.getComponents()).contains("model");
    }

    @Test
    void shouldExtractModelComponentForEntityKeyword() {
        TaskContext context = taskAnalyzer.analyze("create entity mapping", "java", null);
        assertThat(context.getComponents()).contains("model");
    }

    @Test
    void shouldExtractModelComponentForDtoKeyword() {
        TaskContext context = taskAnalyzer.analyze("create request dto class", "java", null);
        assertThat(context.getComponents()).contains("model");
    }

    @Test
    void shouldExtractMultipleComponents() {
        TaskContext context = taskAnalyzer.analyze("add test for service and repository", "java", null);
        assertThat(context.getComponents()).containsExactlyInAnyOrder("test", "service", "repository");
    }

    @Test
    void shouldReturnEmptyComponentsWhenNoKeywordsMatch() {
        TaskContext context = taskAnalyzer.analyze("update the documentation", "java", null);
        assertThat(context.getComponents()).isEmpty();
    }

    // ==================== Concern Extraction ====================

    @Test
    void shouldExtractImportsConcern() {
        TaskContext context = taskAnalyzer.analyze("fix import statements in the file", "java", null);
        assertThat(context.getConcerns()).contains("imports");
    }

    @Test
    void shouldExtractValidationConcern() {
        TaskContext context = taskAnalyzer.analyze("add validation for user input", "java", null);
        assertThat(context.getConcerns()).contains("validation");
    }

    @Test
    void shouldExtractValidationConcernForValidateKeyword() {
        TaskContext context = taskAnalyzer.analyze("validate the request parameters", "java", null);
        assertThat(context.getConcerns()).contains("validation");
    }

    @Test
    void shouldExtractErrorHandlingConcernForError() {
        TaskContext context = taskAnalyzer.analyze("handle error in the payment flow", "java", null);
        assertThat(context.getConcerns()).contains("error_handling");
    }

    @Test
    void shouldExtractErrorHandlingConcernForException() {
        TaskContext context = taskAnalyzer.analyze("catch the exception properly", "java", null);
        assertThat(context.getConcerns()).contains("error_handling");
    }

    @Test
    void shouldExtractLoggingConcern() {
        TaskContext context = taskAnalyzer.analyze("add proper logging to service", "java", null);
        assertThat(context.getConcerns()).contains("logging");
    }

    @Test
    void shouldExtractSecurityConcern() {
        TaskContext context = taskAnalyzer.analyze("add security checks for endpoint", "java", null);
        assertThat(context.getConcerns()).contains("security");
    }

    @Test
    void shouldExtractSecurityConcernForAuthenticationKeyword() {
        TaskContext context = taskAnalyzer.analyze("implement user authentication", "java", null);
        assertThat(context.getConcerns()).contains("security");
    }

    @Test
    void shouldExtractPerformanceConcern() {
        TaskContext context = taskAnalyzer.analyze("improve query performance", "java", null);
        assertThat(context.getConcerns()).contains("performance");
    }

    @Test
    void shouldExtractPerformanceConcernForOptimizeKeyword() {
        TaskContext context = taskAnalyzer.analyze("optimize database queries", "java", null);
        assertThat(context.getConcerns()).contains("performance");
    }

    @Test
    void shouldExtractMultipleConcerns() {
        TaskContext context = taskAnalyzer.analyze("add validation and error handling with logging", "java", null);
        assertThat(context.getConcerns()).containsExactlyInAnyOrder("validation", "error_handling", "logging");
    }

    // ==================== Null/Blank Handling ====================

    @Test
    void shouldReturnEmptyContextWhenDescriptionIsNull() {
        TaskContext context = taskAnalyzer.analyze(null, "java", null);
        assertThat(context.getTaskType()).isEqualTo("general");
        assertThat(context.getComponents()).isEmpty();
        assertThat(context.getConcerns()).isEmpty();
        assertThat(context.getDescription()).isEqualTo("");
    }

    @Test
    void shouldReturnEmptyContextWhenDescriptionIsBlank() {
        TaskContext context = taskAnalyzer.analyze("   ", "java", null);
        assertThat(context.getTaskType()).isEqualTo("general");
        assertThat(context.getComponents()).isEmpty();
        assertThat(context.getConcerns()).isEmpty();
    }

    @Test
    void shouldReturnEmptyContextWhenDescriptionIsEmpty() {
        TaskContext context = taskAnalyzer.analyze("", "java", null);
        assertThat(context.getTaskType()).isEqualTo("general");
        assertThat(context.getComponents()).isEmpty();
    }

    // ==================== Language and Framework Propagation ====================

    @Test
    void shouldPreserveLanguageInContext() {
        TaskContext context = taskAnalyzer.analyze("add feature", "python", null);
        assertThat(context.getLanguage()).isEqualTo("python");
    }

    @Test
    void shouldPreserveFrameworkInContext() {
        TaskContext context = taskAnalyzer.analyze("add feature", "java", "spring");
        assertThat(context.getFramework()).isEqualTo("spring");
    }

    @Test
    void shouldPreserveDescriptionInContext() {
        String description = "fix the null pointer exception";
        TaskContext context = taskAnalyzer.analyze(description, "java", null);
        assertThat(context.getDescription()).isEqualTo(description);
    }

    @Test
    void shouldAllowNullLanguage() {
        TaskContext context = taskAnalyzer.analyze("add feature", null, null);
        assertThat(context.getLanguage()).isNull();
    }

    @Test
    void shouldAllowNullFramework() {
        TaskContext context = taskAnalyzer.analyze("add feature", "java", null);
        assertThat(context.getFramework()).isNull();
    }
}
