package com.dungeonhero.feature.sword;

import java.util.Comparator;

/** Orders swords strongest first using the same priority as gameplay selection. */
public final class SwordComparator implements Comparator<HeroSwordState> {

  @Override
  public int compare(HeroSwordState first, HeroSwordState second) {
    if (first == second) {
      return 0;
    }
    if (first == null) {
      return 1;
    }
    if (second == null) {
      return -1;
    }
    int level = Integer.compare(second.level(), first.level());
    if (level != 0) return level;
    int prestige = Integer.compare(second.prestige(), first.prestige());
    if (prestige != 0) return prestige;
    return Double.compare(second.damageBonus(), first.damageBonus());
  }

  public boolean isStronger(HeroSwordState first, HeroSwordState second) {
    return compare(first, second) < 0;
  }
}
