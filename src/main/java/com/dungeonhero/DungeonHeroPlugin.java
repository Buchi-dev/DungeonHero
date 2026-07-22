package com.dungeonhero;

import com.dungeonhero.feature.mobregistry.MobRegistryService;
import com.dungeonhero.framework.GameplayFramework;
import org.bukkit.plugin.java.JavaPlugin;

/** Thin bootstrap delegating composition and lifecycle ordering to DungeonHeroComponents. */
public final class DungeonHeroPlugin extends JavaPlugin {

  private DungeonHeroComponents components;

  @Override
  public void onEnable() {
    saveDefaultConfig();
    saveResource("mob-registry.yml", false);

    components = new DungeonHeroComponents(this);
    components.load();
    components.reload();
    components.start();

    if (getCommand("dungeonhero") != null) {
      getCommand("dungeonhero").setExecutor(components.command());
      getCommand("dungeonhero").setTabCompleter(components.command());
    }

    getLogger()
        .info(
            "DungeonHero enabled. Hero Sword, Forge, party, rank, Dungeon Rush, and MythicMobs systems are ready.");
  }

  /** Reloads config and all registered modules in dependency order. */
  public void reloadModules() {
    if (components != null) components.reload();
  }

  /** Public integration point for other plugins that create MythicMobs. */
  public MobRegistryService getMobRegistry() {
    return components == null ? null : components.mobRegistry();
  }

  /** Public API root for registering future objectives, actions, rewards, and features. */
  public GameplayFramework getGameplayFramework() {
    return components == null ? null : components.gameplayFramework();
  }

  @Override
  public void onDisable() {
    if (components != null) {
      components.close();
      components = null;
    }
  }
}
