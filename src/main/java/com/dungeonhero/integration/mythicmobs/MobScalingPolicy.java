package com.dungeonhero.integration.mythicmobs;

import java.util.concurrent.ThreadLocalRandom;

/** Pure, configuration-driven mob level and health scaling rules. */
public final class MobScalingPolicy {

  private final int maxMobLevel;
  private final int normalMinOffset;
  private final int normalMaxOffset;
  private final int eliteMinOffset;
  private final int eliteMaxOffset;
  private final int minibossMinOffset;
  private final int minibossMaxOffset;
  private final int rareBossMinOffset;
  private final int rareBossMaxOffset;
  private final double normalBaseHp;
  private final double normalHpPerLevel;
  private final double normalMultiplier;
  private final double eliteMultiplier;
  private final double minibossMultiplier;
  private final double rareBossMultiplier;
  private final double normalAttackFloor;
  private final double eliteAttackFloor;
  private final double minibossAttackFloor;
  private final double rareBossAttackFloor;
  private final double maxAmplifierCompensation;

  public MobScalingPolicy(
      int maxMobLevel,
      int normalMinOffset,
      int normalMaxOffset,
      int eliteMinOffset,
      int eliteMaxOffset,
      int minibossMinOffset,
      int minibossMaxOffset,
      int rareBossMinOffset,
      int rareBossMaxOffset,
      double normalBaseHp,
      double normalHpPerLevel,
      double normalMultiplier,
      double eliteMultiplier,
      double minibossMultiplier,
      double rareBossMultiplier,
      double normalAttackFloor,
      double eliteAttackFloor,
      double minibossAttackFloor,
      double rareBossAttackFloor,
      double maxAmplifierCompensation) {
    this.maxMobLevel = Math.max(1, maxMobLevel);
    this.normalMinOffset = Math.min(normalMinOffset, normalMaxOffset);
    this.normalMaxOffset = Math.max(normalMinOffset, normalMaxOffset);
    this.eliteMinOffset = Math.min(eliteMinOffset, eliteMaxOffset);
    this.eliteMaxOffset = Math.max(eliteMinOffset, eliteMaxOffset);
    this.minibossMinOffset = Math.min(minibossMinOffset, minibossMaxOffset);
    this.minibossMaxOffset = Math.max(minibossMinOffset, minibossMaxOffset);
    this.rareBossMinOffset = Math.min(rareBossMinOffset, rareBossMaxOffset);
    this.rareBossMaxOffset = Math.max(rareBossMinOffset, rareBossMaxOffset);
    this.normalBaseHp = safePositive(normalBaseHp, 400);
    this.normalHpPerLevel = safePositive(normalHpPerLevel, 40);
    this.normalMultiplier = safePositive(normalMultiplier, 1);
    this.eliteMultiplier = safePositive(eliteMultiplier, 3);
    this.minibossMultiplier = safePositive(minibossMultiplier, 8);
    this.rareBossMultiplier = safePositive(rareBossMultiplier, 18);
    this.normalAttackFloor = safePositive(normalAttackFloor, 6);
    this.eliteAttackFloor = safePositive(eliteAttackFloor, 12);
    this.minibossAttackFloor = safePositive(minibossAttackFloor, 25);
    this.rareBossAttackFloor = safePositive(rareBossAttackFloor, 50);
    this.maxAmplifierCompensation =
        Math.max(0, Math.min(0.5, finite(maxAmplifierCompensation, 0.5)));
  }

  public int effectiveCombatLevel(int swordLevel, int prestige) {
    int safeSwordLevel = Math.max(1, swordLevel);
    int safePrestige = Math.max(0, prestige);
    long prestigeFloor = 1L + (long) safePrestige * 20L;
    return (int) Math.min(Integer.MAX_VALUE, Math.max(safeSwordLevel, prestigeFloor));
  }

  public MobLevelRange levelRange(int swordLevel, int prestige, MobKind kind) {
    return rangeFor(effectiveCombatLevel(swordLevel, prestige), kind);
  }

