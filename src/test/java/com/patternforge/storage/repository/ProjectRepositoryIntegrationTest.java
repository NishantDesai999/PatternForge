package com.patternforge.storage.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.patternforge.AbstractIntegrationTest;
import com.patternforge.jooq.tables.records.ProjectsRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.patternforge.jooq.Tables.PROJECTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for ProjectRepository using Testcontainers with PostgreSQL 14.
 * Tests follow AAA pattern with shouldDoSomething naming convention.
 * No mocking - uses real database operations.
 */
class ProjectRepositoryIntegrationTest extends AbstractIntegrationTest {
    
    @Autowired
    private ProjectRepository repository;
    
    @Test
    void shouldReturnAllProjects() {
        // Arrange
        createTestProject("project1", "/path/to/project1");
        createTestProject("project2", "/path/to/project2");
        createTestProject("project3", "/path/to/project3");
        
        // Act
        List<ProjectsRecord> result = repository.findAll();
        
        // Assert
        assertThat(result).hasSize(3);
        assertThat(result).extracting(ProjectsRecord::getProjectName)
            .containsExactlyInAnyOrder("project1", "project2", "project3");
    }
    
    @Test
    void shouldReturnEmptyListWhenNoProjectsExist() {
        // Act
        List<ProjectsRecord> result = repository.findAll();
        
        // Assert
        assertThat(result).isEmpty();
    }
    
