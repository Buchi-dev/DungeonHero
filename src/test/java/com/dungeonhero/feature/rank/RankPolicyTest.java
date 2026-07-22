package com.dungeonhero.feature.rank;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RankPolicyTest {

  private final RankPolicy policy = new RankPolicy();

  @Test
  void calculatesTheLowerConfiguredAndRankCap() {
    assertEquals(20, policy.effectiveSwordLevelCap(100, 20));
  }

  @Test
  void reportsSwordAndCoinRequirementsBeforePayment() {
    RankPolicy.Rank current = new RankPolicy.Rank(1, 1, 10, 0);
    RankPolicy.Rank next = new RankPolicy.Rank(2, 20, 20, 250);

    assertEquals(RankPolicy.Status.SWORD_LEVEL, policy.evaluate(current, next, 19, 500).status());
    assertEquals(
        RankPolicy.Status.INSUFFICIENT_FUNDS, policy.evaluate(current, next, 20, 249).status());
    assertEquals(RankPolicy.Status.ELIGIBLE, policy.evaluate(current, next, 20, 250).status());
  }

  @Test
  void rejectsMissingCurrentRankAndCapsAtMaximumRank() {
    RankPolicy.Rank next = new RankPolicy.Rank(2, 20, 20, 250);

    assertEquals(
        RankPolicy.Status.INVALID_CURRENT_RANK, policy.evaluate(null, next, 20, 250).status());
    assertEquals(RankPolicy.Status.MAX_RANK, policy.evaluate(null, null, 20, 250).status());
  }
}
