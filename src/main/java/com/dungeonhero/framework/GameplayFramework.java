package com.dungeonhero.framework;

import com.dungeonhero.framework.action.ActionRegistry;
import com.dungeonhero.framework.condition.ConditionRegistry;
import com.dungeonhero.framework.event.FeatureEventBus;
import com.dungeonhero.framework.objective.ObjectiveRegistry;
import com.dungeonhero.framework.reward.RewardRegistry;
import com.dungeonhero.framework.state.GameplayStateStore;
import com.dungeonhero.framework.time.TimerService;
import com.dungeonhero.framework.trigger.TriggerRegistry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

/** Composition root for reusable gameplay APIs exposed by DungeonHero. */
public final class GameplayFramework {

  private final FeatureEventBus events = new FeatureEventBus();
  private final GameplayStateStore states = new GameplayStateStore();
  private final ObjectiveRegistry objectives = new ObjectiveRegistry();
  private final ConditionRegistry conditions = new ConditionRegistry();
  private final ActionRegistry actions = new ActionRegistry();
  private final RewardRegistry rewards = new RewardRegistry();
  private final TriggerRegistry triggers = new TriggerRegistry();
  private final TimerService timers = new TimerService();
  private final FeatureContext context;
  private final FeatureRegistry features;

  public GameplayFramework(JavaPlugin plugin) {
    context =
        new FeatureContext(
            plugin,
            events,
            states,
            objectives,
            conditions,
            actions,
            rewards,
            triggers,
            timers,
            plugin == null ? ignored -> {} : plugin.getLogger()::info,
            plugin == null ? ignored -> {} : plugin.getLogger()::warning);
    features = new FeatureRegistry(context);
  }

  public FeatureContext context() {
    return context;
  }

  public FeatureEventBus events() {
    return events;
  }

  public GameplayStateStore states() {
    return states;
  }

  public ObjectiveRegistry objectives() {
    return objectives;
  }

  public ConditionRegistry conditions() {
    return conditions;
  }

  public ActionRegistry actions() {
    return actions;
  }

  public RewardRegistry rewards() {
    return rewards;
  }

  public TriggerRegistry triggers() {
    return triggers;
  }

  public TimerService timers() {
    return timers;
  }

  public FeatureRegistry features() {
    return features;
  }

  public void reload(ConfigurationSection featureRoot) {
    features.reload(featureRoot);
  }

  public void close() {
    features.stopAll();
    events.clear();
    states.clear();
    timers.clear();
  }
}
