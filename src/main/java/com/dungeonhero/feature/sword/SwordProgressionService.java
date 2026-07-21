package com.dungeonhero.feature.sword;

import com.dungeonhero.feature.rank.DungeonRankService;
import com.dungeonhero.integration.mythicmobs.MythicFragmentService;
import com.dungeonhero.messaging.DungeonHeroMessages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

public final class SwordProgressionService implements Listener {

    private final JavaPlugin plugin;
    private final HeroItemService heroItemService;
    private final MythicFragmentService mythicFragmentService;
    private final DungeonRankService dungeonRankService;
    private final HeroSwordStorage heroSwordStorage;

    private String swordXpItemId;
    private boolean autoMobKillXp;
    private int xpPerItem;
    private int xpPerMobKill;
    private int baseXpRequired;
    private double xpRequiredMultiplier;
    private int maxSwordLevel;

    public SwordProgressionService(JavaPlugin plugin, HeroItemService heroItemService,
                                   MythicFragmentService mythicFragmentService,
                                   DungeonRankService dungeonRankService,
                                   HeroSwordStorage heroSwordStorage) {
        this.plugin = plugin;
        this.heroItemService = heroItemService;
        this.mythicFragmentService = mythicFragmentService;
        this.dungeonRankService = dungeonRankService;
        this.heroSwordStorage = heroSwordStorage;
        reload();
    }

    public void reload() {
        swordXpItemId = plugin.getConfig().getString(
                "DungeonHero.Progression.SwordXPItem", "mm:HeroSwordXP");
        autoMobKillXp = plugin.getConfig().getBoolean(
                "DungeonHero.Progression.AutoMobKillXP", true);
        xpPerItem = Math.max(1, plugin.getConfig().getInt(
                "DungeonHero.Progression.XPPerItem", 25));
        xpPerMobKill = Math.max(1, plugin.getConfig().getInt(
                "DungeonHero.Progression.XPPerMobKill", xpPerItem));
        baseXpRequired = Math.max(1, plugin.getConfig().getInt(
                "DungeonHero.Progression.BaseXPRequired", 100));
        xpRequiredMultiplier = Math.max(1.0, plugin.getConfig().getDouble(
                "DungeonHero.Progression.XPRequiredMultiplier", 1.25));
        maxSwordLevel = Math.max(1, plugin.getConfig().getInt(
                "DungeonHero.Progression.MaxSwordLevel", 100));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        if (!autoMobKillXp || !(event.getEntity() instanceof Mob mob)) {
            return;
        }

        Player player = mob.getKiller();
        if (player == null) {
            return;
        }

        // Automatic mode replaces the physical HeroSwordXP drop.
        removeSwordXpDrops(event);

        PlayerInventory inventory = player.getInventory();
        int swordSlot = findStrongestSwordSlot(inventory);
        if (swordSlot < 0) {
            player.sendActionBar(Component.text("You need your Hero Sword to receive Sword XP.",
                    NamedTextColor.RED));
            return;
        }

        ItemStack sword = inventory.getItem(swordSlot);
        int playerLevelCap = getMaxSwordLevel(player);
        if (heroItemService.getSwordLevel(sword) >= playerLevelCap) {
            player.sendActionBar(Component.text("Your Hero Sword has reached the level cap.",
                    NamedTextColor.YELLOW));
            return;
        }

        ProgressionResult result = addExperience(sword, xpPerMobKill, playerLevelCap);
        inventory.setItem(swordSlot, result.sword());
        heroSwordStorage.save(player, result.sword());
        if (result.levelsGained() > 0) {
            player.sendMessage(Component.text("Your Hero Sword reached Level " + result.level() + "!",
                    NamedTextColor.GREEN));
        }
        player.sendActionBar(DungeonHeroMessages.compactSwordActionBar(result.sword(), heroItemService, this,
                playerLevelCap));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSwordXpPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !mythicFragmentService.isItemId(event.getItem().getItemStack(), swordXpItemId)) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        int swordSlot = findStrongestSwordSlot(inventory);
        if (swordSlot < 0) {
            player.sendActionBar(Component.text("You need your Hero Sword to collect Sword XP.", NamedTextColor.RED));
            return;
        }

        ItemStack sword = inventory.getItem(swordSlot);
        int currentLevel = heroItemService.getSwordLevel(sword);
        int playerLevelCap = getMaxSwordLevel(player);
        if (currentLevel >= playerLevelCap) {
            player.sendActionBar(Component.text("Your Hero Sword has reached the level cap.", NamedTextColor.YELLOW));
            return;
        }

        int xpAmount = event.getItem().getItemStack().getAmount() * xpPerItem;
        ProgressionResult result = addExperience(sword, xpAmount, playerLevelCap);
        inventory.setItem(swordSlot, result.sword());
        heroSwordStorage.save(player, result.sword());
        event.setCancelled(true);
        event.getItem().remove();

