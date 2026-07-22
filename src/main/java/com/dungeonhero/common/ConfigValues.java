package com.dungeonhero.common;

import java.util.Map;

/** Small shared normalization helpers for Bukkit configuration adapters. */
public final class ConfigValues {

  private ConfigValues() {}

  public static String mapString(Map<?, ?> values, String key, String fallback) {
    Object value = values == null ? null : values.get(key);
    return value == null ? fallback : String.valueOf(value).trim();
  }

  public static long mapLong(Map<?, ?> values, String key, long fallback) {
    Object value = values == null ? null : values.get(key);
    if (value instanceof Number number) {
      return number.longValue();
    }
    try {
      return value == null ? fallback : Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException exception) {
      return fallback;
    }
  }
}
