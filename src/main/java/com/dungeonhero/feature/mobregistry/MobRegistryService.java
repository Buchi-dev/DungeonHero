package com.dungeonhero.feature.mobregistry;

import com.dungeonhero.config.DungeonHeroConfiguration;
import com.dungeonhero.config.MobRegistryConfiguration;
import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Shared MythicMobs registry used by scaling and Sword XP. Mob IDs and profiles are data-driven;
 * integrations can also register IDs at runtime.
 */
public final class MobRegistryService {

  private final JavaPlugin plugin;
  private final File registryFile;
  private final Map<String, MobProfile> runtimeMobs = new LinkedHashMap<>();
  private Map<String, MobProfile> profiles = Map.of();
  private Map<String, MobProfile> mobs = Map.of();
  private MobProfile defaultProfile;
  private DungeonHeroConfiguration.Progression progression;
  private DungeonHeroConfiguration.MobScaling mobScaling;

  public MobRegistryService(JavaPlugin plugin) {
    this(plugin, DungeonHeroConfiguration.load(plugin));
  }

  public MobRegistryService(JavaPlugin plugin, DungeonHeroConfiguration configuration) {
    this.plugin = plugin;
    this.registryFile = new File(plugin.getDataFolder(), "mob-registry.yml");
    reload(configuration, MobRegistryConfiguration.load(registryFile, plugin.getLogger()));
  }

  /** Reloads profiles and mob mappings from plugins/DungeonHero/mob-registry.yml. */
  public synchronized void reload() {
    DungeonHeroConfiguration configuration = DungeonHeroConfiguration.load(plugin);
    reload(configuration, MobRegistryConfiguration.load(registryFile, plugin.getLogger()));
  }

  public synchronized void reload(DungeonHeroConfiguration configuration) {
    reload(configuration, MobRegistryConfiguration.load(registryFile, plugin.getLogger()));
  }

  private synchronized void reload(
      DungeonHeroConfiguration configuration, MobRegistryConfiguration registryConfiguration) {
    progression = configuration.progression();
    mobScaling = configuration.mobScaling();
    Map<String, MobProfile> loadedProfiles = builtInProfiles();
    for (MobRegistryConfiguration.Profile configured : registryConfiguration.profiles().values()) {
      String normalized = normalize(configured.name());
      MobProfile fallback =
          loadedProfiles.getOrDefault(normalized, new MobProfile(normalized, 0, mythicXp()));
      loadedProfiles.put(
          normalized,
          new MobProfile(
              normalized,
              configured.levelOffset() == null ? fallback.levelOffset() : configured.levelOffset(),
              configured.swordXp() == null ? fallback.swordXp() : configured.swordXp(),
              configured.mobType()));
    }

    defaultProfile = loadedProfiles.getOrDefault("normal", new MobProfile("normal", 0, mythicXp()));
    Map<String, MobProfile> loadedMobs = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : registryConfiguration.mobProfiles().entrySet()) {
      String profileName = entry.getValue();
      MobProfile profile = loadedProfiles.get(normalize(profileName));
      if (profile == null) {
        plugin
            .getLogger()
            .warning(
                "Mob "
                    + entry.getKey()
                    + " references unknown profile "
                    + profileName
                    + "; using normal.");
        profile = defaultProfile;
      }
      loadedMobs.put(normalize(entry.getKey()), profile);
    }
    loadedMobs.putAll(runtimeMobs);
    profiles = Collections.unmodifiableMap(new LinkedHashMap<>(loadedProfiles));
    mobs = Collections.unmodifiableMap(loadedMobs);
  }

  /** Returns the configured profile for a MythicMob ID, if explicitly registered. */
  public synchronized Optional<MobProfile> find(String internalName) {
    return Optional.ofNullable(mobs.get(normalize(internalName)));
  }

  /** Returns the configured profile or the safe normal profile for unknown IDs. */
  public synchronized MobProfile profileOrDefault(String internalName) {
    return mobs.getOrDefault(normalize(internalName), defaultProfile);
  }

  public synchronized Map<String, MobProfile> getRegisteredMobs() {
    return mobs;
  }

  public synchronized Map<String, MobProfile> getProfiles() {
    return profiles;
  }

  /** Registers a runtime mapping; it survives DungeonHero reloads until unregistered. */
  public synchronized void register(String internalName, MobProfile profile) {
    String id = normalize(internalName);
    if (id.isEmpty() || profile == null) {
      throw new IllegalArgumentException("Mob ID and profile are required.");
    }
    runtimeMobs.put(id, profile);
    Map<String, MobProfile> updated = new LinkedHashMap<>(mobs);
    updated.put(id, profile);
    mobs = Collections.unmodifiableMap(updated);
  }

  public synchronized boolean unregister(String internalName) {
    String id = normalize(internalName);
    boolean removed = runtimeMobs.remove(id) != null;
    if (removed) {
      reload();
    }
    return removed;
  }

  private Map<String, MobProfile> builtInProfiles() {
    Map<String, MobProfile> defaults = new LinkedHashMap<>();
    defaults.put("normal", new MobProfile("normal", 0, mythicXp()));
    defaults.put("elite", new MobProfile("elite", levelOffset("Elite", 3), eliteXp()));
    defaults.put("miniboss", new MobProfile("miniboss", levelOffset("Miniboss", 6), minibossXp()));
    defaults.put(
        "rare_boss", new MobProfile("rare_boss", levelOffset("RareBoss", 8), rareBossXp()));
    return defaults;
  }

  private int levelOffset(String profile, int fallback) {
    return switch (profile) {
      case "Elite" -> Math.max(0, mobScaling.eliteMinOffset());
      case "Miniboss" -> Math.max(0, mobScaling.minibossMinOffset());
      case "RareBoss" -> Math.max(0, mobScaling.rareBossMinOffset());
      default -> Math.max(0, mobScaling.normalMinOffset());
    };
  }

  private int mythicXp() {
    return progression.mythicMobXp();
  }

  private int eliteXp() {
    return Math.max(mythicXp(), progression.eliteXp());
  }

  private int minibossXp() {
    return Math.max(eliteXp(), progression.minibossXp());
  }

  private int rareBossXp() {
    return Math.max(minibossXp(), progression.rareBossXp());
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  public record MobProfile(String name, int levelOffset, int swordXp, String mobType) {
    public MobProfile(String name, int levelOffset, int swordXp) {
      this(name, levelOffset, swordXp, name);
    }

    public MobProfile {
      name = name == null || name.isBlank() ? "custom" : name.trim().toLowerCase(Locale.ROOT);
      levelOffset = Math.max(0, levelOffset);
      swordXp = Math.max(0, swordXp);
      mobType = mobType == null || mobType.isBlank() ? name : mobType.trim();
    }

    public com.dungeonhero.integration.mythicmobs.MobCombatBalance.MobKind kind() {
      return com.dungeonhero.integration.mythicmobs.MobCombatBalance.MobKind.from(mobType);
    }
  }
}
