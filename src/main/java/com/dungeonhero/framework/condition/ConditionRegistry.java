package com.dungeonhero.framework.condition;

import com.dungeonhero.framework.GameplayDefinition;
import com.dungeonhero.framework.context.GameplayContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConditionRegistry {
    private final Map<String, GameplayCondition> conditions = new LinkedHashMap<>();

    public synchronized void register(GameplayCondition condition) {
        String type = normalize(condition == null ? null : condition.type());
        if (type.isBlank()) {
            throw new IllegalArgumentException("A condition type is required.");
        }
        if (conditions.putIfAbsent(type, condition) != null) {
            throw new IllegalArgumentException("Condition type is already registered: " + type);
        }
    }

    public synchronized boolean matches(GameplayContext context, GameplayDefinition definition) {
        GameplayCondition condition = conditions.get(normalize(definition.type()));
        return condition != null && condition.matches(context, definition);
    }

    public synchronized boolean matchesAll(GameplayContext context, List<GameplayDefinition> definitions) {
        return definitions == null || definitions.stream().allMatch(definition -> matches(context, definition));
    }

    public synchronized Map<String, GameplayCondition> all() {
        return Map.copyOf(conditions);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
