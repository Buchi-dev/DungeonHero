package com.dungeonhero.feature.mobregistry;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Shared MythicMobs registry used by scaling and Sword XP.
 * Mob IDs and profiles are data-driven; integrations can also register IDs at runtime.
 */
public final class MobRegistryService {

    private final JavaPlugin plugin;
    private final File registryFile;
    private final Map<String, MobProfile> runtimeMobs = new LinkedHashMap<>();
    private Map<String, MobProfile> profiles = Map.of();
    private Map<String, MobProfile> mobs = Map.of();
    private MobProfile defaultProfile;

    public MobRegistryService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.registryFile = new File(plugin.getDataFolder(), "mob-registry.yml");
        reload();
    }

    /** Reloads profiles and mob mappings from plugins/DungeonHero/mob-registry.yml. */
    public synchronized void reload() {
        Map<String, MobProfile> loadedProfiles = builtInProfiles();
        if (registryFile.isFile()) {
            YamlConfiguration configuration = YamlConfiguration.loadConfiguration(registryFile);
            ConfigurationSection profileSection = configuration.getConfigurationSection("Profiles");
            if (profileSection != null) {
                for (String key : profileSection.getKeys(false)) {
                    ConfigurationSection section = profileSection.getConfigurationSection(key);
                    if (section == null) {
                        plugin.getLogger().warning("Ignoring mob profile without a section: " + key);
                        continue;
                    }
                    String normalized = normalize(key);
                    MobProfile fallback = loadedProfiles.getOrDefault(normalized,
                            new MobProfile(normalized, 0, mythicXp()));
                    loadedProfiles.put(normalized, readProfile(normalized, section, fallback));
                }
            }
        }

        defaultProfile = loadedProfiles.getOrDefault("normal",
                new MobProfile("normal", 0, mythicXp()));
        Map<String, MobProfile> loadedMobs = new LinkedHashMap<>();
        if (registryFile.isFile()) {
            YamlConfiguration configuration = YamlConfiguration.loadConfiguration(registryFile);
            ConfigurationSection mobSection = configuration.getConfigurationSection("Mobs");
            if (mobSection != null) {
                for (String key : mobSection.getKeys(false)) {
                    String profileName = mobSection.getString(key + ".Profile", "normal");
                    MobProfile profile = loadedProfiles.get(normalize(profileName));
                    if (profile == null) {
                        plugin.getLogger().warning("Mob " + key + " references unknown profile " + profileName
                                + "; using normal.");
                        profile = defaultProfile;
                    }
                    loadedMobs.put(normalize(key), profile);
                }
            }
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
        defaults.put("rare_boss", new MobProfile("rare_boss", levelOffset("RareBoss", 8), rareBossXp()));
        return defaults;
    }

    private MobProfile readProfile(String name, ConfigurationSection section, MobProfile fallback) {
        return new MobProfile(name,
                Math.max(0, section.getInt("LevelOffset", fallback.levelOffset())),
                Math.max(0, section.getInt("SwordXP", fallback.swordXp())));
    }

    private int levelOffset(String profile, int fallback) {
        return Math.max(0, plugin.getConfig().getInt("DungeonHero.MobScaling.LevelOffsets." + profile, fallback));
    }

    private int mythicXp() {
        return Math.max(1, plugin.getConfig().getInt("DungeonHero.Progression.MythicMobXP", 25));
    }

    private int eliteXp() {
        return Math.max(mythicXp(), plugin.getConfig().getInt("DungeonHero.Progression.EliteXP", 100));
    }

    private int minibossXp() {
        return Math.max(eliteXp(), plugin.getConfig().getInt("DungeonHero.Progression.MinibossXP", 400));
    }

    private int rareBossXp() {
        return Math.max(minibossXp(), plugin.getConfig().getInt("DungeonHero.Progression.RareBossXP", 1000));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record MobProfile(String name, int levelOffset, int swordXp) {
        public MobProfile {
            name = name == null || name.isBlank() ? "custom" : name.trim().toLowerCase(Locale.ROOT);
            levelOffset = Math.max(0, levelOffset);
            swordXp = Math.max(0, swordXp);
        }
    }
}
