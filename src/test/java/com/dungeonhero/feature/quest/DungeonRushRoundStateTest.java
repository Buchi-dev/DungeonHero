package com.dungeonhero.feature.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DungeonRushRoundStateTest {

  @Test
  void rejectsWrongKillTypesAndPersistsTheCompletedLeaderboard() {
    DungeonRushConfiguration configuration =
        new DungeonRushConfiguration(
            true,
            List.of("dungeon_world"),
            List.of(),
            List.of(QuestScoringPolicy.QuestType.MOST_MYTHIC_MOBS_KILLED),
            5,
            60,
            5,
            1,
            Map.of());
    DungeonRushRoundState state = new DungeonRushRoundState(new QuestScoringPolicy());
    UUID playerId = UUID.randomUUID();

    state.reset(0, 5);
    state.start(0, configuration, new java.util.Random(0));
    state.recordKill(playerId, "Hero", QuestScoringPolicy.KillType.DUNGEON, 1);
    assertTrue(state.leaderboard().isEmpty());

    state.recordKill(playerId, "Hero", QuestScoringPolicy.KillType.MYTHIC, 2);
    DungeonRushRoundState.RoundResult result = state.finish(1_000, 60);

    assertFalse(state.active());
    assertEquals(1, result.scores().getFirst().kills());
    assertEquals(result.scores(), state.lastResults());
  }
}
