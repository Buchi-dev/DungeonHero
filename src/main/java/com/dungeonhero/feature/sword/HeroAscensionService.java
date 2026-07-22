package com.dungeonhero.feature.sword;

import com.dungeonhero.feature.rank.DungeonRankService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Owns the confirmed Hero Ascension transaction and the administrator reset transaction. */
public final class HeroAscensionService {

    private final JavaPlugin plugin;
    private final HeroItemService heroItemService;
    private final HeroSwordStorage heroSwordStorage;
    private final DungeonRankService dungeonRankService;
    private final Map<UUID, Long> pendingConfirmations = new HashMap<>();
    private int requiredLevel;
    private int maxPrestige;
    private long confirmationMillis;
    private double prestigeXpMultiplier;
    private double[] rareDropBonuses;

    public HeroAscensionService(JavaPlugin plugin, HeroItemService heroItemService,
                                HeroSwordStorage heroSwordStorage, DungeonRankService dungeonRankService) {
        this.plugin = plugin;
        this.heroItemService = heroItemService;
        this.heroSwordStorage = heroSwordStorage;
        this.dungeonRankService = dungeonRankService;
        reload();
    }

    public void reload() {
        requiredLevel = Math.max(1, plugin.getConfig().getInt(
                "DungeonHero.HeroAscension.RequiredSwordLevel",
                plugin.getConfig().getInt("DungeonHero.Progression.Prestige.RequiredSwordLevel", 100)));
        maxPrestige = Math.max(0, plugin.getConfig().getInt(
                "DungeonHero.HeroAscension.MaxPrestige",
                plugin.getConfig().getInt("DungeonHero.Progression.Prestige.MaxPrestige", 5)));
        long seconds = Math.max(10, plugin.getConfig().getLong(
                "DungeonHero.HeroAscension.ConfirmationSeconds", 30));
        confirmationMillis = seconds * 1000L;
        prestigeXpMultiplier = Math.max(1, plugin.getConfig().getDouble(
                "DungeonHero.HeroAscension.XpMultiplier", 2.0));
        rareDropBonuses = new double[] {0, 0.10, 0.15, 0.20, 0.25, 0.30};
        for (int prestige = 1; prestige < rareDropBonuses.length; prestige++) {
            rareDropBonuses[prestige] = Math.max(0, plugin.getConfig().getDouble(
                    "DungeonHero.HeroAscension.RareDropBonuses." + prestige, rareDropBonuses[prestige]));
        }
    }

    public boolean isEligible(Player player) {
        ItemStack sword = strongestSword(player);
        return sword != null && heroItemService.getSwordLevel(sword) >= requiredLevel
                && heroItemService.getSwordPrestige(sword) < maxPrestige;
    }

    public AscensionResult request(Player player) {
        if (!plugin.getConfig().getBoolean("DungeonHero.HeroAscension.Enabled",
                plugin.getConfig().getBoolean("DungeonHero.Progression.Prestige.Enabled", true))) {
            return AscensionResult.DISABLED;
        }
        ItemStack sword = strongestSword(player);
        if (sword == null) {
            return AscensionResult.NO_SWORD;
        }
        if (heroItemService.getSwordLevel(sword) < requiredLevel) {
            return AscensionResult.LEVEL_REQUIRED;
        }
        if (heroItemService.getSwordPrestige(sword) >= maxPrestige) {
            return AscensionResult.MAX_PRESTIGE;
        }
        pendingConfirmations.put(player.getUniqueId(), System.currentTimeMillis() + confirmationMillis);
        return AscensionResult.CONFIRMATION_REQUIRED;
    }

    public AscensionResult confirm(Player player) {
        Long expiresAt = pendingConfirmations.remove(player.getUniqueId());
        if (expiresAt == null || expiresAt < System.currentTimeMillis()) {
            return AscensionResult.CONFIRMATION_REQUIRED;
        }
        ItemStack sword = strongestSword(player);
        if (sword == null || heroItemService.getSwordLevel(sword) < requiredLevel) {
            return AscensionResult.LEVEL_REQUIRED;
        }
        if (heroItemService.getSwordPrestige(sword) >= maxPrestige) {
            return AscensionResult.MAX_PRESTIGE;
        }

        int slot = strongestSwordSlot(player.getInventory());
        ItemStack previous = sword.clone();
        ItemStack updated = heroItemService.withPrestige(sword);
        try {
            player.getInventory().setItem(slot, updated);
            heroSwordStorage.save(player, updated);
            dungeonRankService.recalculateRank(player);
            return AscensionResult.ASCENDED;
        } catch (RuntimeException exception) {
            player.getInventory().setItem(slot, previous);
            heroSwordStorage.save(player, previous);
            plugin.getLogger().severe("Hero Ascension rolled back for " + player.getUniqueId()
                    + ": " + exception.getMessage());
            return AscensionResult.STORAGE_FAILURE;
        }
    }

