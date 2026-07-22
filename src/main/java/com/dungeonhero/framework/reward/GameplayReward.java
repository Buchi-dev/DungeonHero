package com.dungeonhero.framework.reward;

import com.dungeonhero.framework.GameplayDefinition;
import com.dungeonhero.framework.context.GameplayContext;

public interface GameplayReward {
  String type();

  RewardResult grant(GameplayContext context, GameplayDefinition definition);
}