  public int selectMobLevel(int swordLevel, int prestige, MobKind kind) {
    MobLevelRange range = levelRange(swordLevel, prestige, kind);
    return ThreadLocalRandom.current().nextInt(range.minimum(), range.maximum() + 1);
  }

  public int clampLevel(int level) {
    return Math.max(1, Math.min(maxMobLevel, level));
  }

  public double profileHp(int mobLevel, MobKind kind) {
    double base = normalBaseHp + (Math.max(1, mobLevel) * normalHpPerLevel);
    return base * multiplier(kind);
  }

  public double minimumHp(int mobLevel, MobKind kind, double strongestNormalHitDamage) {
    return Math.max(0, finite(strongestNormalHitDamage, 0)) * attacksRequired(kind);
  }

  public double finalHp(
      int mobLevel,
      MobKind kind,
      double strongestNormalHitDamage,
      double requestedAmplifierCompensation) {
    double compensation =
        Math.max(0, Math.min(maxAmplifierCompensation, finite(requestedAmplifierCompensation, 0)));
    return Math.max(profileHp(mobLevel, kind), minimumHp(mobLevel, kind, strongestNormalHitDamage))
        * (1 + compensation);
  }

  public double boundedAmplifierCompensation(double requested) {
    return Math.max(0, Math.min(maxAmplifierCompensation, finite(requested, 0)));
  }

  private MobLevelRange rangeFor(int effective, MobKind kind) {
    int minimum;
    int maximum;
    switch (kind == null ? MobKind.NORMAL : kind) {
      case ELITE -> {
        minimum = effective + eliteMinOffset;
        maximum = effective + eliteMaxOffset;
      }
      case MINIBOSS -> {
        minimum = effective + minibossMinOffset;
        maximum = effective + minibossMaxOffset;
      }
      case RARE_BOSS -> {
        minimum = effective + rareBossMinOffset;
        maximum = effective + rareBossMaxOffset;
      }
      default -> {
        minimum = effective + normalMinOffset;
        maximum = effective + normalMaxOffset;
      }
    }
    return new MobLevelRange(clampLevel(minimum), clampLevel(maximum));
  }

  private double multiplier(MobKind kind) {
    return switch (kind == null ? MobKind.NORMAL : kind) {
      case ELITE -> eliteMultiplier;
      case MINIBOSS -> minibossMultiplier;
      case RARE_BOSS -> rareBossMultiplier;
      default -> normalMultiplier;
    };
  }

  private double attacksRequired(MobKind kind) {
    return switch (kind == null ? MobKind.NORMAL : kind) {
      case ELITE -> eliteAttackFloor;
      case MINIBOSS -> minibossAttackFloor;
      case RARE_BOSS -> rareBossAttackFloor;
      default -> normalAttackFloor;
    };
  }

  private static double safePositive(double value, double fallback) {
    return Math.max(0, finite(value, fallback));
  }

  private static double finite(double value, double fallback) {
    return Double.isFinite(value) ? value : fallback;
  }

  public enum MobKind {
    NORMAL,
    ELITE,
    MINIBOSS,
    RARE_BOSS;

    public static MobKind from(String value) {
      if (value == null) return NORMAL;
      return switch (value.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_')) {
        case "ELITE" -> ELITE;
        case "MINIBOSS", "MINI_BOSS" -> MINIBOSS;
        case "RARE_BOSS", "RAREBOSS", "BOSS" -> RARE_BOSS;
        default -> {
          String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
          if (normalized.contains("rare") && normalized.contains("boss")) yield RARE_BOSS;
          if (normalized.contains("mini") && normalized.contains("boss")) yield MINIBOSS;
          yield normalized.contains("elite") ? ELITE : NORMAL;
        }
      };
    }
  }

  public record MobLevelRange(int minimum, int maximum) {
    public MobLevelRange {
      minimum = Math.max(1, minimum);
      maximum = Math.max(minimum, maximum);
    }
  }
}
