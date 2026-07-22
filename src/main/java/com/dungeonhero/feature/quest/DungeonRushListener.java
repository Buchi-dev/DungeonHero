package com.dungeonhero.feature.quest;

import com.dungeonhero.common.MythicDeathDeduplicator;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Thin event adapter for Dungeon Rush; application logic remains in DungeonRushService. */
public final class DungeonRushListener implements Listener {

  private final JavaPlugin plugin;
  private final DungeonRushService service;
  private final MythicDeathDeduplicator deduplicator;

  public DungeonRushListener(
      JavaPlugin plugin, DungeonRushService service, MythicDeathDeduplicator deduplicator) {
    this.plugin = plugin;
    this.service = service;
    this.deduplicator = deduplicator;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onMythicMobDeath(MythicMobDeathEvent event) {
    if (!service.enabled()) return;
    if (!(event.getEntity() instanceof LivingEntity entity)) return;
    deduplicator.mark(plugin, entity.getUniqueId());
    if (event.getKiller() instanceof Player player) {
      service.recordKill(player, entity.getLocation(), QuestScoringPolicy.KillType.MYTHIC);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onVanillaMobDeath(EntityDeathEvent event) {
    if (!(event.getEntity() instanceof Monster monster)
        || deduplicator.shouldIgnoreVanilla(monster.getUniqueId())) return;
    Player killer = monster.getKiller();
    if (killer != null)
      service.recordKill(killer, monster.getLocation(), QuestScoringPolicy.KillType.DUNGEON);
  }
}
