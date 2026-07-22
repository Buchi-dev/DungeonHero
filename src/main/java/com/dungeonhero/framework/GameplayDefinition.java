package com.dungeonhero.framework;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable configuration definition shared by objectives, actions, conditions, rewards, and
 * triggers.
 */
public record GameplayDefinition(String id, String type, Map<String, Object> parameters) {

  public GameplayDefinition {
    id = id == null || id.isBlank() ? type : id.trim();
    type = type == null ? "" : type.trim().toLowerCase(java.util.Locale.ROOT);
    parameters = parameters == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(parameters));
  }
}
