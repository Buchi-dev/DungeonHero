package com.dungeonhero.common;

import io.lumine.mythic.bukkit.MythicBukkit;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.plugin.java.JavaPlugin;

/** Shared guard for MythicMobs deaths that may also arrive as vanilla deaths. */
public final class MythicDeathDeduplicator {

  private final Set<UUID> mythicDeathsAwaitingVanillaCheck = ConcurrentHashMap.newKeySet();

  public void mark(JavaPlugin plugin, UUID entityId) {
    if (entityId == null) {
      return;
    }
    mythicDeathsAwaitingVanillaCheck.add(entityId);
    plugin
        .getServer()
        .getScheduler()
        .runTaskLater(plugin, () -> mythicDeathsAwaitingVanillaCheck.remove(entityId), 2L);
  }

  public boolean shouldIgnoreVanilla(UUID entityId) {
    return entityId != null
        && (mythicDeathsAwaitingVanillaCheck.contains(entityId) || isActiveMythicMob(entityId));
  }

  private boolean isActiveMythicMob(UUID entityId) {
    try {
      return MythicBukkit.inst().getMobManager().isActiveMob(entityId);
    } catch (RuntimeException exception) {
      return false;
    }
  }
}
