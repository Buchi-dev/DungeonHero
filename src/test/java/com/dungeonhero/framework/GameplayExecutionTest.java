package com.dungeonhero.framework;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dungeonhero.framework.context.GameplayContext;
import com.dungeonhero.framework.context.PlayerContext;
import com.dungeonhero.framework.objective.DefeatMobsObjective;
import com.dungeonhero.framework.objective.ObjectiveResult;
import com.dungeonhero.framework.reward.GameplayReward;
import com.dungeonhero.framework.reward.RewardResult;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GameplayExecutionTest {

  @Test
  void objectiveAndRewardRegistriesExecuteExtensionTypes() {
    UUID playerId = UUID.randomUUID();
    GameplayContext context =
        GameplayContext.forPlayer(
            new PlayerContext(playerId, "Hero"),
            Map.of("event.mob", "ZOMBIE", "event.amount", 2, "objective.progress", 1));
    var definition =
        new GameplayDefinition("zombies", "defeat_mobs", Map.of("mob", "ZOMBIE", "amount", 3));

    var objectives = new com.dungeonhero.framework.objective.ObjectiveRegistry();
    objectives.register(new DefeatMobsObjective());
    ObjectiveResult result = objectives.evaluate(context, definition);
    assertTrue(result.complete());
    assertEquals(2, result.progressDelta());

    AtomicInteger grants = new AtomicInteger();
    var rewards = new com.dungeonhero.framework.reward.RewardRegistry();
    rewards.register(
        new GameplayReward() {
          @Override
          public String type() {
            return "test";
          }

          @Override
          public RewardResult grant(GameplayContext ignored, GameplayDefinition ignoredDefinition) {
            grants.incrementAndGet();
            return new RewardResult(true, "ok");
          }
        });
    assertTrue(
        rewards.grant(context, new GameplayDefinition("reward", "test", Map.of())).success());
    assertEquals(1, grants.get());
  }
}
