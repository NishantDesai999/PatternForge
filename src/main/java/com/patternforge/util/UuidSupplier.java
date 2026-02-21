package com.patternforge.util;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Supplier for UUID generation to make code testable.
 * Similar to ClockSupplier pattern - allows mocking UUID generation in tests.
 */
public class UuidSupplier implements Supplier<UUID> {
    
    private static UuidSupplier instance = new UuidSupplier();
    
    public static UuidSupplier getInstance() {
        return instance;
    }
    
    public static void setInstance(UuidSupplier uuidSupplier) {
        instance = uuidSupplier;
    }
    
    @Override
    public UUID get() {
        return UUID.randomUUID();
    }
}
