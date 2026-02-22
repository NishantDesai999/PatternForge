package com.patternforge.api.dto;

/**
 * Request body for POST /api/patterns/query.
 *
 * @param task           plain-English description of the task being worked on
 * @param language       programming language (java, python, typescript, etc.)
 * @param projectPath    absolute path to the project root; enables project-specific patterns
 * @param conversationId optional session ID to link queries across a conversation
 * @param topK           maximum task-specific patterns to return from search; global standards are always included
 */
public record PatternQueryRequest(
        String task,
        String language,
        String projectPath,
        String conversationId,
        Integer topK) {}
