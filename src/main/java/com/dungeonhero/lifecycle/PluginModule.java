package com.dungeonhero.lifecycle;

import org.bukkit.event.Listener;

/** One named plugin module with a consistent lifecycle and optional event listener. */
public record PluginModule(
    String id, Listener listener, Runnable load, Runnable reload, Runnable start, Runnable close) {

  public PluginModule {
    if (id == null || id.isBlank()) throw new IllegalArgumentException("Module id is required.");
    load = load == null ? () -> {} : load;
    reload = reload == null ? () -> {} : reload;
    start = start == null ? () -> {} : start;
    close = close == null ? () -> {} : close;
  }
}
