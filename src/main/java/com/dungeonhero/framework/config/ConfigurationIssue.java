package com.dungeonhero.framework.config;

public record ConfigurationIssue(String path, String message) {
    @Override
    public String toString() {
        return path + ": " + message;
    }
}
