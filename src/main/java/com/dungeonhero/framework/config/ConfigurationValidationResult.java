package com.dungeonhero.framework.config;

import java.util.List;

public record ConfigurationValidationResult(List<ConfigurationIssue> issues) {

  public ConfigurationValidationResult {
    issues = issues == null ? List.of() : List.copyOf(issues);
  }

  public boolean valid() {
    return issues.isEmpty();
  }

  public List<String> messages() {
    return issues.stream().map(ConfigurationIssue::toString).toList();
  }
}
