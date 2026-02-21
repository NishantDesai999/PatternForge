package com.patternforge.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON serialization utilities using Jackson.
 */
public class JsonUtils {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    /**
     * Converts object to JSON string.
     */
    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException jsonProcessingException) {
            throw new RuntimeException("Failed to serialize to JSON", jsonProcessingException);
        }
    }
    
    /**
     * Parses JSON string to object.
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException jsonProcessingException) {
            throw new RuntimeException("Failed to deserialize from JSON", jsonProcessingException);
        }
    }
}
