package com.dungeonhero.feature.sword;

import com.dungeonhero.feature.rank.DungeonRankService;
import com.dungeonhero.feature.mobregistry.MobRegistryService;
import com.dungeonhero.messaging.DungeonHeroMessages;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class SwordProgressionService implements Listener {

    private final JavaPlugin plugin;
    private final HeroItemService heroItemService;
    private final SwordXpItemService swordXpItemService;
    private final DungeonRankService dungeonRankService;
    private final HeroSwordStorage heroSwordStorage;
    private final MobRegistryService mobRegistryService;

    private boolean autoMobKillXp;
    private boolean hostileMobKillXpOnly;
    private int xpPerItem;
    private int xpPerMobKill;
    private int mythicMobXp;
    private int baseXpRequired;
    private double xpRequiredMultiplier;
    private int maxSwordLevel;
    private final Set<UUID> mythicDeathsAwaitingVanillaCheck = new HashSet<>();

    public SwordProgressionService(JavaPlugin plugin, HeroItemService heroItemService,
                                   SwordXpItemService swordXpItemService,
                                   DungeonRankService dungeonRankService,
                                   HeroSwordStorage heroSwordStorage,
                                   MobRegistryService mobRegistryService) {
        this.plugin = plugin;
        this.heroItemService = heroItemService;
        this.swordXpItemService = swordXpItemService;
        this.dungeonRankService = dungeonRankService;
        this.heroSwordStorage = heroSwordStorage;
        this.mobRegistryService = mobRegistryService;
        reload();
    }

    public void reload() {
        autoMobKillXp = plugin.getConfig().getBoolean(
                "DungeonHero.Progression.AutoMobKillXP", true);
        hostileMobKillXpOnly = plugin.getConfig().getBoolean(
                "DungeonHero.Progression.HostileMobKillXPOnly", true);
        swordXpItemService.reload();
        xpPerItem = swordXpItemService.getConfiguredXp();
        xpPerMobKill = Math.max(1, plugin.getConfig().getInt(
                "DungeonHero.Progression.XPPerMobKill", xpPerItem));
        mythicMobXp = Math.max(1, plugin.getConfig().getInt(
                "DungeonHero.Progression.MythicMobXP", 25));
        baseXpRequired = Math.max(1, plugin.getConfig().getInt(
                "DungeonHero.Progression.BaseXPRequired", 100));
        xpRequiredMultiplier = Math.max(1.0, plugin.getConfig().getDouble(
                "DungeonHero.Progression.XPRequiredMultiplier", 1.25));
        maxSwordLevel = Math.max(1, plugin.getConfig().getInt(
                "DungeonHero.Progression.MaxSwordLevel", 100));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        if (!autoMobKillXp || !(event.getEntity() instanceof Mob mob)
                || (hostileMobKillXpOnly && !(mob instanceof Monster))) {
            return;
        }

        UUID entityId = mob.getUniqueId();
        if (mythicDeathsAwaitingVanillaCheck.contains(entityId) || isActiveMythicMob(entityId)) {
            return;
        }

        Player player = mob.getKiller();
        if (player == null) {
            return;
        }

        awardExperience(player, xpPerMobKill);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        if (!autoMobKillXp || !(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }

        UUID entityId = entity.getUniqueId();
        mythicDeathsAwaitingVanillaCheck.add(entityId);
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> mythicDeathsAwaitingVanillaCheck.remove(entityId), 2L);

        if (!(event.getKiller() instanceof Player player)) {
            return;
        }
        String internalName = event.getMobType() == null ? "" : event.getMobType().getInternalName();
        int reward = mythicXpFor(internalName);
        if (reward > 0) {
            awardExperience(player, reward);
        }
    }

    private void awardExperience(Player player, int amount) {
        if (amount <= 0) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        int swordSlot = findStrongestSwordSlot(inventory);
        if (swordSlot < 0) {
            player.sendActionBar(Component.text("You need your Hero Sword to receive Sword XP.",
                    NamedTextColor.RED));
            return;
        }

        ItemStack sword = inventory.getItem(swordSlot);
        amount = scaledRewardExperience(sword, amount);
        int playerLevelCap = getMaxSwordLevel(player);
        if (heroItemService.getSwordLevel(sword) >= playerLevelCap) {
            player.sendActionBar(Component.text("Your Hero Sword has reached the level cap.",
                    NamedTextColor.YELLOW));
            return;
        }

        ProgressionResult result = addExperience(sword, amount, playerLevelCap);
        inventory.setItem(swordSlot, result.sword());
        heroSwordStorage.save(player, result.sword());
        if (result.levelsGained() > 0) {
            player.sendMessage(Component.text("Your Hero Sword reached Level " + result.level() + "!",
                    NamedTextColor.GREEN));
        }
        player.sendActionBar(DungeonHeroMessages.compactSwordActionBar(result.sword(), heroItemService, this,
                playerLevelCap));
    }

    /** Prestige grants one configured 2x reward multiplier, never 2^prestige. */
    public int scaledRewardExperience(ItemStack sword, int amount) {
        if (!heroItemService.isHeroSword(sword)) {
            return Math.max(0, amount);
        }
        int prestige = heroItemService.getSwordPrestige(sword);
        double multiplier = prestige > 0 ? Math.max(1, Math.min(2,
                plugin.getConfig().getDouble("DungeonHero.HeroAscension.XpMultiplier", 2.0))) : 1.0;
        return (int) Math.max(0, Math.round(Math.max(0, amount) * multiplier));
    }

    /** Awards progression XP from a completed DungeonHero quest. */
    public void awardQuestExperience(Player player, int amount) {
        awardExperience(player, amount);
    }

    private int mythicXpFor(String internalName) {
        if (mobRegistryService.find(internalName).isPresent()) {
            return mobRegistryService.profileOrDefault(internalName).swordXp();
        }
        String id = internalName == null ? "" : internalName.trim().toUpperCase(java.util.Locale.ROOT);
        return id.startsWith("DH_") || id.startsWith("DW_") ? mythicMobXp : 0;
    }

    private boolean isActiveMythicMob(UUID entityId) {
        try {
            return io.lumine.mythic.bukkit.MythicBukkit.inst().getMobManager().isActiveMob(entityId);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSwordXpPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !swordXpItemService.isSwordXpItem(event.getItem().getItemStack())) {
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

        int xpAmount = event.getItem().getItemStack().getAmount()
                * swordXpItemService.getXpAmount(event.getItem().getItemStack());
        xpAmount = scaledRewardExperience(sword, xpAmount);
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
