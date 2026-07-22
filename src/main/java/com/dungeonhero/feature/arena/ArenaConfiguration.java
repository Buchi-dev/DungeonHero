package com.dungeonhero.feature.arena;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

/** Immutable configuration for the protected, prebuilt Blood Arena. */
public record ArenaConfiguration(
    boolean enabled,
    String arenaWorld,
    String arenaId,
    double targetRadius,
    int maximumPlayers,
    long durationSeconds,
    boolean returnPlayersAfterFight,
    boolean preventTeleport,
    boolean preventPearls,
    LocationSpec playerSpawn,
    LocationSpec bossSpawn,
    String boundaryParticle,
    String entranceSound,
    String combatSound,
    List<Phase> phases,
    List<String> rewardCommands,
    boolean rewardOnTimeout) {

  private static final String PATH = "DungeonHero.Gameplay.Features.blood-arena";

  public static ArenaConfiguration load(JavaPlugin plugin) {
    ConfigurationSection section = plugin.getConfig().getConfigurationSection(PATH);
    if (section == null) return defaults();

    return new ArenaConfiguration(
        section.getBoolean("Enabled", true),
        text(section.getString("ArenaWorld", "dungeon_arenas"), "dungeon_arenas"),
        text(section.getString("ArenaId", "crypt_colosseum"), "crypt_colosseum"),
        bounded(section.getDouble("TargetRadius", 24), 1, 128),
        Math.max(1, section.getInt("MaximumPlayers", 5)),
        Math.max(1, section.getLong("DurationSeconds", 45)),
        section.getBoolean("ReturnPlayersAfterFight", true),
        section.getBoolean("PreventTeleport", true),
        section.getBoolean("PreventPearls", true),
        location(section.getConfigurationSection("PlayerSpawn"), new LocationSpec(0, 100, 0, 0, 0)),
        location(section.getConfigurationSection("BossSpawn"), new LocationSpec(0, 100, 0, 180, 0)),
        text(section.getString("Effects.BoundaryParticle", "REDSTONE"), "REDSTONE"),
        text(
            section.getString("Effects.EntranceSound", "ENTITY_WITHER_SPAWN"),
            "ENTITY_WITHER_SPAWN"),
        text(
            section.getString("Effects.CombatSound", "ENTITY_ENDER_DRAGON_GROWL"),
            "ENTITY_ENDER_DRAGON_GROWL"),
        readPhases(section.getMapList("Phases")),
        List.copyOf(section.getStringList("Rewards.Commands")),
        section.getBoolean("Rewards.RewardOnTimeout", false));
  }

  public static ArenaConfiguration defaults() {
    return new ArenaConfiguration(
        false,
        "dungeon_arenas",
        "crypt_colosseum",
        24,
        5,
        45,
        true,
        true,
        true,
        new LocationSpec(0, 100, 0, 0, 0),
        new LocationSpec(0, 100, 0, 180, 0),
        "REDSTONE",
        "ENTITY_WITHER_SPAWN",
        "ENTITY_ENDER_DRAGON_GROWL",
        List.of(),
        List.of(),
        false);
  }

  private static List<Phase> readPhases(List<java.util.Map<?, ?>> rawPhases) {
    List<Phase> phases = new ArrayList<>();
    for (java.util.Map<?, ?> raw : rawPhases) {
      Object action = raw.get("Action");
      if (action == null) continue;
      String normalizedAction = String.valueOf(action).trim().toLowerCase(Locale.ROOT);
      if (normalizedAction.isBlank()) continue;
      phases.add(
          new Phase(
              Math.max(0, number(raw.get("DelaySeconds"), 0)),
              normalizedAction,
              String.valueOf(raw.get("Mob") == null ? "" : raw.get("Mob")),
              Math.max(1, number(raw.get("Amount"), 1)),
              String.valueOf(raw.get("Effect") == null ? "" : raw.get("Effect")),
              Math.max(1, number(raw.get("DurationSeconds"), 5))));
    }
    return List.copyOf(phases);
  }

  private static LocationSpec location(ConfigurationSection section, LocationSpec fallback) {
    if (section == null) return fallback;
    return new LocationSpec(
        section.getDouble("X", fallback.x()),
        section.getDouble("Y", fallback.y()),
        section.getDouble("Z", fallback.z()),
        (float) section.getDouble("Yaw", fallback.yaw()),
        (float) section.getDouble("Pitch", fallback.pitch()));
  }

  private static int number(Object value, int fallback) {
    if (value instanceof Number number) return number.intValue();
    try {
      return Integer.parseInt(String.valueOf(value));
    } catch (RuntimeException ignored) {
      return fallback;
    }
  }

  private static double bounded(double value, double minimum, double maximum) {
    return Double.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : minimum;
  }

  private static String text(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  public record LocationSpec(double x, double y, double z, float yaw, float pitch) {}

  public record Phase(
      int delaySeconds,
      String action,
      String mob,
      int amount,
      String effect,
      int durationSeconds) {}
}
