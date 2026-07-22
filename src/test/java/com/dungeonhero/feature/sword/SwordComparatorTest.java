package com.dungeonhero.feature.sword;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SwordComparatorTest {

  private final SwordComparator comparator = new SwordComparator();

  @Test
  void comparesLevelThenPrestigeThenDamage() {
    assertTrue(
        comparator.isStronger(
            new HeroSwordState(4, 0, 0, 0, 1), new HeroSwordState(3, 0, 999, 5, 1)));
    assertTrue(
        comparator.isStronger(
            new HeroSwordState(3, 0, 0, 2, 1), new HeroSwordState(3, 0, 999, 1, 1)));
    assertTrue(
        comparator.isStronger(
            new HeroSwordState(3, 0, 12, 1, 1), new HeroSwordState(3, 0, 11, 1, 1)));
  }
}