        if (result.levelsGained() > 0) {
            player.sendMessage(Component.text("Your Hero Sword reached Level " + result.level() + "!",
                    NamedTextColor.GREEN));
        }
        player.sendActionBar(DungeonHeroMessages.compactSwordActionBar(result.sword(), heroItemService, this,
                playerLevelCap));
    }

    public ProgressionResult addExperience(ItemStack sword, int xpAmount) {
        return addExperience(sword, xpAmount, maxSwordLevel);
    }

    public ProgressionResult addExperience(ItemStack sword, int xpAmount, int levelCap) {
        int level = heroItemService.getSwordLevel(sword);
        int xp = heroItemService.getSwordXp(sword);
        int levelsGained = 0;
        int remainingXp = Math.max(0, xpAmount);

        int effectiveCap = Math.max(1, Math.min(maxSwordLevel, levelCap));
        while (level < effectiveCap && remainingXp > 0) {
            int required = requiredXp(level);
            int needed = Math.max(0, required - xp);
            if (remainingXp < needed) {
                xp += remainingXp;
                remainingXp = 0;
                break;
            }

            remainingXp -= needed;
            level++;
            levelsGained++;
            xp = 0;
        }

        if (level >= effectiveCap) {
            xp = 0;
        }

        ItemStack updatedSword = heroItemService.withSwordProgression(sword, level, xp);
        return new ProgressionResult(updatedSword, level, xp, levelsGained);
    }

    public int requiredXp(int level) {
        return Math.max(1, (int) Math.round(baseXpRequired
                * Math.pow(xpRequiredMultiplier, Math.max(0, level - 1))));
    }

    public int getMaxSwordLevel() {
        return maxSwordLevel;
    }

    public int getMaxSwordLevel(Player player) {
        return Math.min(maxSwordLevel, dungeonRankService.getSwordLevelCap(player));
    }

    public float getHudProgress(ItemStack sword) {
        return getHudProgress(sword, maxSwordLevel);
    }

    public float getHudProgress(ItemStack sword, int levelCap) {
        if (!heroItemService.isHeroSword(sword)) {
            return 0.0f;
        }

        int level = heroItemService.getSwordLevel(sword);
        int effectiveCap = Math.max(1, Math.min(maxSwordLevel, levelCap));
        if (level >= effectiveCap) {
            return 0.999f;
        }

        int required = requiredXp(level);
        return Math.max(0.0f, Math.min(0.999f,
                heroItemService.getSwordXp(sword) / (float) required));
    }

    public String getSwordStatus(ItemStack sword) {
        return getSwordStatus(sword, maxSwordLevel);
    }

    public String getSwordStatus(ItemStack sword, int levelCap) {
        if (!heroItemService.isHeroSword(sword)) {
            return "You do not have a Hero Sword.";
        }
        int level = heroItemService.getSwordLevel(sword);
        int effectiveCap = Math.max(1, Math.min(maxSwordLevel, levelCap));
        String tier = heroItemService.getSwordTier(sword).displayName();
        int prestige = heroItemService.getSwordPrestige(sword);
        if (level >= effectiveCap) {
            return tier + " Hero Sword Level " + level + " (RANK CAP) - Prestige " + prestige;
        }
        return tier + " Hero Sword Level " + level + " - XP " + heroItemService.getSwordXp(sword)
                + "/" + requiredXp(level) + " - Cap " + effectiveCap + " - Prestige " + prestige;
    }

    public int findStrongestSwordSlot(PlayerInventory inventory) {
        int strongestSlot = -1;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!heroItemService.isHeroSword(item)) {
                continue;
            }
            if (strongestSlot < 0
                    || isStronger(item, inventory.getItem(strongestSlot))) {
                strongestSlot = slot;
            }
        }
        return strongestSlot;
    }

    private void removeSwordXpDrops(EntityDeathEvent event) {
        event.getDrops().removeIf(item -> mythicFragmentService.isItemId(item, swordXpItemId));
    }

    private boolean isStronger(ItemStack first, ItemStack second) {
        int firstLevel = heroItemService.getSwordLevel(first);
        int secondLevel = heroItemService.getSwordLevel(second);
        if (firstLevel != secondLevel) {
            return firstLevel > secondLevel;
        }
        int firstPrestige = heroItemService.getSwordPrestige(first);
        int secondPrestige = heroItemService.getSwordPrestige(second);
        if (firstPrestige != secondPrestige) {
            return firstPrestige > secondPrestige;
        }
        return heroItemService.getDamageBonus(first) > heroItemService.getDamageBonus(second);
    }

    public record ProgressionResult(ItemStack sword, int level, int xp, int levelsGained) {
    }
}
