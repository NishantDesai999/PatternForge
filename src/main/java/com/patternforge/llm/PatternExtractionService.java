package com.patternforge.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patternforge.api.dto.PatternExtractionRequest;
import com.patternforge.api.dto.PatternDto;
import com.patternforge.jooq.tables.records.PatternsRecord;
import com.patternforge.storage.repository.PatternRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.JSONB;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Orchestrates LLM-based pattern extraction from agent rules files (AGENTS.md, CLAUDE.md, etc.).
 * Reads the file, calls the Anthropic API to extract patterns as JSON,
 * and bulk-upserts them via PatternRepository.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PatternExtractionService {

    private final AnthropicClient anthropicClient;
    private final PatternRepository patternRepository;
    private final ObjectMapper objectMapper;

    @Value("${patternforge.extraction.llm-model}")
    private String llmModel;

    @Value("${patternforge.extraction.max-tokens}")
    private int maxTokens;

    private static final double EXTRACTED_CONFIDENCE = 0.85;

    /**
     * Extracts patterns from the rules file specified in the request.
     *
     * @param request extraction request with filePath (required) and optional projectPath
     * @return list of upserted PatternDto objects
     * @throws IllegalArgumentException if the file does not exist or is not readable
     * @throws AnthropicApiException    if the LLM call fails
     */
    public List<PatternDto> extract(PatternExtractionRequest request) {
        String filePath = request.getFilePath();
        if (Objects.isNull(filePath) || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath is required");
        }

        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("File does not exist: " + filePath);
        }
        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException("File is not readable: " + filePath);
        }

        String fileContent;
        try {
            fileContent = Files.readString(path);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to read file: " + filePath, exception);
        }

        log.info("Extracting patterns from file={}, model={}", filePath, llmModel);

        String prompt = buildExtractionPrompt(fileContent, filePath);
        String llmResponse = anthropicClient.complete(llmModel, maxTokens, prompt);

        List<Map<String, Object>> rawPatterns = parseJsonResponse(llmResponse);
        log.info("LLM returned {} raw pattern entries", rawPatterns.size());

        String sourceTag = "llm-extracted:" + filePath;
        List<PatternsRecord> records = mapToRecords(rawPatterns, sourceTag);

        List<PatternsRecord> saved = patternRepository.saveAll(records);
        log.info("Upserted {} patterns from {}", saved.size(), filePath);

        return saved.stream()
                .map(this::toDto)
                .toList();
    }

    private String buildExtractionPrompt(String fileContent, String filePath) {
        return """
                You are a pattern extraction assistant. Your job is to read a coding rules/guidelines file \
                and extract discrete, reusable coding patterns from it.

                Rules file: %s

                --- FILE CONTENT START ---
                %s
                --- FILE CONTENT END ---

                Extract all distinct coding patterns, conventions, and guidelines from the file above.
                Return ONLY a valid JSON array — no markdown code fences, no explanation text, just the raw JSON.

                Each element in the array must have these fields:
                {
                  "pattern_name": "kebab-case-unique-identifier",
                  "title": "Human readable title",
                  "description": "Detailed description of the pattern",
                  "category": "one of: architecture, error-handling, testing, security, performance, naming, style, tooling, workflow",
                  "when_to_use": "Conditions or scenarios where this pattern applies",
                  "languages": ["java", "python", etc. — use [] if language-agnostic],
                  "code_examples": {"good": "example code here", "bad": "anti-pattern code if applicable"},
                  "is_global_standard": true or false
                }

                Focus on actionable patterns an AI coding agent can apply. Skip high-level philosophy \
                and project-management guidelines that aren't coding patterns.
                """.formatted(filePath, fileContent);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseJsonResponse(String llmResponse) {
        // Secondary guard: strip accidental ```json fences the model may add despite instructions
        String cleaned = llmResponse.strip();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline != -1) {
                cleaned = cleaned.substring(firstNewline + 1);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.lastIndexOf("```")).strip();
            }
        }

        try {
            return objectMapper.readValue(cleaned, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception exception) {
            throw new AnthropicApiException(
                    "Failed to parse LLM response as JSON array: " + exception.getMessage(), exception);
        }
    }

    private List<PatternsRecord> mapToRecords(List<Map<String, Object>> rawPatterns, String sourceTag) {
        List<PatternsRecord> records = new ArrayList<>();

        for (Map<String, Object> raw : rawPatterns) {
            try {
                PatternsRecord record = new PatternsRecord();

                String patternName = (String) raw.get("pattern_name");
                if (Objects.isNull(patternName) || patternName.isBlank()) {
                    log.warn("Skipping entry with missing pattern_name");
                    continue;
                }

                record.setPatternName(patternName);
                record.setTitle(stringOrNull(raw, "title"));
                record.setDescription(stringOrNull(raw, "description"));
                record.setCategory(stringOrNull(raw, "category"));
                record.setScope("global");
                record.setWhenToUse(stringOrNull(raw, "when_to_use"));
                record.setSource(sourceTag);
                record.setConfidence(EXTRACTED_CONFIDENCE);

                Object langsObj = raw.get("languages");
                if (langsObj instanceof List<?> langList) {
                    String[] langs = langList.stream()
                            .filter(Objects::nonNull)
                            .map(Object::toString)
                            .toArray(String[]::new);
                    record.setLanguages(langs);
                }

                Object codeExamplesObj = raw.get("code_examples");
                if (Objects.nonNull(codeExamplesObj)) {
                    try {
                        String codeExamplesJson = objectMapper.writeValueAsString(codeExamplesObj);
                        record.setCodeExamples(JSONB.valueOf(codeExamplesJson));
                    } catch (Exception exception) {
                        log.warn("Failed to serialize code_examples for pattern {}, skipping field", patternName);
                    }
                }

                Object isGlobalObj = raw.get("is_global_standard");
                if (isGlobalObj instanceof Boolean isGlobal) {
                    record.setIsGlobalStandard(isGlobal);
                } else {
                    record.setIsGlobalStandard(false);
                }

                records.add(record);
            } catch (Exception exception) {
                log.warn("Skipping malformed pattern entry: {}", exception.getMessage());
            }
        }

        return records;
    }

    private String stringOrNull(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return Objects.nonNull(value) ? value.toString() : null;
    }

    private PatternDto toDto(PatternsRecord record) {
        Map<String, String> codeExamples = null;
        if (Objects.nonNull(record.getCodeExamples())) {
            try {
                codeExamples = objectMapper.readValue(
                        record.getCodeExamples().data(),
                        new TypeReference<Map<String, String>>() {}
                );
            } catch (Exception exception) {
                log.warn("Failed to parse code examples for pattern: {}", record.getPatternId());
            }
        }

        List<String> languages = Objects.nonNull(record.getLanguages())
                ? Arrays.asList(record.getLanguages())
                : null;

        return PatternDto.builder()
                .patternId(record.getPatternId())
                .patternName(record.getPatternName())
                .title(record.getTitle())
                .description(record.getDescription())
                .category(record.getCategory())
                .scope(record.getScope())
                .languages(languages)
                .whenToUse(record.getWhenToUse())
                .codeExamples(codeExamples)
                .successRate(record.getSuccessRate())
                .usageCount(record.getUsageCount())
                .isGlobalStandard(record.getIsGlobalStandard())
                .build();
    }
}
