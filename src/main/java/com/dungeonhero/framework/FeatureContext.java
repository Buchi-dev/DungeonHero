package com.dungeonhero.framework;

import com.dungeonhero.framework.action.ActionRegistry;
import com.dungeonhero.framework.condition.ConditionRegistry;
import com.dungeonhero.framework.event.FeatureEventBus;
import com.dungeonhero.framework.objective.ObjectiveRegistry;
import com.dungeonhero.framework.reward.RewardRegistry;
import com.dungeonhero.framework.state.GameplayStateStore;
import com.dungeonhero.framework.time.TimerService;
import com.dungeonhero.framework.trigger.TriggerRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.plugin.java.JavaPlugin;

/** Dependency container shared by features without making those features global singletons. */
public final class FeatureContext {

  private final JavaPlugin plugin;
  private final FeatureEventBus events;
  private final GameplayStateStore states;
  private final ObjectiveRegistry objectives;
  private final ConditionRegistry conditions;
  private final ActionRegistry actions;
  private final RewardRegistry rewards;
  private final TriggerRegistry triggers;
  private final TimerService timers;
  private final Consumer<String> infoLogger;
  private final Consumer<String> warningLogger;
  private final Map<String, List<FeatureEventBus.Subscription>> subscriptions = new HashMap<>();

  public FeatureContext(
      JavaPlugin plugin,
      FeatureEventBus events,
      GameplayStateStore states,
      ObjectiveRegistry objectives,
      ConditionRegistry conditions,
      ActionRegistry actions,
      RewardRegistry rewards,
      TriggerRegistry triggers,
      TimerService timers,
      Consumer<String> infoLogger,
      Consumer<String> warningLogger) {
    this.plugin = plugin;
    this.events = events;
    this.states = states;
    this.objectives = objectives;
    this.conditions = conditions;
    this.actions = actions;
    this.rewards = rewards;
    this.triggers = triggers;
    this.timers = timers;
    this.infoLogger = infoLogger == null ? ignored -> {} : infoLogger;
    this.warningLogger = warningLogger == null ? ignored -> {} : warningLogger;
  }

  public JavaPlugin plugin() {
    return plugin;
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

  public void info(String message) {
    infoLogger.accept(message);
  }

  public void warn(String message) {
    warningLogger.accept(message);
  }

  public synchronized <T> FeatureEventBus.Subscription listen(
      String featureId, Class<T> eventType, Consumer<T> handler) {
    FeatureEventBus.Subscription subscription = events.subscribe(eventType, handler);
    subscriptions.computeIfAbsent(featureId, ignored -> new ArrayList<>()).add(subscription);
    return subscription;
  }

  public synchronized void clearFeature(String featureId) {
    for (FeatureEventBus.Subscription subscription :
        subscriptions.getOrDefault(featureId, List.of())) {
      subscription.close();
    }
    subscriptions.remove(featureId);
  }
}
