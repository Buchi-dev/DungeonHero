package com.dungeonhero.feature.armor;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

/** Shared Hero Armor tier bands. Higher tiers intentionally reuse Netherite materials. */
public enum ArmorTier {
  WOOD("Wood", 1, 10, Material.LEATHER_HELMET, NamedTextColor.GREEN),
  STONE("Stone", 11, 20, Material.CHAINMAIL_HELMET, NamedTextColor.GRAY),
  IRON("Iron", 21, 30, Material.IRON_HELMET, NamedTextColor.WHITE),
  GOLD("Gold", 31, 40, Material.GOLDEN_HELMET, NamedTextColor.YELLOW),
  DIAMOND("Diamond", 41, 50, Material.DIAMOND_HELMET, NamedTextColor.AQUA),
  NETHERITE("Netherite", 51, 60, Material.NETHERITE_HELMET, NamedTextColor.DARK_GRAY),
  ANCIENT("Ancient", 61, 70, Material.NETHERITE_HELMET, NamedTextColor.DARK_PURPLE),
  MYTHIC("Mythic", 71, 80, Material.NETHERITE_HELMET, NamedTextColor.LIGHT_PURPLE),
  LEGENDARY("Legendary", 81, 90, Material.NETHERITE_HELMET, NamedTextColor.GOLD),
  HERO("Hero", 91, Integer.MAX_VALUE, Material.NETHERITE_HELMET, NamedTextColor.RED);

  private final String displayName;
  private final int minimumLevel;
  private final int maximumLevel;
  private final Material helmetMaterial;
  private final NamedTextColor color;

  ArmorTier(
      String displayName,
      int minimumLevel,
      int maximumLevel,
      Material helmetMaterial,
      NamedTextColor color) {
    this.displayName = displayName;
    this.minimumLevel = minimumLevel;
    this.maximumLevel = maximumLevel;
    this.helmetMaterial = helmetMaterial;
    this.color = color;
  }

  public String displayName() {
    return displayName;
  }

  public NamedTextColor color() {
    return color;
  }

  public Material material(ArmorSlot slot) {
    String suffix = slot.materialSuffix();
    return Material.valueOf(helmetMaterial.name().replace("HELMET", suffix));
  }

  public static ArmorTier fromLevel(int level) {
    int safeLevel = Math.max(1, level);
    for (ArmorTier tier : values()) {
      if (safeLevel >= tier.minimumLevel && safeLevel <= tier.maximumLevel) return tier;
    }
    return HERO;
  }

  public enum ArmorSlot {
    HELMET("HELMET"),
    CHESTPLATE("CHESTPLATE"),
    LEGGINGS("LEGGINGS"),
    BOOTS("BOOTS");

    private final String materialSuffix;

    ArmorSlot(String materialSuffix) {
      this.materialSuffix = materialSuffix;
    }

    public String materialSuffix() {
      return materialSuffix;
    }
  }
}
