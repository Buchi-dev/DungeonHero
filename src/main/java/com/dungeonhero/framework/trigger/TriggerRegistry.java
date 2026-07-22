package com.dungeonhero.framework.trigger;

import com.dungeonhero.framework.GameplayDefinition;
import com.dungeonhero.framework.context.GameplayContext;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TriggerRegistry {
  private final Map<String, GameplayTrigger> triggers = new LinkedHashMap<>();

  public synchronized void register(GameplayTrigger trigger) {
    String type = normalize(trigger == null ? null : trigger.type());
    if (type.isBlank()) {
      throw new IllegalArgumentException("A trigger type is required.");
    }
    if (triggers.putIfAbsent(type, trigger) != null) {
      throw new IllegalArgumentException("Trigger type is already registered: " + type);
    }
  }

  public synchronized boolean matches(GameplayContext context, GameplayDefinition definition) {
    GameplayTrigger trigger = triggers.get(normalize(definition.type()));
    return trigger != null && trigger.matches(context, definition);
  }

  public synchronized Map<String, GameplayTrigger> all() {
    return Map.copyOf(triggers);
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
  }
}
