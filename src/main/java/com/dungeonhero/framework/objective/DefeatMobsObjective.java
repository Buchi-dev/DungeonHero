package com.dungeonhero.framework.objective;

import com.dungeonhero.framework.GameplayDefinition;
import com.dungeonhero.framework.context.GameplayContext;
import java.util.Locale;

/** Built-in objective used by the Open-World Dungeon module and future quest modules. */
public final class DefeatMobsObjective implements GameplayObjective {
  @Override
  public String type() {
    return "defeat_mobs";
  }

  @Override
  public ObjectiveResult evaluate(GameplayContext context, GameplayDefinition definition) {
    String expectedMob = string(definition.parameters().get("mob"));
    String actualMob = string(context.attributes().get("event.mob"));
    int required = positiveInt(definition.parameters().get("amount"));
    int current = positiveInt(context.attributes().get("objective.progress"));
    int amount = Math.max(0, number(context.attributes().get("event.amount")));
    if (expectedMob.isBlank()
        || required < 1
        || !expectedMob.equalsIgnoreCase(actualMob)
        || amount < 1) {
      return ObjectiveResult.noProgress();
    }
    int updated = Math.min(required, current + amount);
    return new ObjectiveResult(
        updated >= required,
        updated - current,
        updated >= required ? "Objective complete" : updated + "/" + required);
  }

  private String string(Object value) {
    return value == null ? "" : value.toString().trim().toUpperCase(Locale.ROOT);
  }

  private int positiveInt(Object value) {
    return Math.max(0, number(value));
  }

  private int number(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    try {
      return value == null ? 0 : Integer.parseInt(value.toString());
    } catch (NumberFormatException ignored) {
      return 0;
    }
  }
}
