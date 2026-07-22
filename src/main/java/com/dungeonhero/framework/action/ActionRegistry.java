package com.dungeonhero.framework.action;

import com.dungeonhero.framework.GameplayDefinition;
import com.dungeonhero.framework.context.GameplayContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ActionRegistry {
    private final Map<String, GameplayAction> actions = new LinkedHashMap<>();

    public synchronized void register(GameplayAction action) {
        String type = normalize(action == null ? null : action.type());
        if (type.isBlank()) {
            throw new IllegalArgumentException("An action type is required.");
        }
        if (actions.putIfAbsent(type, action) != null) {
            throw new IllegalArgumentException("Action type is already registered: " + type);
        }
    }

    public synchronized ActionResult execute(GameplayContext context, GameplayDefinition definition) {
        GameplayAction action = actions.get(normalize(definition.type()));
        return action == null ? new ActionResult(false, "Unknown action: " + definition.type())
                : action.execute(context, definition);
    }

    public synchronized List<ActionResult> executeAll(GameplayContext context, List<GameplayDefinition> definitions) {
        return definitions == null ? List.of() : definitions.stream().map(definition -> execute(context, definition)).toList();
    }

    public synchronized Map<String, GameplayAction> all() {
        return Map.copyOf(actions);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
