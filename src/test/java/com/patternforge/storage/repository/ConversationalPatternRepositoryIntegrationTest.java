package com.patternforge.storage.repository;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.patternforge.AbstractIntegrationTest;
import com.patternforge.jooq.tables.records.ConversationalPatternsRecord;
import com.patternforge.jooq.tables.records.ProjectsRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.patternforge.jooq.Tables.CONVERSATIONAL_PATTERNS;
import static com.patternforge.jooq.Tables.PROJECTS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ConversationalPatternRepository using Testcontainers with PostgreSQL 14.
 * Tests follow AAA pattern with shouldDoSomething naming convention.
 * No mocking - uses real database operations.
 */
class ConversationalPatternRepositoryIntegrationTest extends AbstractIntegrationTest {
    
    @Autowired
    private ConversationalPatternRepository repository;
    
    @Test
    void shouldFindAllConversationalPatterns() {
        // Arrange
        UUID projectId = createTestProject("test-project-1");
        createTestPattern("pattern1", "test-conv-1", projectId);
        createTestPattern("pattern2", "test-conv-2", projectId);
        
        // Act
        List<ConversationalPatternsRecord> result = repository.findAll();
        
        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).extracting(ConversationalPatternsRecord::getDescription)
            .containsExactlyInAnyOrder("pattern1", "pattern2");
    }
    
    @Test
    void shouldReturnEmptyListWhenNoPatterns() {
        // Act
        List<ConversationalPatternsRecord> result = repository.findAll();
        
        // Assert
        assertThat(result).isEmpty();
    }
    
    @Test
    void shouldFindPatternsByProjectId() {
        // Arrange
        UUID projectId1 = createTestProject("test-project-1");
        UUID projectId2 = createTestProject("test-project-2");
        createTestPattern("pattern1", "conv-1", projectId1);
        createTestPattern("pattern2", "conv-2", projectId1);
        createTestPattern("pattern3", "conv-3", projectId2);
        
        // Act
        List<ConversationalPatternsRecord> result = repository.findByProjectId(projectId1);
        
        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(record -> record.getProjectId().equals(projectId1));
        assertThat(result).extracting(ConversationalPatternsRecord::getDescription)
            .containsExactlyInAnyOrder("pattern1", "pattern2");
    }
    
    @Test
    void shouldReturnEmptyListWhenNoPatternsByProjectId() {
        // Arrange
        UUID nonExistentProjectId = UUID.randomUUID();
        
        // Act
        List<ConversationalPatternsRecord> result = repository.findByProjectId(nonExistentProjectId);
        
        // Assert
        assertThat(result).isEmpty();
    }
    
    @Test
    void shouldReturnEmptyListWhenFindByProjectIdWithNullInput() {
        // Arrange
        UUID nullProjectId = null;
        
        // Act
        List<ConversationalPatternsRecord> result = repository.findByProjectId(nullProjectId);
        
        // Assert
        assertThat(result).isEmpty();
    }
    
    @Test
    void shouldFindPatternsByConversationId() {
        // Arrange
        UUID projectId = createTestProject("test-project");
        createTestPattern("pattern1", "conv-123", projectId);
        createTestPattern("pattern2", "conv-123", projectId);
        createTestPattern("pattern3", "conv-456", projectId);
        
        // Act
        List<ConversationalPatternsRecord> result = repository.findByConversationId("conv-123");
        
        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(record -> "conv-123".equals(record.getConversationId()));
        assertThat(result).extracting(ConversationalPatternsRecord::getDescription)
            .containsExactlyInAnyOrder("pattern1", "pattern2");
    }
    
    @Test
    void shouldReturnEmptyListWhenNoPatternsByConversationId() {
        // Arrange
        String nonExistentConversationId = "non-existent-conv";
        
        // Act
        List<ConversationalPatternsRecord> result = repository.findByConversationId(nonExistentConversationId);
        
        // Assert
        assertThat(result).isEmpty();
    }
    
    @Test
    void shouldReturnEmptyListWhenFindByConversationIdWithNullInput() {
        // Arrange
        String nullConversationId = null;
        
        // Act
        List<ConversationalPatternsRecord> result = repository.findByConversationId(nullConversationId);
        
        // Assert
        assertThat(result).isEmpty();
    }
    
    @Test
    void shouldReturnEmptyListWhenFindByConversationIdWithBlankInput() {
        // Arrange
        String blankConversationId = "   ";
        
        // Act
        List<ConversationalPatternsRecord> result = repository.findByConversationId(blankConversationId);
        
        // Assert
        assertThat(result).isEmpty();
    }
    
    @Test
    void shouldReturnEmptyListWhenFindByConversationIdWithEmptyInput() {
        // Arrange
        String emptyConversationId = "";
        
        // Act
        List<ConversationalPatternsRecord> result = repository.findByConversationId(emptyConversationId);
        
        // Assert
        assertThat(result).isEmpty();
    }
    
    @Test
    void shouldFindPendingPromotionPatterns() {
        // Arrange
        UUID projectId = createTestProject("test-project");
        createTestPatternWithPromotionCount("pattern1", "conv-1", projectId, 5, false);
        createTestPatternWithPromotionCount("pattern2", "conv-2", projectId, 7, false);
        createTestPatternWithPromotionCount("pattern3", "conv-3", projectId, 10, false);
        createTestPatternWithPromotionCount("pattern4", "conv-4", projectId, 3, false); // Not eligible
        createTestPatternWithPromotionCount("pattern5", "conv-5", projectId, 8, true); // Already promoted
        
        // Act
        List<ConversationalPatternsRecord> result = repository.findPendingPromotion();
        
        // Assert
        assertThat(result).hasSize(3);
        assertThat(result).allMatch(record -> record.getPromotionCount() >= 5);
        assertThat(result).allMatch(record -> !record.getIsProjectStandard());
        assertThat(result).extracting(ConversationalPatternsRecord::getDescription)
            .containsExactlyInAnyOrder("pattern1", "pattern2", "pattern3");
    }
    
    @Test
    void shouldReturnEmptyListWhenNoPendingPromotionPatterns() {
        // Arrange
        UUID projectId = createTestProject("test-project");
        createTestPatternWithPromotionCount("pattern1", "conv-1", projectId, 2, false);
        createTestPatternWithPromotionCount("pattern2", "conv-2", projectId, 5, true); // Already promoted
        
        // Act
        List<ConversationalPatternsRecord> result = repository.findPendingPromotion();
        
        // Assert
        assertThat(result).isEmpty();
    }
    
    @Test
    void shouldInsertNewPatternWhenIdIsNull() {
        // Arrange
        UUID projectId = createTestProject("test-project");
        ConversationalPatternsRecord newRecord = createRecordWithoutId("new pattern", "conv-new", projectId);
        
        // Act
        ConversationalPatternsRecord savedRecord = repository.save(newRecord);
        
        // Assert
        assertThat(savedRecord).isNotNull();
        assertThat(savedRecord.getId()).isNotNull();
        assertThat(savedRecord.getDescription()).isEqualTo("new pattern");
        assertThat(savedRecord.getConversationId()).isEqualTo("conv-new");
        assertThat(savedRecord.getProjectId()).isEqualTo(projectId);
        
        // Verify in database
        List<ConversationalPatternsRecord> allPatterns = repository.findAll();
        assertThat(allPatterns).hasSize(1);
        assertThat(allPatterns.get(0).getId()).isEqualTo(savedRecord.getId());
    }
    
    @Test
    void shouldUpdateExistingPatternWhenIdIsNotNull() {
        // Arrange
        UUID projectId = createTestProject("test-project");
        UUID patternId = createTestPattern("original description", "conv-original", projectId);
        
        // Fetch the record and modify it
        ConversationalPatternsRecord recordToUpdate = dsl.selectFrom(CONVERSATIONAL_PATTERNS)
            .where(CONVERSATIONAL_PATTERNS.ID.eq(patternId))
            .fetchOne();
        assertThat(recordToUpdate).isNotNull();
        
        recordToUpdate.setDescription("updated description");
        recordToUpdate.setRationale("updated rationale");
        
        // Act
        ConversationalPatternsRecord updatedRecord = repository.save(recordToUpdate);
        
        // Assert
        assertThat(updatedRecord).isNotNull();
        assertThat(updatedRecord.getId()).isEqualTo(patternId);
        assertThat(updatedRecord.getDescription()).isEqualTo("updated description");
        assertThat(updatedRecord.getRationale()).isEqualTo("updated rationale");
        
        // Verify in database
        ConversationalPatternsRecord dbRecord = dsl.selectFrom(CONVERSATIONAL_PATTERNS)
            .where(CONVERSATIONAL_PATTERNS.ID.eq(patternId))
            .fetchOne();
        assertThat(dbRecord).isNotNull();
        assertThat(dbRecord.getDescription()).isEqualTo("updated description");
        assertThat(dbRecord.getRationale()).isEqualTo("updated rationale");
    }
    
    @Test
    void shouldIncrementPromotionCountSuccessfully() {
        // Arrange
        UUID projectId = createTestProject("test-project");
        UUID patternId = createTestPatternWithPromotionCount("pattern", "conv-1", projectId, 3, false);
        
        // Act
        repository.incrementPromotionCount(patternId);
        
        // Assert
        ConversationalPatternsRecord updatedRecord = dsl.selectFrom(CONVERSATIONAL_PATTERNS)
            .where(CONVERSATIONAL_PATTERNS.ID.eq(patternId))
            .fetchOne();
        assertThat(updatedRecord).isNotNull();
        assertThat(updatedRecord.getPromotionCount()).isEqualTo(4);
    }
    
    @Test
    void shouldIncrementPromotionCountMultipleTimes() {
        // Arrange
        UUID projectId = createTestProject("test-project");
        UUID patternId = createTestPatternWithPromotionCount("pattern", "conv-1", projectId, 1, false);
        
        // Act
        repository.incrementPromotionCount(patternId);
        repository.incrementPromotionCount(patternId);
        repository.incrementPromotionCount(patternId);
        
        // Assert
        ConversationalPatternsRecord updatedRecord = dsl.selectFrom(CONVERSATIONAL_PATTERNS)
            .where(CONVERSATIONAL_PATTERNS.ID.eq(patternId))
            .fetchOne();
        assertThat(updatedRecord).isNotNull();
        assertThat(updatedRecord.getPromotionCount()).isEqualTo(4);
    }
    
    @Test
    void shouldDoNothingWhenIncrementPromotionCountWithNullId() {
        // Arrange
        UUID nullId = null;
        
        // Act
        repository.incrementPromotionCount(nullId);
        
        // Assert - no exception thrown, operation completes successfully
        List<ConversationalPatternsRecord> allPatterns = repository.findAll();
        assertThat(allPatterns).isEmpty();
    }
    
    @Test
    void shouldPromoteToProjectStandardSuccessfully() {
        // Arrange
        UUID projectId = createTestProject("test-project");
        UUID patternId = createTestPatternWithPromotionCount("pattern", "conv-1", projectId, 5, false);
        
        // Act
        repository.promoteToProjectStandard(patternId);
        
        // Assert
        ConversationalPatternsRecord promotedRecord = dsl.selectFrom(CONVERSATIONAL_PATTERNS)
            .where(CONVERSATIONAL_PATTERNS.ID.eq(patternId))
            .fetchOne();
        assertThat(promotedRecord).isNotNull();
        assertThat(promotedRecord.getIsProjectStandard()).isTrue();
    }
    
    @Test
    void shouldNotAffectOtherPatternsWhenPromotingOne() {
        // Arrange
        UUID projectId = createTestProject("test-project");
        UUID patternId1 = createTestPatternWithPromotionCount("pattern1", "conv-1", projectId, 5, false);
        UUID patternId2 = createTestPatternWithPromotionCount("pattern2", "conv-2", projectId, 5, false);
        
        // Act
        repository.promoteToProjectStandard(patternId1);
        
        // Assert
        ConversationalPatternsRecord promoted = dsl.selectFrom(CONVERSATIONAL_PATTERNS)
            .where(CONVERSATIONAL_PATTERNS.ID.eq(patternId1))
            .fetchOne();
        assertThat(promoted).isNotNull();
        assertThat(promoted.getIsProjectStandard()).isTrue();
        
        ConversationalPatternsRecord notPromoted = dsl.selectFrom(CONVERSATIONAL_PATTERNS)
            .where(CONVERSATIONAL_PATTERNS.ID.eq(patternId2))
            .fetchOne();
        assertThat(notPromoted).isNotNull();
        assertThat(notPromoted.getIsProjectStandard()).isFalse();
    }
    
    @Test
    void shouldDoNothingWhenPromoteToProjectStandardWithNullId() {
        // Arrange
        UUID nullId = null;
        
        // Act
        repository.promoteToProjectStandard(nullId);
        
        // Assert - no exception thrown, operation completes successfully
        List<ConversationalPatternsRecord> allPatterns = repository.findAll();
        assertThat(allPatterns).isEmpty();
    }
    
    @Test
    void shouldHandleMultiplePatternsSavedSequentially() {
        // Arrange
        UUID projectId = createTestProject("test-project");
        ConversationalPatternsRecord record1 = createRecordWithoutId("pattern1", "conv-1", projectId);
        ConversationalPatternsRecord record2 = createRecordWithoutId("pattern2", "conv-2", projectId);
        ConversationalPatternsRecord record3 = createRecordWithoutId("pattern3", "conv-3", projectId);
        
        // Act
        repository.save(record1);
        repository.save(record2);
        repository.save(record3);
        
        // Assert
        List<ConversationalPatternsRecord> allPatterns = repository.findAll();
        assertThat(allPatterns).hasSize(3);
        assertThat(allPatterns).extracting(ConversationalPatternsRecord::getDescription)
            .containsExactlyInAnyOrder("pattern1", "pattern2", "pattern3");
    }
    
    // Helper methods
    
    private UUID createTestProject(String projectName) {
        UUID projectId = UUID.randomUUID();
        ProjectsRecord project = dsl.newRecord(PROJECTS);
        project.setProjectId(projectId);
        project.setProjectName(projectName);
        project.setProjectPath("/test/path/" + projectName);
        dsl.insertInto(PROJECTS)
            .set(project)
            .execute();
        return projectId;
    }
    
    private UUID createTestPattern(String description, String conversationId, UUID projectId) {
        return createTestPatternWithPromotionCount(description, conversationId, projectId, 1, false);
    }
    
    private UUID createTestPatternWithPromotionCount(String description, String conversationId, 
                                                     UUID projectId, Integer promotionCount, 
                                                     Boolean isProjectStandard) {
        ConversationalPatternsRecord record = dsl.newRecord(CONVERSATIONAL_PATTERNS);
        UUID patternId = UUID.randomUUID();
        record.setId(patternId);
        record.setDescription(description);
        record.setConversationId(conversationId);
        record.setProjectId(projectId);
        record.setSource("user_explicit");
        record.setConfidence(0.95);
        record.setPromotionCount(promotionCount);
        record.setIsProjectStandard(isProjectStandard);
        record.setCodeExample("// Test code example");
        record.setRationale("Test rationale");
        
        dsl.insertInto(CONVERSATIONAL_PATTERNS)
            .set(record)
            .execute();
        
        return patternId;
    }
    
    private ConversationalPatternsRecord createRecordWithoutId(String description, 
                                                                String conversationId, 
                                                                UUID projectId) {
        ConversationalPatternsRecord record = dsl.newRecord(CONVERSATIONAL_PATTERNS);
        record.setId(null); // Explicitly set to null to test insert path
        record.setDescription(description);
        record.setConversationId(conversationId);
        record.setProjectId(projectId);
        record.setSource("user_explicit");
        record.setConfidence(0.90);
        record.setPromotionCount(1);
        record.setIsProjectStandard(false);
        record.setCodeExample("// Code example");
        record.setRationale("Rationale");
        return record;
    }
}
