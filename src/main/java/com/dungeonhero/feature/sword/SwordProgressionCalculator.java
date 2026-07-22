package com.dungeonhero.feature.sword;

/** Pure sword XP, level progression, and effective-cap calculations. */
public final class SwordProgressionCalculator {

  private final int baseXpRequired;
  private final double xpRequiredMultiplier;
  private final int maxSwordLevel;

  public SwordProgressionCalculator(
      int baseXpRequired, double xpRequiredMultiplier, int maxSwordLevel) {
    this.baseXpRequired = Math.max(1, baseXpRequired);
    this.xpRequiredMultiplier = Math.max(1.0, xpRequiredMultiplier);
    this.maxSwordLevel = Math.max(1, maxSwordLevel);
  }

  public int requiredXp(int level) {
    return Math.max(
        1,
        (int) Math.round(baseXpRequired * Math.pow(xpRequiredMultiplier, Math.max(0, level - 1))));
  }

  public ProgressionResult addExperience(HeroSwordState state, int xpAmount, int levelCap) {
    HeroSwordState safeState = state == null ? HeroSwordState.defaults() : state;
    int currentLevel = safeState.level();
    int currentXp = safeState.xp();
    int levelsGained = 0;
    int remainingXp = Math.max(0, xpAmount);
    int effectiveCap = Math.max(1, Math.min(maxSwordLevel, levelCap));

    while (currentLevel < effectiveCap && remainingXp > 0) {
      int needed = Math.max(0, requiredXp(currentLevel) - currentXp);
      if (remainingXp < needed) {
        currentXp += remainingXp;
        remainingXp = 0;
        break;
      }

      remainingXp -= needed;
      currentLevel++;
      levelsGained++;
      currentXp = 0;
    }

    if (currentLevel >= effectiveCap) {
      currentXp = 0;
    }
    return new ProgressionResult(safeState.withProgression(currentLevel, currentXp), levelsGained);
  }

  public record ProgressionResult(HeroSwordState state, int levelsGained) {}
}
