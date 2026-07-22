package com.dungeonhero.framework.action;

public record ActionResult(boolean success, String message) {
  public ActionResult {
    message = message == null ? "" : message;
  }
}
