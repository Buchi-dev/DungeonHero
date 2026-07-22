package com.dungeonhero.feature.openworlddungeon;

import com.dungeonhero.framework.FeatureConfig;
import com.dungeonhero.framework.FeatureContext;
import com.dungeonhero.framework.GameplayDefinition;
import com.dungeonhero.framework.GameplayFeature;
import com.dungeonhero.framework.action.ActionResult;
import com.dungeonhero.framework.context.GameplayContext;
import com.dungeonhero.framework.context.PlayerContext;
import com.dungeonhero.framework.event.MobDefeatedEvent;
import com.dungeonhero.framework.event.ObjectiveCompletedEvent;
import com.dungeonhero.framework.event.RewardGrantedEvent;
import com.dungeonhero.framework.objective.ObjectiveResult;
import com.dungeonhero.framework.reward.RewardResult;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Framework-backed composition module for the existing open-world dungeon loop.
 * DungeonCore remains the authoritative spawner; this module consumes gameplay events
 * and composes configured objectives, conditions, actions, and rewards.
 */
public final class OpenWorldDungeonFeature implements GameplayFeature, Listener {

    public static final String ID = "open-world-dungeon";

    private final JavaPlugin plugin;
    private FeatureContext context;
    private List<GameplayDefinition> objectives = List.of();
    private List<GameplayDefinition> conditions = List.of();
    private List<GameplayDefinition> actions = List.of();
    private List<GameplayDefinition> rewards = List.of();
    private List<String> worlds = List.of();
    private boolean started;

    public OpenWorldDungeonFeature(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void load(FeatureContext context, FeatureConfig config) {
        this.context = context;
        objectives = config.definitions("Objectives");
        conditions = config.definitions("Conditions");
        actions = config.definitions("Actions");
        rewards = config.definitions("Rewards");
        worlds = config.getStringList("Worlds").stream()
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .toList();
    }

    @Override
    public void start() {
        if (started) {
            return;
        }
        started = true;
        context.listen(ID, MobDefeatedEvent.class, this::onMobDefeated);
        if (plugin != null) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
        }
        context.info("Open-World Dungeon feature started with " + objectives.size() + " objectives.");
    }

    @Override
    public void stop() {
        if (!started) {
            return;
        }
        started = false;
        HandlerList.unregisterAll(this);
    }

    /** Public extension API for DungeonCore and other spawners to publish custom mob IDs. */
    public void recordMobDefeat(Player player, String mobType, int amount) {
        if (player == null || !started || !isTrackedWorld(player.getWorld().getName())) {
            return;
        }
        context.events().publish(new MobDefeatedEvent(player.getUniqueId(), mobType, amount));
    }

    public boolean isStarted() {
        return started;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            recordMobDefeat(killer, event.getEntity().getType().name(), 1);
        }
    }

    private void onMobDefeated(MobDefeatedEvent event) {
        for (GameplayDefinition objective : objectives) {
            processObjective(event, objective);
        }
    }

    private void processObjective(MobDefeatedEvent event, GameplayDefinition objective) {
        String scope = ID + ":" + event.playerId();
        int current = context.states().getInt(scope, progressKey(objective), 0);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("event.mob", event.mobType());
        attributes.put("event.amount", event.amount());
        attributes.put("objective.progress", current);
        GameplayContext gameplayContext = GameplayContext.forPlayer(
                new PlayerContext(event.playerId(), event.playerId().toString()), attributes);
        if (!context.conditions().matchesAll(gameplayContext, conditions)) {
            return;
        }

        ObjectiveResult result = context.objectives().evaluate(gameplayContext, objective);
        if (result.progressDelta() <= 0) {
            return;
        }
        int updated = current + result.progressDelta();
        context.states().put(scope, progressKey(objective), updated);
        if (!result.complete() || context.states().getBoolean(scope, completedKey(objective), false)) {
            return;
        }

        context.states().put(scope, completedKey(objective), true);
        context.events().publish(new ObjectiveCompletedEvent(ID, objective.id(), event.playerId()));
        for (GameplayDefinition reward : rewards) {
            RewardResult rewardResult = context.rewards().grant(gameplayContext, reward);
            context.events().publish(new RewardGrantedEvent(ID, objective.id(), event.playerId(),
                    reward.type(), rewardResult));
        }
        for (GameplayDefinition action : actions) {
            ActionResult actionResult = context.actions().execute(gameplayContext, action);
            if (!actionResult.success()) {
                context.warn("Open-World Dungeon action '" + action.type() + "' failed: " + actionResult.message());
            }
        }
    }

    private String progressKey(GameplayDefinition objective) {
        return "objective." + objective.id() + ".progress";
    }

    private String completedKey(GameplayDefinition objective) {
        return "objective." + objective.id() + ".completed";
    }

    private boolean isTrackedWorld(String worldName) {
        return worlds.isEmpty() || worlds.contains(worldName.toLowerCase(Locale.ROOT));
    }
}
