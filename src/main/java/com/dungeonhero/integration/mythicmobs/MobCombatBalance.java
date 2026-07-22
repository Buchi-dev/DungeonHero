package com.dungeonhero.integration.mythicmobs;

/** Pure, configuration-driven combat math shared by the MythicMobs adapter and tests. */
/**
 * @deprecated Use {@link MobScalingPolicy}. This adapter preserves the original MythicMobs
 *     integration-facing types while keeping the calculations in the domain policy.
 */
@Deprecated(forRemoval = false)
public final class MobCombatBalance {

  private final MobScalingPolicy delegate;

  public MobCombatBalance(
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
    this.delegate =
        new MobScalingPolicy(
            maxMobLevel,
            normalMinOffset,
            normalMaxOffset,
            eliteMinOffset,
            eliteMaxOffset,
            minibossMinOffset,
            minibossMaxOffset,
            rareBossMinOffset,
            rareBossMaxOffset,
            normalBaseHp,
            normalHpPerLevel,
            normalMultiplier,
            eliteMultiplier,
            minibossMultiplier,
            rareBossMultiplier,
            normalAttackFloor,
            eliteAttackFloor,
            minibossAttackFloor,
            rareBossAttackFloor,
            maxAmplifierCompensation);
  }

  public int effectiveCombatLevel(int swordLevel, int prestige) {
    return delegate.effectiveCombatLevel(swordLevel, prestige);
  }

  public MobLevelRange levelRange(int swordLevel, int prestige, MobKind kind) {
    MobScalingPolicy.MobLevelRange range =
        delegate.levelRange(swordLevel, prestige, toDomain(kind));
    return new MobLevelRange(range.minimum(), range.maximum());
  }

  public int selectMobLevel(int swordLevel, int prestige, MobKind kind) {
    return delegate.selectMobLevel(swordLevel, prestige, toDomain(kind));
  }

  public int clampLevel(int level) {
    return delegate.clampLevel(level);
  }

  public double profileHp(int mobLevel, MobKind kind) {
    return delegate.profileHp(mobLevel, toDomain(kind));
  }

  public double minimumHp(int mobLevel, MobKind kind, double strongestNormalHitDamage) {
    return delegate.minimumHp(mobLevel, toDomain(kind), strongestNormalHitDamage);
  }

  public double finalHp(
      int mobLevel,
      MobKind kind,
      double strongestNormalHitDamage,
      double requestedAmplifierCompensation) {
    return delegate.finalHp(
        mobLevel, toDomain(kind), strongestNormalHitDamage, requestedAmplifierCompensation);
  }

  public double boundedAmplifierCompensation(double requested) {
    return delegate.boundedAmplifierCompensation(requested);
  }

  /** Returns the canonical framework-free policy used by this compatibility facade. */
  public MobScalingPolicy policy() {
    return delegate;
  }

  private static MobScalingPolicy.MobKind toDomain(MobKind kind) {
    return kind == null
        ? MobScalingPolicy.MobKind.NORMAL
        : MobScalingPolicy.MobKind.valueOf(kind.name());
  }

  public enum MobKind {
    NORMAL,
    ELITE,
    MINIBOSS,
    RARE_BOSS;

    public static MobKind from(String value) {
      if (value == null) {
        return NORMAL;
      }
      return switch (value.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_')) {
        case "ELITE" -> ELITE;
        case "MINIBOSS", "MINI_BOSS" -> MINIBOSS;
        case "RARE_BOSS", "RAREBOSS", "BOSS" -> RARE_BOSS;
        default -> {
          String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
          if (normalized.contains("rare") && normalized.contains("boss")) {
            yield RARE_BOSS;
          }
          if (normalized.contains("mini") && normalized.contains("boss")) {
            yield MINIBOSS;
          }
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
