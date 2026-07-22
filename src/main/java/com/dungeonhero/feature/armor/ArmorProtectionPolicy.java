package com.dungeonhero.feature.armor;

/** Bukkit-free deterministic damage reduction policy for Hero Armor. */
public final class ArmorProtectionPolicy {

  private final double levelReductionPerLevel;
  private final double maxLevelReduction;
  private final double fragmentReductionPerPoint;
  private final double maxFragmentReduction;
  private final double maxTotalReduction;

  public ArmorProtectionPolicy(
      double levelReductionPerLevel,
      double maxLevelReduction,
      double fragmentReductionPerPoint,
      double maxFragmentReduction,
      double maxTotalReduction) {
    this.levelReductionPerLevel = nonNegative(levelReductionPerLevel);
    this.maxLevelReduction = nonNegative(maxLevelReduction);
    this.fragmentReductionPerPoint = nonNegative(fragmentReductionPerPoint);
    this.maxFragmentReduction = nonNegative(maxFragmentReduction);
    this.maxTotalReduction = Math.min(1, nonNegative(maxTotalReduction));
  }

  public double reduction(int armorLevel, double effectiveArmorBonus, int equippedPieces) {
    double level = Math.min(maxLevelReduction, Math.max(0, armorLevel) * levelReductionPerLevel);
    double fragments =
        Math.min(
            maxFragmentReduction, nonNegative(effectiveArmorBonus) * fragmentReductionPerPoint);
    int safePieces = Math.max(0, equippedPieces);
    double set = safePieces >= 3 ? 0.05 : safePieces >= 2 ? 0.02 : 0;
    return Math.min(maxTotalReduction, level + fragments + set);
  }

  public double apply(
      double damage, int armorLevel, double effectiveArmorBonus, int equippedPieces) {
    double safeDamage = Double.isFinite(damage) ? Math.max(0, damage) : 0;
    return Math.max(
        0, safeDamage * (1 - reduction(armorLevel, effectiveArmorBonus, equippedPieces)));
  }

  private static double nonNegative(double value) {
    return Double.isFinite(value) ? Math.max(0, value) : 0;
  }
}
