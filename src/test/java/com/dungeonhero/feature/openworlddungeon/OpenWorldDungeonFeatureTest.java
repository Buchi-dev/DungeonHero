package com.dungeonhero.feature.openworlddungeon;

import com.dungeonhero.framework.GameplayFramework;
import com.dungeonhero.framework.GameplayFeature;
import com.dungeonhero.framework.event.MobDefeatedEvent;
import com.dungeonhero.framework.reward.GameplayReward;
import com.dungeonhero.framework.reward.RewardResult;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenWorldDungeonFeatureTest {

    @Test
    void completesConfiguredObjectiveAndRunsRewardOnce() {
        GameplayFramework framework = new GameplayFramework(null);
        AtomicInteger grants = new AtomicInteger();
        framework.objectives().register(new com.dungeonhero.framework.objective.DefeatMobsObjective());
        framework.rewards().register(new GameplayReward() {
            @Override public String type() { return "test-reward"; }
            @Override public RewardResult grant(com.dungeonhero.framework.context.GameplayContext context,
                                                com.dungeonhero.framework.GameplayDefinition definition) {
                grants.incrementAndGet();
                return new RewardResult(true, "granted");
            }
        });
        OpenWorldDungeonFeature feature = new OpenWorldDungeonFeature(null);
        framework.features().register(feature);

        YamlConfiguration config = new YamlConfiguration();
        config.set("open-world-dungeon.Enabled", true);
        config.set("open-world-dungeon.Objectives", java.util.List.of(
                Map.of("Id", "zombies", "Type", "defeat_mobs", "Mob", "ZOMBIE", "Amount", 2)));
        config.set("open-world-dungeon.Rewards", java.util.List.of(
                Map.of("Id", "reward", "Type", "test-reward")));
        framework.reload(config);

        UUID player = UUID.randomUUID();
        framework.events().publish(new MobDefeatedEvent(player, "ZOMBIE", 1));
        framework.events().publish(new MobDefeatedEvent(player, "ZOMBIE", 1));
        framework.events().publish(new MobDefeatedEvent(player, "ZOMBIE", 1));

        assertTrue(feature.isStarted());
        assertEquals(2, framework.states().getInt(OpenWorldDungeonFeature.ID + ":" + player,
                "objective.zombies.progress", 0));
        assertEquals(1, grants.get());
    }
}
