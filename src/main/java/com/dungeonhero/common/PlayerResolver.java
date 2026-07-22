package com.dungeonhero.common;

import java.util.UUID;
import org.bukkit.entity.Player;

/** Resolves online players at the Bukkit boundary. */
@FunctionalInterface
public interface PlayerResolver {

  Player resolve(UUID playerId);
}
