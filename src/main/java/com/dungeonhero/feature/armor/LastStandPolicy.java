package com.dungeonhero.feature.armor;

/** Pure Last Stand threshold and cooldown decision logic. */
public final class LastStandPolicy {

  private final double healthThreshold;

  public LastStandPolicy(double healthThreshold) {
    this.healthThreshold =
        Math.max(0, Math.min(1, Double.isFinite(healthThreshold) ? healthThreshold : .3));
  }

  public Decision evaluate(
      double health,
      double maximumHealth,
      double incomingDamage,
      boolean fullSet,
      boolean offCooldown) {
    double safeMax = Math.max(0, Double.isFinite(maximumHealth) ? maximumHealth : 0);
    double safeHealth = Math.max(0, Double.isFinite(health) ? health : 0);
    double safeDamage = Math.max(0, Double.isFinite(incomingDamage) ? incomingDamage : 0);
    boolean activate =
        fullSet
            && offCooldown
            && safeMax > 0
            && safeHealth <= safeMax * healthThreshold
            && safeDamage >= safeHealth;
    return new Decision(activate, activate ? safeMax * healthThreshold : safeHealth);
  }

  public record Decision(boolean activate, double resultingHealth) {}
}
