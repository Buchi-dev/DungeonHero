package com.dungeonhero.feature.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuestScoringPolicyTest {

  @Test
  void recordsOnlyEligibleKillsAndRanksByKillsThenFirstKill() {
    QuestScoringPolicy policy = new QuestScoringPolicy();
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    QuestScoringPolicy.Score score =
        policy.recordKill(
            null,
            first,
            "First",
            QuestScoringPolicy.KillType.DUNGEON,
            QuestScoringPolicy.QuestType.MOST_MYTHIC_MOBS_KILLED,
            10);

    assertEquals(null, score);
    score =
        policy.recordKill(
            null,
            first,
            "First",
            QuestScoringPolicy.KillType.MYTHIC,
            QuestScoringPolicy.QuestType.MOST_MYTHIC_MOBS_KILLED,
            20);
    score =
        policy.recordKill(
            score,
            first,
            "First",
            QuestScoringPolicy.KillType.MYTHIC,
            QuestScoringPolicy.QuestType.MOST_MYTHIC_MOBS_KILLED,
            30);
    QuestScoringPolicy.Score other =
        policy.recordKill(
            null,
            second,
            "Second",
            QuestScoringPolicy.KillType.MYTHIC,
            QuestScoringPolicy.QuestType.MOST_MYTHIC_MOBS_KILLED,
            10);

    List<QuestScoringPolicy.Score> results = policy.rank(List.of(score, other));
    assertEquals(first, results.getFirst().playerId());
    assertEquals(2, results.getFirst().kills());
  }
}
