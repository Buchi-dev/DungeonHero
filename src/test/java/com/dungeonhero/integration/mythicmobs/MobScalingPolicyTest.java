package com.dungeonhero.integration.mythicmobs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MobScalingPolicyTest {

  @Test
  void appliesScalingWithoutAnIntegrationRuntime() {
    MobScalingPolicy policy =
        new MobScalingPolicy(
            104, -2, 4, 0, 4, 1, 4, 2, 4, 400, 40, 1, 3, 8, 18, 6, 12, 25, 50, 0.50);

    assertEquals(
        new MobScalingPolicy.MobLevelRange(38, 44),
        policy.levelRange(40, 0, MobScalingPolicy.MobKind.NORMAL));
    assertEquals(6_000, policy.profileHp(40, MobScalingPolicy.MobKind.ELITE));
    assertEquals(0.5, policy.boundedAmplifierCompensation(2), 0.0001);
  }

  @Test
  void clampsInvalidInputsToSafeGameplayBounds() {
    MobScalingPolicy policy =
        new MobScalingPolicy(
            10, -2, 2, -2, 2, -2, 2, -2, 2, 400, 40, 1, 3, 8, 18, 6, 12, 25, 50, 0.5);

    assertEquals(1, policy.clampLevel(-100));
    assertEquals(10, policy.clampLevel(100));
    assertEquals(0.0, policy.boundedAmplifierCompensation(Double.NaN), 0.0001);
    assertEquals(1, policy.effectiveCombatLevel(-1, -5));
  }
}
