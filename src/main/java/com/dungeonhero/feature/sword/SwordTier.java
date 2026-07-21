package com.dungeonhero.feature.sword;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

public enum SwordTier {
    WOOD("Wood", 1, 10, Material.WOODEN_SWORD, NamedTextColor.GREEN),
    STONE("Stone", 11, 20, Material.STONE_SWORD, NamedTextColor.GRAY),
    IRON("Iron", 21, 30, Material.IRON_SWORD, NamedTextColor.WHITE),
    GOLD("Gold", 31, 40, Material.GOLDEN_SWORD, NamedTextColor.YELLOW),
    DIAMOND("Diamond", 41, 50, Material.DIAMOND_SWORD, NamedTextColor.AQUA),
    NETHERITE("Netherite", 51, 60, Material.NETHERITE_SWORD, NamedTextColor.DARK_GRAY),
    ANCIENT("Ancient", 61, 70, Material.NETHERITE_SWORD, NamedTextColor.DARK_PURPLE),
    MYTHIC("Mythic", 71, 80, Material.NETHERITE_SWORD, NamedTextColor.LIGHT_PURPLE),
    LEGENDARY("Legendary", 81, 90, Material.NETHERITE_SWORD, NamedTextColor.GOLD),
    HERO("Hero", 91, Integer.MAX_VALUE, Material.NETHERITE_SWORD, NamedTextColor.RED);

    private final String displayName;
    private final int minimumLevel;
    private final int maximumLevel;
    private final Material material;
    private final NamedTextColor color;

    SwordTier(String displayName, int minimumLevel, int maximumLevel, Material material, NamedTextColor color) {
        this.displayName = displayName;
        this.minimumLevel = minimumLevel;
        this.maximumLevel = maximumLevel;
        this.material = material;
        this.color = color;
    }

    public String displayName() {
        return displayName;
    }

    public Material material() {
        return material;
    }

    public NamedTextColor color() {
        return color;
    }

    public static SwordTier fromLevel(int level) {
        int safeLevel = Math.max(1, level);
        for (SwordTier tier : values()) {
            if (safeLevel >= tier.minimumLevel && safeLevel <= tier.maximumLevel) {
                return tier;
            }
        }
        return HERO;
    }
}
