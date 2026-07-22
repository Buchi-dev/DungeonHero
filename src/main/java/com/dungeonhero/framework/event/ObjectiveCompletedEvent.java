package com.dungeonhero.framework.event;

import java.util.UUID;

public record ObjectiveCompletedEvent(String featureId, String objectiveId, UUID playerId) implements GameplayEvent {
}