    @Test
    void shouldFindProjectByIdWhenProjectExists() {
        // Arrange
        UUID projectId = createTestProject("test-project", "/path/to/project");
        
        // Act
        Optional<ProjectsRecord> result = repository.findById(projectId);
        
        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getProjectId()).isEqualTo(projectId);
        assertThat(result.get().getProjectName()).isEqualTo("test-project");
        assertThat(result.get().getProjectPath()).isEqualTo("/path/to/project");
    }
    
    @Test
    void shouldReturnEmptyOptionalWhenProjectNotFoundById() {
        // Arrange
        UUID nonExistentProjectId = UUID.randomUUID();
        
        // Act
        Optional<ProjectsRecord> result = repository.findById(nonExistentProjectId);
        
        // Assert
        assertThat(result).isEmpty();
    }
    
    @Test
    void shouldReturnEmptyOptionalWhenFindByIdWithNullInput() {
        // Arrange
        UUID nullProjectId = null;
        
        // Act
        Optional<ProjectsRecord> result = repository.findById(nullProjectId);
        
        // Assert
        assertThat(result).isEmpty();
    }
    
    @Test
    void shouldFindProjectByPathWhenProjectExists() {
        // Arrange
        String projectPath = "/unique/path/to/project";
        UUID projectId = createTestProject("test-project", projectPath);
        
        // Act
        Optional<ProjectsRecord> result = repository.findByPath(projectPath);
        
        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getProjectId()).isEqualTo(projectId);
        assertThat(result.get().getProjectPath()).isEqualTo(projectPath);
    }
    
    @Test
    void shouldReturnEmptyOptionalWhenProjectNotFoundByPath() {
        // Arrange
        String nonExistentPath = "/non/existent/path";
        
        // Act
        Optional<ProjectsRecord> result = repository.findByPath(nonExistentPath);
        
        // Assert
        assertThat(result).isEmpty();
    }
    
    @Test
    void shouldReturnEmptyOptionalWhenFindByPathWithNullInput() {
        // Arrange
        String nullPath = null;
        
        // Act
        Optional<ProjectsRecord> result = repository.findByPath(nullPath);
        
        // Assert
        assertThat(result).isEmpty();
    }
    
    @Test
    void shouldReturnEmptyOptionalWhenFindByPathWithBlankInput() {
        // Arrange
        String blankPath = "   ";
        
        // Act
        Optional<ProjectsRecord> result = repository.findByPath(blankPath);
        
        // Assert
        assertThat(result).isEmpty();
    }
    
    @Test
    void shouldReturnEmptyOptionalWhenFindByPathWithEmptyInput() {
        // Arrange
        String emptyPath = "";
        
        // Act
        Optional<ProjectsRecord> result = repository.findByPath(emptyPath);
        
        // Assert
        assertThat(result).isEmpty();
    }
    
    @Test
    void shouldFindExistingProjectInFindOrCreate() {
        // Arrange
        String projectName = "existing-project";
        String projectPath = "/path/to/existing";
        UUID existingProjectId = createTestProject(projectName, projectPath);
        
        // Act
        ProjectsRecord result = repository.findOrCreate(projectName, projectPath);
        
        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getProjectId()).isEqualTo(existingProjectId);
        assertThat(result.getProjectName()).isEqualTo(projectName);
        assertThat(result.getProjectPath()).isEqualTo(projectPath);
        
        // Verify no duplicate created
        List<ProjectsRecord> allProjects = repository.findAll();
        assertThat(allProjects).hasSize(1);
    }
    
    @Test
    void shouldCreateNewProjectInFindOrCreateWhenNotExists() {
        // Arrange
        String projectName = "new-project";
        String projectPath = "/path/to/new/project";
        
        // Act
        ProjectsRecord result = repository.findOrCreate(projectName, projectPath);
        
        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getProjectId()).isNotNull();
        assertThat(result.getProjectName()).isEqualTo(projectName);
        assertThat(result.getProjectPath()).isEqualTo(projectPath);
        
        // Verify in database
        Optional<ProjectsRecord> dbRecord = repository.findByPath(projectPath);
        assertThat(dbRecord).isPresent();
        assertThat(dbRecord.get().getProjectId()).isEqualTo(result.getProjectId());
    }
    
    @Test
    void shouldThrowExceptionWhenFindOrCreateWithNullName() {
        // Arrange
        String nullName = null;
        String projectPath = "/path/to/project";
        
        // Act & Assert
        assertThatThrownBy(() -> repository.findOrCreate(nullName, projectPath))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Project name cannot be null or blank");
    }
    
    @Test
    void shouldThrowExceptionWhenFindOrCreateWithBlankName() {
        // Arrange
        String blankName = "   ";
        String projectPath = "/path/to/project";
        
        // Act & Assert
        assertThatThrownBy(() -> repository.findOrCreate(blankName, projectPath))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Project name cannot be null or blank");
    }
    
    @Test
    void shouldThrowExceptionWhenFindOrCreateWithEmptyName() {
        // Arrange
        String emptyName = "";
        String projectPath = "/path/to/project";
        
        // Act & Assert
        assertThatThrownBy(() -> repository.findOrCreate(emptyName, projectPath))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Project name cannot be null or blank");
    }
    
    @Test
    void shouldThrowExceptionWhenFindOrCreateWithNullPath() {
        // Arrange
        String projectName = "test-project";
        String nullPath = null;
        
        // Act & Assert
        assertThatThrownBy(() -> repository.findOrCreate(projectName, nullPath))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Project path cannot be null or blank");
    }
    
    @Test
    void shouldThrowExceptionWhenFindOrCreateWithBlankPath() {
        // Arrange
        String projectName = "test-project";
        String blankPath = "   ";
        
        // Act & Assert
        assertThatThrownBy(() -> repository.findOrCreate(projectName, blankPath))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Project path cannot be null or blank");
    }
    
    @Test
    void shouldThrowExceptionWhenFindOrCreateWithEmptyPath() {
        // Arrange
        String projectName = "test-project";
        String emptyPath = "";
        
        // Act & Assert
        assertThatThrownBy(() -> repository.findOrCreate(projectName, emptyPath))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Project path cannot be null or blank");
    }
    
    @Test
    void shouldInsertNewRecordInSaveWhenProjectIdIsNull() {
        // Arrange
        ProjectsRecord newRecord = dsl.newRecord(PROJECTS);
        newRecord.setProjectId(null); // Explicitly null to test insert path
        newRecord.setProjectName("new-project");
        newRecord.setProjectPath("/path/to/new");
        
        // Act
        ProjectsRecord result = repository.save(newRecord);
        
        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getProjectId()).isNotNull();
        assertThat(result.getProjectName()).isEqualTo("new-project");
        assertThat(result.getProjectPath()).isEqualTo("/path/to/new");
        
        // Verify in database
        Optional<ProjectsRecord> dbRecord = repository.findById(result.getProjectId());
        assertThat(dbRecord).isPresent();
        assertThat(dbRecord.get().getProjectName()).isEqualTo("new-project");
    }
    
    @Test
    void shouldUpdateExistingRecordInSaveWhenProjectIdExists() {
        // Arrange
        UUID projectId = createTestProject("original-name", "/path/original");
        
        ProjectsRecord recordToUpdate = dsl.selectFrom(PROJECTS)
            .where(PROJECTS.PROJECT_ID.eq(projectId))
            .fetchOne();
        assertThat(recordToUpdate).isNotNull();
        
        recordToUpdate.setProjectName("updated-name");
        recordToUpdate.setProjectPath("/path/updated");
        
        // Act
        ProjectsRecord result = repository.save(recordToUpdate);
        
        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getProjectId()).isEqualTo(projectId);
        assertThat(result.getProjectName()).isEqualTo("updated-name");
        assertThat(result.getProjectPath()).isEqualTo("/path/updated");
        
        // Verify in database
        ProjectsRecord dbRecord = dsl.selectFrom(PROJECTS)
            .where(PROJECTS.PROJECT_ID.eq(projectId))
            .fetchOne();
        assertThat(dbRecord).isNotNull();
        assertThat(dbRecord.getProjectName()).isEqualTo("updated-name");
        assertThat(dbRecord.getProjectPath()).isEqualTo("/path/updated");
        
        // Verify no duplicate created
        List<ProjectsRecord> allProjects = repository.findAll();
        assertThat(allProjects).hasSize(1);
    }
    
    @Test
    void shouldUpdateLastAnalyzedSuccessfully() {
        // Arrange
        UUID projectId = createTestProject("test-project", "/path/to/project");
        LocalDateTime beforeUpdate = LocalDateTime.now();
        
        // Wait a bit to ensure timestamp difference
        try {
            Thread.sleep(100);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        
        // Act
        repository.updateLastAnalyzed(projectId);
        
        // Assert
        ProjectsRecord updatedRecord = dsl.selectFrom(PROJECTS)
            .where(PROJECTS.PROJECT_ID.eq(projectId))
            .fetchOne();
        assertThat(updatedRecord).isNotNull();
        assertThat(updatedRecord.getLastAnalyzed()).isNotNull();
        assertThat(updatedRecord.getLastAnalyzed()).isAfter(beforeUpdate);
    }
    
    @Test
    void shouldUpdateLastAnalyzedMultipleTimes() {
        // Arrange
        UUID projectId = createTestProject("test-project", "/path/to/project");
        
        // Act - Update multiple times
        repository.updateLastAnalyzed(projectId);
        LocalDateTime firstUpdate = getLastAnalyzed(projectId);
        
        try {
            Thread.sleep(100);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        
        repository.updateLastAnalyzed(projectId);
        LocalDateTime secondUpdate = getLastAnalyzed(projectId);
        
        // Assert
        assertThat(firstUpdate).isNotNull();
        assertThat(secondUpdate).isNotNull();
        assertThat(secondUpdate).isAfter(firstUpdate);
    }
    
    @Test
    void shouldDoNothingWhenUpdateLastAnalyzedWithNullId() {
        // Arrange
        UUID nullId = null;
        
        // Act
        repository.updateLastAnalyzed(nullId);
        
        // Assert - no exception thrown, operation completes successfully
        List<ProjectsRecord> allProjects = repository.findAll();
        assertThat(allProjects).isEmpty();
    }
    
    @Test
    void shouldNotAffectOtherProjectsWhenUpdatingLastAnalyzed() {
        // Arrange
        UUID projectId1 = createTestProject("project1", "/path/to/project1");
        UUID projectId2 = createTestProject("project2", "/path/to/project2");
        
        // Act
        repository.updateLastAnalyzed(projectId1);
        
        // Assert
        ProjectsRecord updatedProject = dsl.selectFrom(PROJECTS)
            .where(PROJECTS.PROJECT_ID.eq(projectId1))
            .fetchOne();
        assertThat(updatedProject).isNotNull();
        assertThat(updatedProject.getLastAnalyzed()).isNotNull();
        
        ProjectsRecord untouchedProject = dsl.selectFrom(PROJECTS)
            .where(PROJECTS.PROJECT_ID.eq(projectId2))
            .fetchOne();
        assertThat(untouchedProject).isNotNull();
        assertThat(untouchedProject.getLastAnalyzed()).isNull();
    }
    
    @Test
    void shouldHandleMultipleProjectsSavedSequentially() {
        // Arrange
        ProjectsRecord record1 = createRecordWithoutId("project1", "/path/1");
        ProjectsRecord record2 = createRecordWithoutId("project2", "/path/2");
        ProjectsRecord record3 = createRecordWithoutId("project3", "/path/3");
        
        // Act
        repository.save(record1);
        repository.save(record2);
        repository.save(record3);
        
        // Assert
        List<ProjectsRecord> allProjects = repository.findAll();
        assertThat(allProjects).hasSize(3);
        assertThat(allProjects).extracting(ProjectsRecord::getProjectName)
            .containsExactlyInAnyOrder("project1", "project2", "project3");
    }
    
    @Test
    void shouldHandleFindOrCreateWithSamePathButDifferentName() {
        // Arrange
        String projectPath = "/shared/path";
        String firstName = "first-project";
        String secondName = "second-project";
        
        // Act
        ProjectsRecord first = repository.findOrCreate(firstName, projectPath);
        ProjectsRecord second = repository.findOrCreate(secondName, projectPath);
        
        // Assert - should find existing project, not create new one
        assertThat(first.getProjectId()).isEqualTo(second.getProjectId());
        assertThat(second.getProjectName()).isEqualTo(firstName); // Original name preserved
        
        List<ProjectsRecord> allProjects = repository.findAll();
        assertThat(allProjects).hasSize(1);
    }
    
    @Test
    void shouldPreserveAdditionalFieldsWhenSaving() {
        // Arrange
        ProjectsRecord record = dsl.newRecord(PROJECTS);
        record.setProjectId(null);
        record.setProjectName("detailed-project");
        record.setProjectPath("/path/to/detailed");
        record.setArchitectureStyle("microservices");
        record.setFileCount(150);
        
        // Act
        ProjectsRecord savedRecord = repository.save(record);
        
        // Assert
        assertThat(savedRecord.getArchitectureStyle()).isEqualTo("microservices");
        assertThat(savedRecord.getFileCount()).isEqualTo(150);
        
        // Verify in database
        ProjectsRecord dbRecord = dsl.selectFrom(PROJECTS)
            .where(PROJECTS.PROJECT_ID.eq(savedRecord.getProjectId()))
            .fetchOne();
        assertThat(dbRecord).isNotNull();
        assertThat(dbRecord.getArchitectureStyle()).isEqualTo("microservices");
        assertThat(dbRecord.getFileCount()).isEqualTo(150);
    }
    
    // Helper methods
    
    private UUID createTestProject(String projectName, String projectPath) {
        UUID projectId = UUID.randomUUID();
        ProjectsRecord project = dsl.newRecord(PROJECTS);
        project.setProjectId(projectId);
        project.setProjectName(projectName);
        project.setProjectPath(projectPath);
        dsl.insertInto(PROJECTS)
            .set(project)
            .execute();
        return projectId;
    }
    
    private ProjectsRecord createRecordWithoutId(String projectName, String projectPath) {
        ProjectsRecord record = dsl.newRecord(PROJECTS);
        record.setProjectId(null); // Explicitly null to test insert path
        record.setProjectName(projectName);
        record.setProjectPath(projectPath);
        return record;
    }
    
    private LocalDateTime getLastAnalyzed(UUID projectId) {
        ProjectsRecord record = dsl.selectFrom(PROJECTS)
            .where(PROJECTS.PROJECT_ID.eq(projectId))
            .fetchOne();
        return Objects.nonNull(record) ? record.getLastAnalyzed() : null;
    }
}
