package com.dungeonhero.feature.armor;

/** Immutable, Bukkit-free snapshot of the shared Hero Armor progression. */
public record HeroArmorState(int level, int xp, double armorBonus, int fragmentRank) {

  public HeroArmorState {
    level = Math.max(1, level);
    xp = Math.max(0, xp);
    armorBonus = Double.isFinite(armorBonus) ? Math.max(0, armorBonus) : 0;
    fragmentRank = Math.max(1, fragmentRank);
  }

  public static HeroArmorState defaults() {
    return new HeroArmorState(1, 0, 0, 1);
  }

  public HeroArmorState withProgression(int newLevel, int newXp) {
    return new HeroArmorState(newLevel, newXp, armorBonus, fragmentRank);
  }

  public HeroArmorState withFragmentRank(int rank) {
    return new HeroArmorState(level, xp, armorBonus, rank);
  }
}
