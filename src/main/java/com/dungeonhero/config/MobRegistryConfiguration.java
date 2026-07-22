package com.dungeonhero.config;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Typed boundary snapshot for the external mob-registry.yml file. */
public record MobRegistryConfiguration(
    Map<String, Profile> profiles, Map<String, String> mobProfiles) {

  public static MobRegistryConfiguration load(File file, Logger logger) {
    if (!file.isFile()) return new MobRegistryConfiguration(Map.of(), Map.of());
    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
    Map<String, Profile> profiles = new LinkedHashMap<>();
    ConfigurationSection profileSection = yaml.getConfigurationSection("Profiles");
    if (profileSection != null) {
      for (String key : profileSection.getKeys(false)) {
        ConfigurationSection section = profileSection.getConfigurationSection(key);
        if (section == null) {
          logger.warning("Ignoring mob profile without a section: " + key);
          continue;
        }
        profiles.put(
            normalize(key),
            new Profile(
                normalize(key),
                section.contains("LevelOffset") ? Math.max(0, section.getInt("LevelOffset")) : null,
                section.contains("SwordXP") ? Math.max(0, section.getInt("SwordXP")) : null,
                section.getString("MobType", key)));
      }
    }
    Map<String, String> mobProfiles = new LinkedHashMap<>();
    ConfigurationSection mobs = yaml.getConfigurationSection("Mobs");
    if (mobs != null) {
      for (String key : mobs.getKeys(false)) {
        mobProfiles.put(normalize(key), mobs.getString(key + ".Profile", "normal"));
      }
    }
    return new MobRegistryConfiguration(Map.copyOf(profiles), Map.copyOf(mobProfiles));
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
  }

  public record Profile(String name, Integer levelOffset, Integer swordXp, String mobType) {}
}
