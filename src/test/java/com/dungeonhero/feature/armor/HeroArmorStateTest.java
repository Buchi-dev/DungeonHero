package com.dungeonhero.feature.armor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HeroArmorStateTest {

  @Test
  void sanitizesPersistedValues() {
    HeroArmorState state = new HeroArmorState(-4, -2, Double.NaN, 0);

    assertEquals(1, state.level());
    assertEquals(0, state.xp());
    assertEquals(0, state.armorBonus());
    assertEquals(1, state.fragmentRank());
  }

  @Test
  void sanitizesInfiniteBonusAndPreservesFiniteProgression() {
    HeroArmorState state = new HeroArmorState(4, 12, Double.POSITIVE_INFINITY, 3);

    assertEquals(4, state.level());
    assertEquals(12, state.xp());
    assertEquals(0, state.armorBonus());
    assertEquals(3, state.fragmentRank());
  }
}
