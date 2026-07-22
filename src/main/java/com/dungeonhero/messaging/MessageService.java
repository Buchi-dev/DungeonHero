package com.dungeonhero.messaging;

import com.dungeonhero.config.DungeonHeroConfiguration;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Loads locale resources while keeping an English fallback for missing keys. */
public final class MessageService {

  private final JavaPlugin plugin;
  private YamlConfiguration messages = new YamlConfiguration();

  public MessageService(JavaPlugin plugin) {
    this.plugin = plugin;
    reload(DungeonHeroConfiguration.load(plugin).locale());
  }

  public void reload() {
    reload(DungeonHeroConfiguration.load(plugin).locale());
  }

  public void reload(String configuredLocale) {
    String locale =
        configuredLocale == null || configuredLocale.isBlank()
            ? DungeonHeroConfiguration.DEFAULT_LOCALE
            : configuredLocale.toLowerCase(java.util.Locale.ROOT);
    messages = load(locale);
  }

  public Component text(String key, String fallback) {
    return Component.text(messages.getString(key, fallback));
  }

  private YamlConfiguration load(String locale) {
    YamlConfiguration loaded = loadResource(locale);
    return loaded.getKeys(true).isEmpty() && !DungeonHeroConfiguration.DEFAULT_LOCALE.equals(locale)
        ? loadResource(DungeonHeroConfiguration.DEFAULT_LOCALE)
        : loaded;
  }

  private YamlConfiguration loadResource(String locale) {
    String path = "messages/" + locale + ".yml";
    InputStream resource = plugin.getResource(path);
    if (resource == null) {
      return new YamlConfiguration();
    }
    try (InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
      return YamlConfiguration.loadConfiguration(reader);
    } catch (java.io.IOException exception) {
      plugin
          .getLogger()
          .warning("Unable to load message resource " + path + ": " + exception.getMessage());
      return new YamlConfiguration();
    }
  }
}
