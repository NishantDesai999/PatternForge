package com.patternforge.storage.repository;

import com.patternforge.jooq.tables.records.ProjectsRecord;
import com.patternforge.util.UuidSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.patternforge.jooq.Tables.PROJECTS;

/**
 * Repository for project persistence operations using jOOQ.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class ProjectRepository {
    
    private final DSLContext dsl;
    
    public List<ProjectsRecord> findAll() {
        return dsl.selectFrom(PROJECTS)
            .fetch();
    }
    
    public Optional<ProjectsRecord> findById(UUID projectId) {
        if (Objects.isNull(projectId)) {
            return Optional.empty();
        }
        
        ProjectsRecord record = dsl.selectFrom(PROJECTS)
            .where(PROJECTS.PROJECT_ID.eq(projectId))
            .fetchOne();
        
        return Optional.ofNullable(record);
    }
    
    public Optional<ProjectsRecord> findByPath(String projectPath) {
        if (Objects.isNull(projectPath) || projectPath.isBlank()) {
            return Optional.empty();
        }
        
        ProjectsRecord record = dsl.selectFrom(PROJECTS)
            .where(PROJECTS.PROJECT_PATH.eq(projectPath))
            .fetchOne();
        
        return Optional.ofNullable(record);
    }
    
    public ProjectsRecord findOrCreate(String projectName, String projectPath) {
        if (Objects.isNull(projectName) || projectName.isBlank()) {
            throw new IllegalArgumentException("Project name cannot be null or blank");
        }
        if (Objects.isNull(projectPath) || projectPath.isBlank()) {
            throw new IllegalArgumentException("Project path cannot be null or blank");
        }
        
        Optional<ProjectsRecord> existing = findByPath(projectPath);
        if (existing.isPresent()) {
            log.debug("Found existing project at path: {}", projectPath);
            return existing.get();
        }
        
        ProjectsRecord record = dsl.newRecord(PROJECTS);
        record.setProjectId(UuidSupplier.getInstance().get());
        record.setProjectName(projectName);
        record.setProjectPath(projectPath);
        
        dsl.insertInto(PROJECTS)
            .set(record)
            .execute();
        
        log.info("Created new project: {} at path: {}", projectName, projectPath);
        return record;
    }
    
    public ProjectsRecord save(ProjectsRecord record) {
        if (Objects.nonNull(record.getProjectId())) {
            // Update existing record using DSLContext
            dsl.update(PROJECTS)
                .set(record)
                .where(PROJECTS.PROJECT_ID.eq(record.getProjectId()))
                .execute();
        } else {
            // Insert new record using DSLContext
            record.setProjectId(UuidSupplier.getInstance().get());
            dsl.insertInto(PROJECTS)
                .set(record)
                .execute();
        }
        return record;
    }
    
    public void updateLastAnalyzed(UUID projectId) {
        if (Objects.isNull(projectId)) {
            return;
        }
        
        dsl.update(PROJECTS)
            .set(PROJECTS.LAST_ANALYZED, LocalDateTime.now())
            .where(PROJECTS.PROJECT_ID.eq(projectId))
            .execute();
        
        log.debug("Updated last_analyzed timestamp for project: {}", projectId);
    }
}
