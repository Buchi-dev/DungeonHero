package com.dungeonhero.feature.rank;

/** Pure rank requirements, payment checks, and sword-cap rules. */
public final class RankPolicy {

  public int effectiveSwordLevelCap(int configuredMaxSwordLevel, int rankSwordLevelCap) {
    return Math.min(configuredMaxSwordLevel, rankSwordLevelCap);
  }

  public RankUpDecision evaluate(Rank current, Rank next, int swordLevel, long balance) {
    if (next == null) {
      return new RankUpDecision(
          Status.MAX_RANK, 0, Math.max(0, swordLevel), 0, Math.max(0, balance));
    }
    int safeSwordLevel = Math.max(0, swordLevel);
    long safeBalance = Math.max(0, balance);
    if (current == null) {
      return new RankUpDecision(
          Status.INVALID_CURRENT_RANK,
          next.requiredSwordLevel(),
          safeSwordLevel,
          next.cost(),
          safeBalance);
    }
    if (safeSwordLevel < next.requiredSwordLevel()) {
      return new RankUpDecision(
          Status.SWORD_LEVEL, next.requiredSwordLevel(), safeSwordLevel, next.cost(), safeBalance);
    }
    if (safeBalance < next.cost()) {
      return new RankUpDecision(
          Status.INSUFFICIENT_FUNDS,
          next.requiredSwordLevel(),
          safeSwordLevel,
          next.cost(),
          safeBalance);
    }
    return new RankUpDecision(
        Status.ELIGIBLE, next.requiredSwordLevel(), safeSwordLevel, next.cost(), safeBalance);
  }

  public record Rank(int number, int requiredSwordLevel, int swordLevelCap, long cost) {
    public Rank {
      number = Math.max(1, number);
      requiredSwordLevel = Math.max(1, requiredSwordLevel);
      swordLevelCap = Math.max(1, swordLevelCap);
      cost = Math.max(0, cost);
    }
  }

  public record RankUpDecision(
      Status status, int requiredSwordLevel, int actualSwordLevel, long cost, long balance) {}

  public enum Status {
    ELIGIBLE,
    MAX_RANK,
    INVALID_CURRENT_RANK,
    SWORD_LEVEL,
    INSUFFICIENT_FUNDS
  }
}
