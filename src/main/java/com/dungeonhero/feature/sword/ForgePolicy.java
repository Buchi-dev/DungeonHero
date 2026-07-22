package com.dungeonhero.feature.sword;

/** Pure forge arithmetic; item validation and persistence remain at the Bukkit edge. */
public final class ForgePolicy {

  private ForgePolicy() {}

  public static Result apply(
      double currentDamage, double damagePerFragment, int quantity, double maximumStoredDamage) {
    if (quantity <= 0) {
      throw new IllegalArgumentException("Forge quantity must be positive.");
    }
    double safeCurrentDamage = finiteOrZero(currentDamage);
    double safeDamagePerFragment = finiteOrZero(damagePerFragment);
    double safeMaximum = Math.max(0, finiteOrZero(maximumStoredDamage));
    double updatedDamage =
        Math.min(safeMaximum, Math.max(0, safeCurrentDamage + safeDamagePerFragment * quantity));
    return new Result(updatedDamage, safeMaximum - updatedDamage);
  }

  private static double finiteOrZero(double value) {
    return Double.isFinite(value) ? value : 0;
  }

  public record Result(double totalDamage, double remainingCapacity) {}
}
