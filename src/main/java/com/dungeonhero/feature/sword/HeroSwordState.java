package com.dungeonhero.feature.sword;

/** Immutable, Bukkit-free snapshot of the gameplay state stored on a Hero Sword. */
public record HeroSwordState(
    int level, int xp, double damageBonus, int prestige, int fragmentRank) {

  public HeroSwordState {
    level = Math.max(1, level);
    xp = Math.max(0, xp);
    damageBonus = Double.isFinite(damageBonus) ? Math.max(0, damageBonus) : 0;
    prestige = Math.max(0, prestige);
    fragmentRank = Math.max(1, fragmentRank);
  }

  public static HeroSwordState defaults() {
    return new HeroSwordState(1, 0, 0, 0, 1);
  }

  public HeroSwordState withProgression(int newLevel, int newXp) {
    return new HeroSwordState(newLevel, newXp, damageBonus, prestige, fragmentRank);
  }
}
