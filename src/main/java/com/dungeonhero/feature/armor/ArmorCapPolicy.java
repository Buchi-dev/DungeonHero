package com.dungeonhero.feature.armor;

/** Pure stored/effective armor fragment cap and overflow rules. */
public final class ArmorCapPolicy {

  private static final double[] DEFAULT_CAPS = {0, 10, 20, 35, 55, 80, 110, 145, 185, 230, 280};
  private final double[] caps;
  private final double maximumStoredArmor;

  public ArmorCapPolicy(double[] configuredCaps, double maximumStoredArmor) {
    caps = DEFAULT_CAPS.clone();
    if (configuredCaps != null) {
      for (int rank = 1; rank < Math.min(caps.length, configuredCaps.length); rank++) {
        if (Double.isFinite(configuredCaps[rank])) caps[rank] = Math.max(0, configuredCaps[rank]);
      }
    }
    this.maximumStoredArmor =
        Math.max(0, Double.isFinite(maximumStoredArmor) ? maximumStoredArmor : 100000);
  }

  public static ArmorCapPolicy defaults() {
    return new ArmorCapPolicy(null, 100000);
  }

  public double cap(int rank) {
    return caps[Math.max(1, Math.min(caps.length - 1, rank))];
  }

  public double sanitizeTotal(double total) {
    return Math.max(0, Math.min(maximumStoredArmor, Double.isFinite(total) ? total : 0));
  }

  public double effective(double total, int rank) {
    return Math.min(sanitizeTotal(total), cap(rank));
  }

  public double overflow(double total, int rank) {
    return Math.max(0, sanitizeTotal(total) - effective(total, rank));
  }

  public double maximumStoredArmor() {
    return maximumStoredArmor;
  }
}
