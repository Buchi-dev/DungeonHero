package com.dungeonhero.framework.state;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/** In-memory, feature-scoped state store. Persistent features can adapt it to their own storage. */
public final class GameplayStateStore {
    private final Map<String, Map<String, Object>> states = new LinkedHashMap<>();

    public synchronized Map<String, Object> get(String scope) {
        return Map.copyOf(states.getOrDefault(scope, Map.of()));
    }

    public synchronized Object get(String scope, String key) {
        return states.getOrDefault(scope, Map.of()).get(key);
    }

    public synchronized int getInt(String scope, String key, int fallback) {
        Object value = get(scope, key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    public synchronized boolean getBoolean(String scope, String key, boolean fallback) {
        Object value = get(scope, key);
        return value instanceof Boolean booleanValue ? booleanValue : fallback;
    }

    public synchronized void put(String scope, String key, Object value) {
        states.computeIfAbsent(scope, ignored -> new LinkedHashMap<>()).put(key, value);
    }

    public synchronized Map<String, Object> update(String scope, UnaryOperator<Map<String, Object>> updater) {
        Map<String, Object> current = new LinkedHashMap<>(states.getOrDefault(scope, Map.of()));
        Map<String, Object> updated = updater.apply(current);
        Map<String, Object> safe = updated == null ? Map.of() : new LinkedHashMap<>(updated);
        states.put(scope, safe);
        return Map.copyOf(safe);
    }

    public synchronized void clearScope(String scope) {
        states.remove(scope);
    }

    public synchronized void clear() {
        states.clear();
    }
}
