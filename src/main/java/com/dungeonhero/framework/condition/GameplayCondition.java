package com.dungeonhero.framework.condition;

import com.dungeonhero.framework.GameplayDefinition;
import com.dungeonhero.framework.context.GameplayContext;

public interface GameplayCondition {
    String type();

    boolean matches(GameplayContext context, GameplayDefinition definition);
}
