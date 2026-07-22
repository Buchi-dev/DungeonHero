package com.dungeonhero.framework.action;

import com.dungeonhero.framework.GameplayDefinition;
import com.dungeonhero.framework.context.GameplayContext;

public interface GameplayAction {
    String type();

    ActionResult execute(GameplayContext context, GameplayDefinition definition);
}
