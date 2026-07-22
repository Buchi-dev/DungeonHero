package com.dungeonhero.feature.armor;

import com.dungeonhero.feature.sword.HeroSwordState;
import com.dungeonhero.feature.sword.SwordProgressionCalculator;

/** Pure shared armor XP and level-cap calculation. */
public final class ArmorProgressionCalculator {

  private final SwordProgressionCalculator progressionCalculator;

  public ArmorProgressionCalculator(
      int baseXpRequired, double xpRequiredMultiplier, int maxArmorLevel) {
    progressionCalculator =
        new SwordProgressionCalculator(
            baseXpRequired,
            Double.isFinite(xpRequiredMultiplier) ? xpRequiredMultiplier : 1,
            maxArmorLevel);
  }

  public int requiredXp(int level) {
    return progressionCalculator.requiredXp(level);
  }

  public ProgressionResult addExperience(HeroArmorState state, int amount, int levelCap) {
    HeroArmorState safe = state == null ? HeroArmorState.defaults() : state;
    SwordProgressionCalculator.ProgressionResult result =
        progressionCalculator.addExperience(
            new HeroSwordState(safe.level(), safe.xp(), 0, 0, safe.fragmentRank()),
            amount,
            levelCap);
    return new ProgressionResult(
        safe.withProgression(result.state().level(), result.state().xp()), result.levelsGained());
  }

  public record ProgressionResult(HeroArmorState state, int levelsGained) {}
}
