package com.dungeonhero.feature.sword;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ForgePolicyTest {

  @Test
  void appliesEachFragmentExactlyOnceAndCapsStoredDamage() {
    ForgePolicy.Result result = ForgePolicy.apply(4, 2, 3, 10);

    assertEquals(10, result.totalDamage(), 0.0001);
    assertEquals(0, result.remainingCapacity(), 0.0001);
  }

  @Test
  void rejectsNonPositiveForgeQuantities() {
    assertThrows(IllegalArgumentException.class, () -> ForgePolicy.apply(0, 2, 0, 100));
  }
}
