package com.dungeonhero.framework.context;

import java.util.UUID;
import org.bukkit.entity.Player;

public record PlayerContext(UUID id, String name) {
  public PlayerContext {
    if (id == null) {
      throw new IllegalArgumentException("Player context requires an ID.");
    }
    name = name == null ? id.toString() : name;
  }

  public static PlayerContext from(Player player) {
    return new PlayerContext(player.getUniqueId(), player.getName());
  }
}
