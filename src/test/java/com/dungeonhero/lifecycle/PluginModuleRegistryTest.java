package com.dungeonhero.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dungeonhero.TestFixtures;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

class PluginModuleRegistryTest {

  @Test
  void runsLifecycleInRegistrationOrderAndClosesInReverseOrder() throws Exception {
    JavaPlugin plugin = TestFixtures.plugin(Files.createTempDirectory("dungeonhero-modules"));
    PluginModuleRegistry registry = new PluginModuleRegistry(plugin);
    List<String> events = new ArrayList<>();

    registry.register(module("first", events));
    registry.register(module("second", events));

    registry.load();
    registry.reload();
    registry.start();
    registry.reload();
    registry.close();

    assertEquals(
        List.of(
            "first.load", "second.load",
            "first.reload", "second.reload",
            "first.start", "second.start",
            "first.reload", "second.reload",
            "first.start", "second.start",
            "second.close", "first.close"),
        events);
  }

  private static PluginModule module(String id, List<String> events) {
    return new PluginModule(
        id,
        null,
        () -> events.add(id + ".load"),
        () -> events.add(id + ".reload"),
        () -> events.add(id + ".start"),
        () -> events.add(id + ".close"));
  }
}
