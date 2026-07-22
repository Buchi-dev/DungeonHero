package com.dungeonhero.feature.sword;

import com.dungeonhero.common.MythicDeathDeduplicator;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Thin Bukkit event adapter for sword progression. */
public final class SwordProgressionListener implements Listener {

  private final JavaPlugin plugin;
  private final SwordProgressionService service;
  private final MythicDeathDeduplicator deduplicator;

  public SwordProgressionListener(
      JavaPlugin plugin, SwordProgressionService service, MythicDeathDeduplicator deduplicator) {
    this.plugin = plugin;
    this.service = service;
    this.deduplicator = deduplicator;
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onMobDeath(EntityDeathEvent event) {
    if (!service.autoMobKillXpEnabled()
        || !(event.getEntity() instanceof Mob mob)
        || (service.hostileMobKillXpOnly() && !(mob instanceof Monster))) return;
    if (deduplicator.shouldIgnoreVanilla(mob.getUniqueId())) return;
    Player player = mob.getKiller();
    if (player != null) service.awardMobKillExperience(player);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onMythicMobDeath(MythicMobDeathEvent event) {
    if (!service.autoMobKillXpEnabled() || !(event.getEntity() instanceof LivingEntity entity))
      return;
    deduplicator.mark(plugin, entity.getUniqueId());
    if (event.getKiller() instanceof Player player) {
      String internalName = event.getMobType() == null ? "" : event.getMobType().getInternalName();
      service.awardMythicMobExperience(player, internalName);
    }
  }

  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  public void onSwordXpPickup(EntityPickupItemEvent event) {
    if (!(event.getEntity() instanceof Player player)) return;
    if (service.collectSwordXp(player, event.getItem().getItemStack())) {
      event.setCancelled(true);
      event.getItem().remove();
    }
  }
}
