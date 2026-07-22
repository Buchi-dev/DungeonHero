package com.dungeonhero;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.UnsafeValues;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

/** Shared Bukkit test fixtures that keep service tests focused on behavior. */
public final class TestFixtures {

  private TestFixtures() {}

  public static JavaPlugin plugin(Path dataFolder) {
    return plugin(dataFolder, null);
  }

  public static JavaPlugin plugin(Path dataFolder, Server server) {
    if (server == null) {
      server = mock(Server.class);
    }
    when(server.getUnsafe()).thenReturn(mock(UnsafeValues.class));
    installBukkitServer(server);
    return FixturePlugin.create(dataFolder, server);
  }

  public static Player player(UUID id, String name) {
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(id);
    when(player.getName()).thenReturn(name);
    return player;
  }

  public static FileConfiguration defaultConfig() {
    try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources/config.yml"))) {
      return YamlConfiguration.loadConfiguration(reader);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to load the test configuration.", exception);
    }
  }

  private static void installBukkitServer(Server server) {
    try {
      var field = Bukkit.class.getDeclaredField("server");
      field.setAccessible(true);
      field.set(null, server);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Unable to install the Bukkit test server.", exception);
    }
  }

  private static final class FixturePlugin extends JavaPlugin {

    private FileConfiguration config;
    private Logger logger;

    private static FixturePlugin create(Path dataFolder, Server server) {
      try {
        FixturePlugin plugin = (FixturePlugin) unsafe().allocateInstance(FixturePlugin.class);
        plugin.config = defaultConfig();
        plugin.logger = Logger.getLogger("DungeonHeroTest");
        PluginDescriptionFile description =
            new PluginDescriptionFile("DungeonHero", "test", FixturePlugin.class.getName());
        setJavaPluginField(plugin, "server", server);
        setJavaPluginField(plugin, "description", description);
        setJavaPluginField(plugin, "dataFolder", dataFolder.toFile());
        setJavaPluginField(plugin, "file", null);
        setJavaPluginField(plugin, "classLoader", FixturePlugin.class.getClassLoader());
        setJavaPluginField(plugin, "configFile", dataFolder.resolve("config.yml").toFile());
        setJavaPluginField(plugin, "pluginMeta", description);
        setJavaPluginField(plugin, "logger", plugin.logger);
        return plugin;
      } catch (InstantiationException exception) {
        throw new IllegalStateException("Unable to create the Bukkit test plugin.", exception);
      }
    }

    @Override
    public FileConfiguration getConfig() {
      return config;
    }

    @Override
    public Logger getLogger() {
      return logger;
    }

    private static sun.misc.Unsafe unsafe() {
      try {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
      } catch (ReflectiveOperationException exception) {
        throw new IllegalStateException("Unable to access the test allocator.", exception);
      }
    }

    private static void setJavaPluginField(JavaPlugin plugin, String name, Object value) {
      try {
        var field = JavaPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
      } catch (ReflectiveOperationException exception) {
        throw new IllegalStateException("Unable to configure the Bukkit test plugin.", exception);
      }
    }
  }
}
