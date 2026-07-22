package com.dungeonhero.feature.sword;

import com.dungeonhero.config.DungeonHeroConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Lifecycle-owned scheduler for periodic sword HUD synchronization. */
public final class SwordHudScheduler {

  private final JavaPlugin plugin;
  private final SwordHudService swordHudService;
  private long updateTicks;
  private BukkitTask task;

  public SwordHudScheduler(JavaPlugin plugin, SwordHudService swordHudService) {
    this.plugin = plugin;
    this.swordHudService = swordHudService;
  }

  public void reload() {
    reload(DungeonHeroConfiguration.load(plugin).hud());
  }

  public void reload(DungeonHeroConfiguration.Hud configuration) {
    close();
    updateTicks = configuration.updateTicks();
  }

  public void start() {
    if (task == null) {
      task =
          plugin
              .getServer()
              .getScheduler()
              .runTaskTimer(plugin, swordHudService::syncOnlinePlayers, updateTicks, updateTicks);
    }
  }

  public void close() {
    if (task != null) {
      task.cancel();
      task = null;
    }
  }
}
