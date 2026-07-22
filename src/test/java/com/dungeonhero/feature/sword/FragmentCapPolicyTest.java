package com.dungeonhero.feature.sword;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FragmentCapPolicyTest {

  @Test
  void keepsStoredOverflowSeparateFromActiveDamage() {
    FragmentCapPolicy policy = FragmentCapPolicy.defaults();

    assertEquals(55, policy.effective(114, 4));
    assertEquals(59, policy.overflow(114, 4));
  }

  @Test
  void sanitizesInvalidDamageAndClampsUnknownRanks() {
    FragmentCapPolicy policy = new FragmentCapPolicy(new double[] {0, 12}, 500);

    assertEquals(0, policy.effective(Double.NaN, 99));
    assertEquals(0, policy.overflow(Double.NEGATIVE_INFINITY, 99));
    assertEquals(12, policy.effective(20, 1));
    assertEquals(20, policy.effective(20, 99));
  }
}
