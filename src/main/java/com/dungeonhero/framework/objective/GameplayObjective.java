package com.dungeonhero.framework.objective;

import com.dungeonhero.framework.GameplayDefinition;
import com.dungeonhero.framework.context.GameplayContext;

public interface GameplayObjective {
  String type();

  ObjectiveResult evaluate(GameplayContext context, GameplayDefinition definition);
}
