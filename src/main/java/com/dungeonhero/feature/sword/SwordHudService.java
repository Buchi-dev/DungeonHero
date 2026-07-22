package com.dungeonhero.feature.sword;

import com.dungeonhero.config.DungeonHeroConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class SwordHudService implements Listener {

  private final JavaPlugin plugin;
  private final HeroItemService heroItemService;
  private final SwordProgressionService swordProgressionService;
  private boolean enabled;

  public SwordHudService(
      JavaPlugin plugin,
      HeroItemService heroItemService,
      SwordProgressionService swordProgressionService) {
    this(
        plugin,
        heroItemService,
        swordProgressionService,
        DungeonHeroConfiguration.load(plugin).hud());
  }

  public SwordHudService(
      JavaPlugin plugin,
      HeroItemService heroItemService,
      SwordProgressionService swordProgressionService,
      DungeonHeroConfiguration.Hud configuration) {
    this.plugin = plugin;
    this.heroItemService = heroItemService;
    this.swordProgressionService = swordProgressionService;
    reload(configuration);
  }

  public void reload() {
    reload(DungeonHeroConfiguration.load(plugin).hud());
  }

  public void reload(DungeonHeroConfiguration.Hud configuration) {
    enabled = configuration.useVanillaXpBar();
  }

  public void syncOnlinePlayers() {
    if (!enabled) {
      return;
    }
    for (Player player : Bukkit.getOnlinePlayers()) {
      sync(player);
    }
  }

  public void sync(Player player) {
    if (!enabled) {
      return;
    }

    ItemStack sword = heroItemService.findStrongestHeroSword(player);
    if (!heroItemService.isHeroSword(sword)) {
      player.setLevel(0);
      player.setExp(0.0f);
      return;
    }

    player.setLevel(heroItemService.getSwordLevel(sword));
    player.setExp(
        swordProgressionService.getHudProgress(
            sword, swordProgressionService.getMaxSwordLevel(player)));
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    Bukkit.getScheduler().runTask(plugin, () -> sync(event.getPlayer()));
  }

  @EventHandler
  public void onRespawn(PlayerRespawnEvent event) {
    Bukkit.getScheduler().runTask(plugin, () -> sync(event.getPlayer()));
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onVanillaExperience(PlayerExpChangeEvent event) {
    if (!enabled) {
      return;
    }
    event.setAmount(0);
    Bukkit.getScheduler().runTask(plugin, () -> sync(event.getPlayer()));
  }

  @EventHandler
  public void onLevelChange(PlayerLevelChangeEvent event) {
    if (enabled) {
      Bukkit.getScheduler().runTask(plugin, () -> sync(event.getPlayer()));
    }
  }

  @EventHandler
  public void onDeath(PlayerDeathEvent event) {
    if (enabled) {
      event.setDroppedExp(0);
    }
  }
}
