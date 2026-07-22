package com.dungeonhero.framework.reward;

import com.dungeonhero.framework.GameplayDefinition;
import com.dungeonhero.framework.context.GameplayContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RewardRegistry {
  private final Map<String, GameplayReward> rewards = new LinkedHashMap<>();

  public synchronized void register(GameplayReward reward) {
    String type = normalize(reward == null ? null : reward.type());
    if (type.isBlank()) {
      throw new IllegalArgumentException("A reward type is required.");
    }
    if (rewards.putIfAbsent(type, reward) != null) {
      throw new IllegalArgumentException("Reward type is already registered: " + type);
    }
  }

  public synchronized RewardResult grant(GameplayContext context, GameplayDefinition definition) {
    GameplayReward reward = rewards.get(normalize(definition.type()));
    return reward == null
        ? new RewardResult(false, "Unknown reward: " + definition.type())
        : reward.grant(context, definition);
  }

  public synchronized List<RewardResult> grantAll(
      GameplayContext context, List<GameplayDefinition> definitions) {
    return definitions == null
        ? List.of()
        : definitions.stream().map(definition -> grant(context, definition)).toList();
  }

  public synchronized Map<String, GameplayReward> all() {
    return Map.copyOf(rewards);
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
  }
}
