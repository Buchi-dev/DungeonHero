package com.dungeonhero.framework.objective;

import com.dungeonhero.framework.GameplayDefinition;
import com.dungeonhero.framework.context.GameplayContext;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ObjectiveRegistry {
  private final Map<String, GameplayObjective> objectives = new LinkedHashMap<>();

  public synchronized void register(GameplayObjective objective) {
    requireType(objective == null ? null : objective.type(), "objective");
    String type = normalize(objective.type());
    if (objectives.containsKey(type)) {
      throw new IllegalArgumentException("Objective type is already registered: " + type);
    }
    objectives.put(type, objective);
  }

  public synchronized ObjectiveResult evaluate(
      GameplayContext context, GameplayDefinition definition) {
    GameplayObjective objective = objectives.get(normalize(definition.type()));
    if (objective == null) {
      return ObjectiveResult.noProgress();
    }
    return objective.evaluate(context, definition);
  }

  public synchronized Map<String, GameplayObjective> all() {
    return Collections.unmodifiableMap(new LinkedHashMap<>(objectives));
  }

  private void requireType(String type, String kind) {
    if (type == null || type.isBlank()) {
      throw new IllegalArgumentException("A " + kind + " type is required.");
    }
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
  }
}
