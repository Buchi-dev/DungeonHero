package com.dungeonhero.feature.quest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Mutable application state for one Dungeon Rush lifecycle; no Bukkit dependencies. */
public final class DungeonRushRoundState {

  private final DungeonRushLeaderboard leaderboard;
  private final QuestScoringPolicy scoringPolicy;
  private final Map<UUID, QuestScoringPolicy.Score> scores = new LinkedHashMap<>();
  private boolean active;
  private long endsAt;
  private long nextStartAt;
  private long roundNumber;
  private QuestScoringPolicy.QuestType currentQuest;
  private String currentBiome;
  private List<QuestScoringPolicy.Score> lastResults = List.of();
  private QuestScoringPolicy.QuestType lastQuest;
  private String lastBiome;

  public DungeonRushRoundState(QuestScoringPolicy scoringPolicy) {
    this.scoringPolicy = scoringPolicy;
    this.leaderboard = new DungeonRushLeaderboard(scoringPolicy);
  }

  public void reset(long now, int firstDelayMinutes) {
    active = false;
    scores.clear();
    currentQuest = null;
    currentBiome = null;
    nextStartAt = now + firstDelayMinutes * 60_000L;
  }

  public void clear() {
    active = false;
    scores.clear();
    currentQuest = null;
    currentBiome = null;
  }

  public void start(long now, DungeonRushConfiguration config, Random random) {
    scores.clear();
    currentQuest = config.questTypes().get(random.nextInt(config.questTypes().size()));
    currentBiome =
        config.biomes().isEmpty()
            ? "All Biomes"
            : config.biomes().get(random.nextInt(config.biomes().size()));
    roundNumber++;
    endsAt = now + config.durationMinutes() * 60_000L;
    active = true;
  }

  public void recordKill(
      UUID playerId, String name, QuestScoringPolicy.KillType killType, long timestamp) {
    if (!active || currentQuest == null) return;
    QuestScoringPolicy.Score score = leaderboardScore(playerId, name, killType, timestamp);
    if (score != null) scores.put(playerId, score);
  }

  public RoundResult finish(long now, int intervalMinutes) {
    List<QuestScoringPolicy.Score> results = leaderboard.rank(scores.values());
    RoundResult result = new RoundResult(currentQuest, currentBiome, results, roundNumber);
    lastResults = results;
    lastQuest = currentQuest;
    lastBiome = currentBiome;
    active = false;
    currentQuest = null;
    currentBiome = null;
    nextStartAt = now + intervalMinutes * 60_000L;
    return result;
  }

  public List<QuestScoringPolicy.Score> leaderboard() {
    return leaderboard.rank(scores.values());
  }

  public boolean active() {
    return active;
  }

  public long endsAt() {
    return endsAt;
  }

  public long nextStartAt() {
    return nextStartAt;
  }

  public long roundNumber() {
    return roundNumber;
  }

  public QuestScoringPolicy.QuestType currentQuest() {
    return currentQuest;
  }

  public String currentBiome() {
    return currentBiome;
  }

  public List<QuestScoringPolicy.Score> lastResults() {
    return lastResults;
  }

  public QuestScoringPolicy.QuestType lastQuest() {
    return lastQuest;
  }

  public String lastBiome() {
    return lastBiome;
  }

  private QuestScoringPolicy.Score leaderboardScore(
      UUID playerId, String name, QuestScoringPolicy.KillType killType, long timestamp) {
    QuestScoringPolicy.Score current = scores.get(playerId);
    return scoringPolicy.recordKill(current, playerId, name, killType, currentQuest, timestamp);
  }

  public record RoundResult(
      QuestScoringPolicy.QuestType quest,
      String biome,
      List<QuestScoringPolicy.Score> scores,
      long roundNumber) {}
}
