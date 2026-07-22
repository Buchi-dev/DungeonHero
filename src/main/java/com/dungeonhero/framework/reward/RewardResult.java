package com.dungeonhero.framework.reward;

public record RewardResult(boolean success, String message) {
    public RewardResult {
        message = message == null ? "" : message;
    }
}
