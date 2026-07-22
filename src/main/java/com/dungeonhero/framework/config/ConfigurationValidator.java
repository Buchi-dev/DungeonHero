package com.dungeonhero.framework.config;

import com.dungeonhero.framework.FeatureConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/** Validates framework-owned configuration before a feature is allowed to start. */
public final class ConfigurationValidator {

  private ConfigurationValidator() {}

  public static ConfigurationValidationResult validate(FeatureConfig config) {
    List<ConfigurationIssue> issues = new ArrayList<>();
    ConfigurationSection section = config.section();
    if (section == null) {
      return new ConfigurationValidationResult(issues);
    }

    Object enabled = section.get("Enabled");
    if (enabled != null && !(enabled instanceof Boolean)) {
      issues.add(new ConfigurationIssue(config.featureId() + ".Enabled", "must be true or false"));
    }
    Object version = section.get("ConfigVersion");
    if (version != null && (!(version instanceof Number) || ((Number) version).intValue() < 1)) {
      issues.add(
          new ConfigurationIssue(
              config.featureId() + ".ConfigVersion", "must be a positive number"));
    } else if (version instanceof Number
        && ((Number) version).intValue() > FeatureConfig.CURRENT_VERSION) {
      issues.add(
          new ConfigurationIssue(
              config.featureId() + ".ConfigVersion",
              "is newer than the supported version " + FeatureConfig.CURRENT_VERSION));
    }

    for (String key : List.of("Objectives", "Conditions", "Actions", "Rewards", "Triggers")) {
      Object definitions = section.get(key);
      if (definitions == null) {
        continue;
      }
      if (!(definitions instanceof List<?> values)) {
        issues.add(new ConfigurationIssue(config.featureId() + "." + key, "must be a list"));
        continue;
      }
      for (int index = 0; index < values.size(); index++) {
        Object definition = values.get(index);
        if (!(definition instanceof Map<?, ?> map)) {
          issues.add(
              new ConfigurationIssue(
                  config.featureId() + "." + key + "[" + index + "]",
                  "must be a map containing type"));
          continue;
        }
        Object type = findIgnoreCase(map, "type");
        if (!(type instanceof String value) || value.isBlank()) {
          issues.add(
              new ConfigurationIssue(
                  config.featureId() + "." + key + "[" + index + "].type",
                  "must be a non-blank string"));
        }
      }
    }
    Object timers = section.get("Timers");
    if (timers != null && !(timers instanceof ConfigurationSection)) {
      issues.add(
          new ConfigurationIssue(
              config.featureId() + ".Timers", "must be a map of positive seconds"));
    } else if (timers instanceof ConfigurationSection timerSection) {
      for (String key : timerSection.getKeys(false)) {
        Object value = timerSection.get(key);
        if (!(value instanceof Number number) || number.longValue() < 1) {
          issues.add(
              new ConfigurationIssue(
                  config.featureId() + ".Timers." + key, "must be a positive number of seconds"));
        }
      }
    }
    return new ConfigurationValidationResult(issues);
  }

  private static Object findIgnoreCase(Map<?, ?> map, String key) {
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (entry.getKey() != null && key.equalsIgnoreCase(entry.getKey().toString())) {
        return entry.getValue();
      }
    }
    return null;
  }
}
