package com.dungeonhero.lifecycle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

/** Ordered lifecycle coordinator for plugin modules and their listeners. */
public final class PluginModuleRegistry {

  private final JavaPlugin plugin;
  private final List<PluginModule> modules = new ArrayList<>();
  private final Set<String> moduleIds = new HashSet<>();
  private boolean loaded;
  private boolean started;

  public PluginModuleRegistry(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  public synchronized void register(PluginModule module) {
    if (!moduleIds.add(module.id())) {
      throw new IllegalArgumentException("Plugin module is already registered: " + module.id());
    }
    modules.add(module);
  }

  public synchronized void load() {
    if (loaded) return;
    for (PluginModule module : modules) module.load().run();
    loaded = true;
  }

  public synchronized void reload() {
    if (!loaded) load();
    boolean wasStarted = started;
    for (PluginModule module : modules) module.reload().run();
    if (wasStarted) {
      for (PluginModule module : modules) module.start().run();
    }
  }

  public synchronized void start() {
    if (!loaded) load();
    if (started) return;
    for (PluginModule module : modules) {
      if (module.listener() != null) {
        plugin.getServer().getPluginManager().registerEvents(module.listener(), plugin);
      }
    }
    for (PluginModule module : modules) module.start().run();
    started = true;
  }

  public synchronized void close() {
    if (!loaded) return;
    for (int index = modules.size() - 1; index >= 0; index--) {
      PluginModule module = modules.get(index);
      module.close().run();
      if (module.listener() != null) HandlerList.unregisterAll(module.listener());
    }
    started = false;
    loaded = false;
  }

  public synchronized List<String> moduleIds() {
    return modules.stream().map(PluginModule::id).toList();
  }
}
