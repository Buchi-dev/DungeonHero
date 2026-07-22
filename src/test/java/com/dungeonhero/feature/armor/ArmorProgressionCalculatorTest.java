package com.dungeonhero.feature.armor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ArmorProgressionCalculatorTest {

  @Test
  void stopsAtEffectiveCapAndClearsCappedXp() {
    ArmorProgressionCalculator calculator = new ArmorProgressionCalculator(10, 1, 100);

    var result = calculator.addExperience(new HeroArmorState(1, 0, 0, 1), 100, 3);

    assertEquals(3, result.state().level());
    assertEquals(0, result.state().xp());
    assertEquals(2, result.levelsGained());
  }

  @Test
  void followsTheSwordXpCurveAndSanitizesInvalidMultiplier() {
    ArmorProgressionCalculator calculator = new ArmorProgressionCalculator(100, Double.NaN, 100);

    assertEquals(100, calculator.requiredXp(1));
    assertEquals(100, calculator.requiredXp(20));
  }
}
