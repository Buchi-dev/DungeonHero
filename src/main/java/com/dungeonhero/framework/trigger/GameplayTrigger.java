package com.dungeonhero.framework.trigger;

import com.dungeonhero.framework.GameplayDefinition;
import com.dungeonhero.framework.context.GameplayContext;

public interface GameplayTrigger {
  String type();

  boolean matches(GameplayContext context, GameplayDefinition definition);
}
