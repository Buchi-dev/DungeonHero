package com.dungeonhero.framework.context;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public record TeamContext(String id, Set<UUID> members) {
  public TeamContext {
    id = id == null ? "" : id.trim();
    members = members == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(members));
  }
}
