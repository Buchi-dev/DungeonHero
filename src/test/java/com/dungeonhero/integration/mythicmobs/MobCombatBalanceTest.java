package com.dungeonhero.integration.mythicmobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MobCombatBalanceTest {

  private final MobCombatBalance balance =
      new MobCombatBalance(104, -2, 4, 0, 4, 1, 4, 2, 4, 400, 40, 1, 3, 8, 18, 6, 12, 25, 50, 0.50);

  @Test
  void swordLevelFortyUsesTheRequestedProfileRanges() {
    assertEquals(
        new MobCombatBalance.MobLevelRange(38, 44),
        balance.levelRange(40, 0, MobCombatBalance.MobKind.NORMAL));
    assertEquals(
        new MobCombatBalance.MobLevelRange(40, 44),
        balance.levelRange(40, 0, MobCombatBalance.MobKind.ELITE));
    assertEquals(
        new MobCombatBalance.MobLevelRange(41, 44),
        balance.levelRange(40, 0, MobCombatBalance.MobKind.MINIBOSS));
    assertEquals(
        new MobCombatBalance.MobLevelRange(42, 44),
        balance.levelRange(40, 0, MobCombatBalance.MobKind.RARE_BOSS));
  }

  @Test
  void prestigeCreatesTheCombatFloorAndLevelCap() {
    assertEquals(21, balance.effectiveCombatLevel(1, 1));
    assertEquals(
        new MobCombatBalance.MobLevelRange(19, 25),
        balance.levelRange(1, 1, MobCombatBalance.MobKind.NORMAL));
    assertEquals(101, balance.effectiveCombatLevel(1, 5));
    assertEquals(
        new MobCombatBalance.MobLevelRange(99, 104),
        balance.levelRange(1, 5, MobCombatBalance.MobKind.NORMAL));
  }

  @Test
  void hpFormulaAndMinimumAttackFloorAreApplied() {
    assertEquals(2_000, balance.profileHp(40, MobCombatBalance.MobKind.NORMAL));
    assertEquals(6_000, balance.profileHp(40, MobCombatBalance.MobKind.ELITE));
    assertTrue(balance.finalHp(40, MobCombatBalance.MobKind.NORMAL, 400, 0) >= 2_400);
    assertEquals(0.5, balance.boundedAmplifierCompensation(2.0), 0.0001);
    assertEquals(1.5 * 2_400, balance.finalHp(40, MobCombatBalance.MobKind.NORMAL, 400, 2), 0.0001);
  }
}
