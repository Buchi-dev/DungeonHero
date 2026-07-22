package com.dungeonhero.feature.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class RewardPolicyTest {

  @Test
  void normalizesConfiguredRewardTypesAtTheDomainBoundary() {
    RewardPolicy.Reward reward = new RewardPolicy().parse(" ITEM ", 3, " diamond ", "");

    assertEquals(RewardPolicy.Type.ITEM, reward.type());
    assertEquals("diamond", reward.material());
    assertEquals(3, reward.amount());
  }

  @Test
  void rejectsBlankRewardTypesAtTheConfigurationBoundary() {
    assertNull(new RewardPolicy().parse("  ", 3, "diamond", "give %player%"));
  }
}
