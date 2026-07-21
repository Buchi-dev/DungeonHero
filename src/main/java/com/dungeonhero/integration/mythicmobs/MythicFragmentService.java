package com.dungeonhero.integration.mythicmobs;

import io.lumine.mythic.api.items.ItemManager;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class MythicFragmentService {

    private static final String ID_PREFIX = "mm:";

    private final JavaPlugin plugin;
    private final Map<String, FragmentUpgrade> configuredFragments = new TreeMap<>();

    public MythicFragmentService(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        configuredFragments.clear();
        ConfigurationSection fragments = plugin.getConfig().getConfigurationSection("DungeonHero.Fragments");
        if (fragments == null) {
            return;
        }

        for (String configuredId : fragments.getKeys(false)) {
            ConfigurationSection fragment = fragments.getConfigurationSection(configuredId);
            if (fragment == null) {
                continue;
            }

            String id = normalizeId(configuredId);
            String type = fragment.getString("Type", "");
            String stat = fragment.getString("Stat", "").trim().toUpperCase(Locale.ROOT);
            double amount = fragment.getDouble("Amount", 0);
            FragmentUpgrade upgrade = new FragmentUpgrade(id, stat, amount);

            if (!"fragment".equalsIgnoreCase(type) || !upgrade.isSupported() || amount <= 0) {
                plugin.getLogger().warning("Invalid DungeonHero fragment configuration for " + configuredId + ".");
                continue;
            }
            configuredFragments.put(id.toLowerCase(Locale.ROOT), upgrade);
        }
    }

    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("MythicMobs");
    }

    public Inspection inspect(ItemStack item) {
        if (!isAvailable()) {
            return Inspection.invalid("MythicMobs is not installed or enabled.");
        }
        if (item == null || item.getType().isAir()) {
            return Inspection.invalid("Place a MythicMobs fragment in the Forge.");
        }

        ItemManager itemManager = MythicBukkit.inst().getItemManager();
        if (!itemManager.isMythicItem(item)) {
            return Inspection.invalid("This item is not a MythicMobs item.");
        }

        String internalName = itemManager.getMythicTypeFromItem(item);
        if (internalName == null || internalName.isBlank()) {
            return Inspection.invalid("This MythicMobs item has no internal item ID.");
        }

        String id = normalizeId(internalName);
        FragmentUpgrade upgrade = configuredFragments.get(id.toLowerCase(Locale.ROOT));
        if (upgrade == null) {
            return Inspection.invalid("DungeonHero has no fragment configuration for " + id + ".");
        }
        return Inspection.valid(upgrade);
    }

    public boolean isItemId(ItemStack item, String expectedId) {
        if (!isAvailable() || item == null || expectedId == null) {
            return false;
        }

        ItemManager itemManager = MythicBukkit.inst().getItemManager();
        if (!itemManager.isMythicItem(item)) {
            return false;
        }

        String internalName = itemManager.getMythicTypeFromItem(item);
        return internalName != null
                && normalizeId(internalName).equalsIgnoreCase(normalizeId(expectedId));
    }

    public Optional<ItemStack> createItem(String id) {
        if (!isAvailable() || id == null) {
            return Optional.empty();
        }

        String internalName = normalizeId(id).substring(ID_PREFIX.length());
        if (internalName.isBlank()) {
            return Optional.empty();
        }

        ItemStack item = MythicBukkit.inst().getItemManager().getItemStack(internalName);
        return item == null || item.getType().isAir() ? Optional.empty() : Optional.of(item);
    }

    public List<String> getFragmentIds() {
        return configuredFragments.values().stream()
                .map(FragmentUpgrade::id)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private String normalizeId(String id) {
        String trimmed = id.trim();
        return trimmed.regionMatches(true, 0, ID_PREFIX, 0, ID_PREFIX.length())
                ? ID_PREFIX + trimmed.substring(ID_PREFIX.length())
                : ID_PREFIX + trimmed;
    }

    public record FragmentUpgrade(String id, String stat, double amount) {
        public boolean isSupported() {
            return "DAMAGE".equals(stat);
        }
    }

    public record Inspection(FragmentUpgrade upgrade, String error) {
        public static Inspection valid(FragmentUpgrade upgrade) {
            return new Inspection(upgrade, null);
        }

        public static Inspection invalid(String error) {
            return new Inspection(null, error);
        }

        public boolean isValid() {
            return upgrade != null;
        }
    }
}
