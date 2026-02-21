package com.patternforge.util;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Utility methods for converting between float arrays and PostgreSQL vector format.
 */
public class VectorUtils {
    
    /**
     * Converts float array to PostgreSQL vector string format.
     * Example: [0.1, 0.2, 0.3] → "[0.1,0.2,0.3]"
     */
    public static String toPostgresVector(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            return "[]";
        }
        
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(embedding[i]);
        }
        sb.append("]");
        
        return sb.toString();
    }
    
    /**
     * Parses PostgreSQL vector string back to float array.
     * Example: "[0.1,0.2,0.3]" → [0.1, 0.2, 0.3]
     */
    public static float[] fromPostgresVector(String vectorString) {
        if (vectorString == null || vectorString.isBlank()) {
            return new float[0];
        }
        
        String cleaned = vectorString.trim()
            .replaceAll("^\\[", "")
            .replaceAll("\\]$", "");
        
        if (cleaned.isBlank()) {
            return new float[0];
        }
        
        String[] parts = cleaned.split(",");
        float[] result = new float[parts.length];
        
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        
        return result;
    }
}
