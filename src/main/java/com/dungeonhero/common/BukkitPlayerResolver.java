package com.dungeonhero.common;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Default online-player resolver backed by Bukkit. */
public final class BukkitPlayerResolver implements PlayerResolver {

  @Override
  public Player resolve(UUID playerId) {
    return playerId == null ? null : Bukkit.getPlayer(playerId);
  }
}
