package com.dungeonhero.feature.quest;

import java.util.Collection;
import java.util.List;

/** Application-facing leaderboard ordering for Dungeon Rush rounds. */
public final class DungeonRushLeaderboard {

  private final QuestScoringPolicy scoringPolicy;

  public DungeonRushLeaderboard(QuestScoringPolicy scoringPolicy) {
    this.scoringPolicy = scoringPolicy;
  }

  public List<QuestScoringPolicy.Score> rank(Collection<QuestScoringPolicy.Score> scores) {
    return scoringPolicy.rank(scores);
  }
}
