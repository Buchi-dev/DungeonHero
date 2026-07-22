package com.dungeonhero.framework.objective;

public record ObjectiveResult(boolean complete, int progressDelta, String message) {
  public ObjectiveResult {
    progressDelta = Math.max(0, progressDelta);
    message = message == null ? "" : message;
  }

  public static ObjectiveResult noProgress() {
    return new ObjectiveResult(false, 0, "");
  }
}