    public ResetSnapshot previewReset(Player player) {
        ItemStack sword = strongestSword(player);
        int level = sword == null ? 1 : heroItemService.getSwordLevel(sword);
        int xp = sword == null ? 0 : heroItemService.getSwordXp(sword);
        int prestige = sword == null ? 0 : heroItemService.getSwordPrestige(sword);
        double damage = sword == null ? 0 : heroItemService.getDamageBonus(sword);
        int rank = dungeonRankService.getRank(player);
        return new ResetSnapshot(level, xp, prestige, damage, rank, 1, 0, 0, rank,
                dungeonRankService.getBalance(player));
    }

    public ResetResult resetSword(Player player, String administrator) {
        int slot = strongestSwordSlot(player.getInventory());
        if (slot < 0) {
            return new ResetResult(ResetStatus.NO_SWORD, null);
        }
        ItemStack previous = player.getInventory().getItem(slot).clone();
        ResetSnapshot before = previewReset(player);
        ItemStack reset = heroItemService.resetSword(previous);
        try {
            player.getInventory().setItem(slot, reset);
            heroSwordStorage.save(player, reset);
            int rank = dungeonRankService.recalculateRank(player);
            ResetSnapshot after = new ResetSnapshot(1, 0, 0, 0, before.currentRank(), 1, 0, 0, rank,
                    dungeonRankService.getBalance(player));
            plugin.getLogger().warning("ADMIN_AUDIT resetsword administrator=" + administrator
                    + " target=" + player.getUniqueId() + " before=" + before + " after=" + after);
            return new ResetResult(ResetStatus.RESET, after);
        } catch (RuntimeException exception) {
            player.getInventory().setItem(slot, previous);
            heroSwordStorage.save(player, previous);
            plugin.getLogger().severe("Admin sword reset rolled back for " + player.getUniqueId()
                    + ": " + exception.getMessage());
            return new ResetResult(ResetStatus.STORAGE_FAILURE, before);
        }
    }

    public double xpMultiplier(int prestige) {
        return prestige > 0 ? prestigeXpMultiplier : 1.0;
    }

    public double rareDropBonus(int prestige) {
        return rareDropBonuses[Math.max(0, Math.min(rareDropBonuses.length - 1, prestige))];
    }

    public int requiredLevel() {
        return requiredLevel;
    }

    public int maxPrestige() {
        return maxPrestige;
    }

    private ItemStack strongestSword(Player player) {
        return heroItemService.findStrongestHeroSword(player);
    }

    private int strongestSwordSlot(PlayerInventory inventory) {
        int slot = -1;
        for (int index = 0; index < inventory.getSize(); index++) {
            ItemStack item = inventory.getItem(index);
            if (!heroItemService.isHeroSword(item)) {
                continue;
            }
            if (slot < 0 || stronger(item, inventory.getItem(slot))) {
                slot = index;
            }
        }
        return slot;
    }

    private boolean stronger(ItemStack first, ItemStack second) {
        return heroItemService.getSwordLevel(first) > heroItemService.getSwordLevel(second)
                || (heroItemService.getSwordLevel(first) == heroItemService.getSwordLevel(second)
                && heroItemService.getSwordPrestige(first) > heroItemService.getSwordPrestige(second));
    }

    public enum AscensionResult {
        CONFIRMATION_REQUIRED, ASCENDED, NO_SWORD, LEVEL_REQUIRED, MAX_PRESTIGE, DISABLED, STORAGE_FAILURE
    }

    public enum ResetStatus { RESET, NO_SWORD, STORAGE_FAILURE }

    public record ResetSnapshot(int currentLevel, int currentXp, int currentPrestige, double currentDamage,
                                int currentRank, int resultingLevel, int resultingXp, int resultingPrestige,
                                int resultingRank, long coins) {
    }

    public record ResetResult(ResetStatus status, ResetSnapshot snapshot) {
    }
}
