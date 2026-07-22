package com.dungeonhero.framework;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/** Safe, feature-scoped view over the existing Bukkit YAML configuration. */
public final class FeatureConfig {

  public static final int CURRENT_VERSION = 1;

  private final String featureId;
  private final ConfigurationSection section;

  public FeatureConfig(String featureId, ConfigurationSection section) {
    this.featureId = featureId;
    this.section = section;
  }

  public String featureId() {
    return featureId;
  }

  public boolean enabled() {
    return section == null || !section.contains("Enabled") || section.getBoolean("Enabled");
  }

  public int version() {
    return section == null ? CURRENT_VERSION : section.getInt("ConfigVersion", CURRENT_VERSION);
  }

  public ConfigurationSection section() {
    return section;
  }

  public String getString(String path, String fallback) {
    return section == null ? fallback : section.getString(path, fallback);
  }

  public int getInt(String path, int fallback) {
    return section == null ? fallback : section.getInt(path, fallback);
  }

  public boolean getBoolean(String path, boolean fallback) {
    return section == null ? fallback : section.getBoolean(path, fallback);
  }

  public List<String> getStringList(String path) {
    return section == null ? List.of() : List.copyOf(section.getStringList(path));
  }

  public List<GameplayDefinition> definitions(String path) {
    if (section == null || !(section.get(path) instanceof List<?> values)) {
      return List.of();
    }
    List<GameplayDefinition> definitions = new ArrayList<>();
    int index = 0;
    for (Object value : values) {
      if (!(value instanceof Map<?, ?> raw)) {
        index++;
        continue;
      }
      Map<String, Object> parameters = new LinkedHashMap<>();
      String type = "";
      String id = "";
      for (Map.Entry<?, ?> entry : raw.entrySet()) {
        if (entry.getKey() == null) {
          continue;
        }
        String key = entry.getKey().toString();
        Object item = entry.getValue();
        if (key.equalsIgnoreCase("type")) {
          type = item == null ? "" : item.toString();
        } else if (key.equalsIgnoreCase("id")) {
          id = item == null ? "" : item.toString();
        } else {
          parameters.put(key.toLowerCase(java.util.Locale.ROOT), item);
        }
      }
      definitions.add(
          new GameplayDefinition(id.isBlank() ? path + "-" + index : id, type, parameters));
      index++;
    }
    return List.copyOf(definitions);
  }

  public Map<String, Object> settings() {
    if (section == null) {
      return Map.of();
    }
    ConfigurationSection settings = section.getConfigurationSection("Settings");
    if (settings == null) {
      return Map.of();
    }
    return Map.copyOf(new LinkedHashMap<>(settings.getValues(false)));
  }

  /** Returns configured timer durations in seconds, ready for use with TimerService. */
  public Map<String, Duration> timers() {
    if (section == null) {
      return Map.of();
    }
    ConfigurationSection timerSection = section.getConfigurationSection("Timers");
    if (timerSection == null) {
      return Map.of();
    }
    Map<String, Duration> timers = new LinkedHashMap<>();
    for (String key : timerSection.getKeys(false)) {
      long seconds = timerSection.getLong(key, 0);
      if (seconds > 0) {
        timers.put(key, Duration.ofSeconds(seconds));
      }
    }
    return Map.copyOf(timers);
  }

  public Map<String, Object> metadata() {
    if (section == null) {
      return Map.of();
    }
    ConfigurationSection metadata = section.getConfigurationSection("Metadata");
    return metadata == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(metadata.getValues(false)));
  }
}
