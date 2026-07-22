package com.dungeonhero.feature.sword;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SwordProgressionCalculatorTest {

  private final SwordProgressionCalculator calculator =
      new SwordProgressionCalculator(100, 1.25, 100);

  @Test
  void carriesProgressionStateAcrossLevelBoundaries() {
    HeroSwordState state = new HeroSwordState(1, 0, 12, 2, 3);

    SwordProgressionCalculator.ProgressionResult result = calculator.addExperience(state, 125, 3);

    assertEquals(new HeroSwordState(2, 25, 12, 2, 3), result.state());
    assertEquals(1, result.levelsGained());
  }

  @Test
  void clearsXpAtTheEffectiveLevelCap() {
    SwordProgressionCalculator.ProgressionResult result =
        calculator.addExperience(new HeroSwordState(2, 120, 0, 0, 1), 10_000, 2);

    assertEquals(new HeroSwordState(2, 0, 0, 0, 1), result.state());
    assertEquals(0, result.levelsGained());
  }

  @Test
  void ignoresNegativeExperienceAndUsesDefaultStateForMissingState() {
    SwordProgressionCalculator.ProgressionResult result = calculator.addExperience(null, -50, 3);

    assertEquals(new HeroSwordState(1, 0, 0, 0, 0), result.state());
    assertEquals(0, result.levelsGained());
  }
}
