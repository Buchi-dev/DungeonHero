package com.dungeonhero.feature.quest;

import java.util.Locale;

/** Pure reward parsing and normalization; delivery stays at the Bukkit edge. */
public final class RewardPolicy {

  public Reward parse(String rawType, long amount, String material, String command) {
    String normalized = rawType == null ? "" : rawType.trim().toLowerCase(Locale.ROOT);
    if (normalized.isBlank()) {
      return null;
    }
    return new Reward(
        Type.from(normalized),
        amount,
        material == null ? "" : material.trim(),
        command == null ? "" : command);
  }

  public Reward fallbackCoins(long amount) {
    return new Reward(Type.COINS, amount, "", "");
  }

  public Reward fallbackSwordXp(long amount) {
    return new Reward(Type.SWORD_XP, amount, "", "");
  }

  public enum Type {
    COINS("coins"),
    SWORD_XP("sword_xp"),
    ITEM("item"),
    COMMAND("command"),
    UNKNOWN("unknown");

    private final String id;

    Type(String id) {
      this.id = id;
    }

    public static Type from(String value) {
      for (Type type : values()) {
        if (type.id.equals(value)) return type;
      }
      return UNKNOWN;
    }
  }

  public record Reward(Type type, long amount, String material, String command) {}
}
