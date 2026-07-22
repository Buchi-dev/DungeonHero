package com.dungeonhero.feature.armor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ArmorPolicyTest {

  @Test
  void capsOverflowAndCombinesSetReduction() {
    ArmorCapPolicy caps = new ArmorCapPolicy(new double[] {0, 10}, 100000);
    ArmorProtectionPolicy protection = new ArmorProtectionPolicy(.0015, .15, .01, .20, .40);

    assertEquals(10, caps.effective(25, 1));
    assertEquals(15, caps.overflow(25, 1));
    assertEquals(.085, protection.reduction(10, 2, 3), .000001);
  }

  @Test
  void lastStandNeedsFullSetThresholdLethalDamageAndCooldown() {
    LastStandPolicy policy = new LastStandPolicy(.30);

    assertTrue(policy.evaluate(6, 20, 7, true, true).activate());
    assertTrue(!policy.evaluate(7, 20, 7, true, true).activate());
    assertTrue(!policy.evaluate(6, 20, 7, true, false).activate());
    assertEquals(6, policy.evaluate(6, 20, 7, true, true).resultingHealth());
  }

  @Test
  void setBonusBandsAreDeterministic() {
    ArmorSetBonusPolicy policy = new ArmorSetBonusPolicy();

    assertEquals(0, policy.damageReduction(1));
    assertEquals(.02, policy.damageReduction(2));
    assertEquals(.05, policy.damageReduction(4));
    assertTrue(policy.hasLastStand(4));
  }

  @Test
  void protectionNeverReturnsNegativeDamageOrAcceptsNegativePieces() {
    ArmorProtectionPolicy policy = new ArmorProtectionPolicy(.0015, .15, .01, .20, .40);

    assertEquals(0, policy.apply(-10, 100, 100, -1));
    assertEquals(.035, policy.reduction(10, 2, -1), .000001);
  }
}
