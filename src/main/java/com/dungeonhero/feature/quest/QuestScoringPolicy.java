package com.dungeonhero.feature.quest;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Pure quest eligibility, score accumulation, and leaderboard ordering. */
public final class QuestScoringPolicy {

  public boolean accepts(QuestType questType, KillType killType) {
    return questType == null
        || questType == QuestType.MOST_DUNGEON_MOBS_KILLED
        || killType == KillType.MYTHIC;
  }

  public Score recordKill(
      Score current,
      UUID playerId,
      String name,
      KillType killType,
      QuestType questType,
      long timestamp) {
    if (!accepts(questType, killType)) {
      return current;
    }
    if (current == null) {
      return new Score(playerId, name, 1, timestamp);
    }
    return new Score(
        current.playerId(), current.name(), current.kills() + 1, current.firstKillAt());
  }

  public List<Score> rank(Collection<Score> scores) {
    return scores == null
        ? List.of()
        : scores.stream()
            .sorted(
                Comparator.comparingInt(Score::kills)
                    .reversed()
                    .thenComparingLong(Score::firstKillAt)
                    .thenComparing(Score::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
  }

  public enum KillType {
    DUNGEON,
    MYTHIC
  }

  public enum QuestType {
    MOST_DUNGEON_MOBS_KILLED,
    MOST_MYTHIC_MOBS_KILLED
  }

  public record Score(UUID playerId, String name, int kills, long firstKillAt) {
    public Score {
      if (playerId == null) throw new IllegalArgumentException("Score playerId is required.");
      name = name == null ? "" : name;
      kills = Math.max(0, kills);
      firstKillAt = Math.max(0, firstKillAt);
    }
  }
}
