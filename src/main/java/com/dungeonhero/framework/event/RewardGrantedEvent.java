package com.dungeonhero.framework.event;

import com.dungeonhero.framework.reward.RewardResult;

import java.util.UUID;

public record RewardGrantedEvent(String featureId, String objectiveId, UUID playerId,
                                 String rewardType, RewardResult result) implements GameplayEvent {
}
