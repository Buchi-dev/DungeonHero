package com.dungeonhero.framework.event;

import java.util.UUID;

public record MobDefeatedEvent(UUID playerId, String mobType, int amount) implements GameplayEvent {
  public MobDefeatedEvent {
    if (playerId == null) {
      throw new IllegalArgumentException("A mob defeat event requires a player ID.");
    }
    mobType = mobType == null ? "" : mobType.trim();
    amount = Math.max(1, amount);
  }
}
