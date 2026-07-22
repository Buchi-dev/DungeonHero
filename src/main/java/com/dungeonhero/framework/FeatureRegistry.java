package com.dungeonhero.framework;

import com.dungeonhero.framework.config.ConfigurationValidationResult;
import com.dungeonhero.framework.config.ConfigurationValidator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.bukkit.configuration.ConfigurationSection;

/** Registers and safely coordinates feature configuration and lifecycle transitions. */
public final class FeatureRegistry {

  private final FeatureContext context;
  private final Map<String, GameplayFeature> features = new LinkedHashMap<>();
  private final Map<String, FeatureLifecycle> lifecycles = new LinkedHashMap<>();
  private final Map<String, String> errors = new LinkedHashMap<>();

  public FeatureRegistry(FeatureContext context) {
    this.context = context;
  }

  public synchronized void register(GameplayFeature feature) {
    if (feature == null || feature.id() == null || feature.id().isBlank()) {
      throw new IllegalArgumentException("A gameplay feature must have a non-blank ID.");
    }
    String id = normalize(feature.id());
    if (features.containsKey(id)) {
      throw new IllegalArgumentException("Gameplay feature is already registered: " + id);
    }
    features.put(id, feature);
    lifecycles.put(id, FeatureLifecycle.REGISTERED);
  }

  public synchronized void reload(ConfigurationSection featureRoot) {
    stopAll();
    errors.clear();
    for (Map.Entry<String, GameplayFeature> entry : features.entrySet()) {
      String id = entry.getKey();
      ConfigurationSection section =
          featureRoot == null ? null : featureRoot.getConfigurationSection(id);
      FeatureConfig config = new FeatureConfig(id, section);
      ConfigurationValidationResult validation = ConfigurationValidator.validate(config);
      if (!validation.valid()) {
        String message = String.join("; ", validation.messages());
        errors.put(id, message);
        lifecycles.put(id, FeatureLifecycle.FAILED);
        context.warn("Feature '" + id + "' was not loaded: " + message);
        continue;
      }
      if (!config.enabled()) {
        lifecycles.put(id, FeatureLifecycle.DISABLED);
        continue;
      }
      try {
        entry.getValue().load(context, config);
        lifecycles.put(id, FeatureLifecycle.LOADED);
      } catch (RuntimeException exception) {
        errors.put(
            id,
            exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage());
        lifecycles.put(id, FeatureLifecycle.FAILED);
        context.warn("Feature '" + id + "' failed to load: " + exception.getMessage());
      }
    }
    for (Map.Entry<String, GameplayFeature> entry : features.entrySet()) {
      if (lifecycles.get(entry.getKey()) != FeatureLifecycle.LOADED) {
        continue;
      }
      try {
        entry.getValue().start();
        lifecycles.put(entry.getKey(), FeatureLifecycle.STARTED);
      } catch (RuntimeException exception) {
        context.clearFeature(entry.getKey());
        errors.put(
            entry.getKey(),
            exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage());
        lifecycles.put(entry.getKey(), FeatureLifecycle.FAILED);
        context.warn("Feature '" + entry.getKey() + "' failed to start: " + exception.getMessage());
      }
    }
  }

  public synchronized void stopAll() {
    for (Map.Entry<String, GameplayFeature> entry : features.entrySet()) {
      FeatureLifecycle lifecycle = lifecycles.get(entry.getKey());
      if (lifecycle == FeatureLifecycle.STARTED || lifecycle == FeatureLifecycle.LOADED) {
        try {
          entry.getValue().stop();
        } catch (RuntimeException exception) {
          context.warn(
              "Feature '" + entry.getKey() + "' failed to stop: " + exception.getMessage());
        }
      }
      context.clearFeature(entry.getKey());
      if (lifecycle != FeatureLifecycle.DISABLED) {
        lifecycles.put(entry.getKey(), FeatureLifecycle.STOPPED);
      }
    }
  }

  public synchronized Optional<GameplayFeature> find(String id) {
    return Optional.ofNullable(features.get(normalize(id)));
  }

  public synchronized FeatureLifecycle lifecycle(String id) {
    return lifecycles.getOrDefault(normalize(id), FeatureLifecycle.STOPPED);
  }

  public synchronized Map<String, FeatureLifecycle> lifecycles() {
    return Collections.unmodifiableMap(new LinkedHashMap<>(lifecycles));
  }

  public synchronized Map<String, String> errors() {
    return Collections.unmodifiableMap(new LinkedHashMap<>(errors));
  }

  private String normalize(String id) {
    return id == null ? "" : id.trim().toLowerCase(java.util.Locale.ROOT);
  }
}
