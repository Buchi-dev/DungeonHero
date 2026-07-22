package com.dungeonhero.feature.sword;

import com.dungeonhero.config.DungeonHeroConfiguration;
import com.dungeonhero.feature.mobregistry.MobRegistryService;
import com.dungeonhero.feature.rank.DungeonRankService;
import com.dungeonhero.messaging.DungeonHeroMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

public final class SwordProgressionService {

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
  private double ascensionXpMultiplier;
  private int maxSwordLevel;
  private SwordProgressionCalculator progressionCalculator;
  private final SwordComparator swordComparator = new SwordComparator();

  public SwordProgressionService(
      JavaPlugin plugin,
      HeroItemService heroItemService,
      SwordXpItemService swordXpItemService,
      DungeonRankService dungeonRankService,
      HeroSwordStorage heroSwordStorage,
      MobRegistryService mobRegistryService) {
    this(
        plugin,
        heroItemService,
        swordXpItemService,
        dungeonRankService,
        heroSwordStorage,
        mobRegistryService,
        DungeonHeroConfiguration.load(plugin));
  }

  public SwordProgressionService(
      JavaPlugin plugin,
      HeroItemService heroItemService,
      SwordXpItemService swordXpItemService,
      DungeonRankService dungeonRankService,
      HeroSwordStorage heroSwordStorage,
      MobRegistryService mobRegistryService,
      DungeonHeroConfiguration configuration) {
    this.plugin = plugin;
    this.heroItemService = heroItemService;
    this.swordXpItemService = swordXpItemService;
    this.dungeonRankService = dungeonRankService;
    this.heroSwordStorage = heroSwordStorage;
    this.mobRegistryService = mobRegistryService;
    reload(configuration);
  }

  public void reload() {
    swordXpItemService.reload();
    reload(DungeonHeroConfiguration.load(plugin));
  }

  public void reload(DungeonHeroConfiguration configuration) {
    DungeonHeroConfiguration.Progression progression = configuration.progression();
    autoMobKillXp = progression.autoMobKillXp();
    hostileMobKillXpOnly = progression.hostileMobKillXpOnly();
    xpPerItem = swordXpItemService.getConfiguredXp();
    xpPerMobKill = Math.max(1, progression.xpPerMobKill());
    mythicMobXp = progression.mythicMobXp();
    baseXpRequired = progression.baseXpRequired();
    xpRequiredMultiplier = progression.xpRequiredMultiplier();
    maxSwordLevel = progression.maxSwordLevel();
    ascensionXpMultiplier = configuration.ascension().xpMultiplier();
    progressionCalculator =
        new SwordProgressionCalculator(baseXpRequired, xpRequiredMultiplier, maxSwordLevel);
  }

  boolean autoMobKillXpEnabled() {
    return autoMobKillXp;
  }

  boolean hostileMobKillXpOnly() {
    return hostileMobKillXpOnly;
  }

  void awardMobKillExperience(Player player) {
    awardExperience(player, xpPerMobKill);
  }

  void awardMythicMobExperience(Player player, String internalName) {
    int reward = mythicXpFor(internalName);
    if (reward > 0) awardExperience(player, reward);
  }

  private void awardExperience(Player player, int amount) {
    if (amount <= 0) {
      return;
    }

    PlayerInventory inventory = player.getInventory();
    int swordSlot = findStrongestSwordSlot(inventory);
    if (swordSlot < 0) {
      player.sendActionBar(
          Component.text("You need your Hero Sword to receive Sword XP.", NamedTextColor.RED));
      return;
    }

    ItemStack sword = inventory.getItem(swordSlot);
    amount = scaledRewardExperience(sword, amount);
    int playerLevelCap = getMaxSwordLevel(player);
    if (heroItemService.getSwordLevel(sword) >= playerLevelCap) {
      player.sendActionBar(
          Component.text("Your Hero Sword has reached the level cap.", NamedTextColor.YELLOW));
      return;
    }

    ProgressionResult result = addExperience(sword, amount, playerLevelCap);
    inventory.setItem(swordSlot, result.sword());
    heroSwordStorage.save(player, result.sword());
    if (result.levelsGained() > 0) {
      player.sendMessage(
          Component.text(
              "Your Hero Sword reached Level " + result.level() + "!", NamedTextColor.GREEN));
    }
    player.sendActionBar(
        DungeonHeroMessages.compactSwordActionBar(
            result.sword(), heroItemService, this, playerLevelCap));
  }

  /** Prestige grants one configured 2x reward multiplier, never 2^prestige. */
  public int scaledRewardExperience(ItemStack sword, int amount) {
    if (!heroItemService.isHeroSword(sword)) {
      return Math.max(0, amount);
    }
    int prestige = heroItemService.getSwordPrestige(sword);
    double multiplier = prestige > 0 ? Math.max(1, Math.min(2, ascensionXpMultiplier)) : 1.0;
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

  boolean collectSwordXp(Player player, ItemStack xpItem) {
    if (!swordXpItemService.isSwordXpItem(xpItem)) return false;
    PlayerInventory inventory = player.getInventory();
    int swordSlot = findStrongestSwordSlot(inventory);
    if (swordSlot < 0) {
      player.sendActionBar(
          Component.text("You need your Hero Sword to collect Sword XP.", NamedTextColor.RED));
      return false;
    }

    ItemStack sword = inventory.getItem(swordSlot);
    int currentLevel = heroItemService.getSwordLevel(sword);
    int playerLevelCap = getMaxSwordLevel(player);
    if (currentLevel >= playerLevelCap) {
      player.sendActionBar(
          Component.text("Your Hero Sword has reached the level cap.", NamedTextColor.YELLOW));
      return false;
    }

    int xpAmount = xpItem.getAmount() * swordXpItemService.getXpAmount(xpItem);
    xpAmount = scaledRewardExperience(sword, xpAmount);
    ProgressionResult result = addExperience(sword, xpAmount, playerLevelCap);
    inventory.setItem(swordSlot, result.sword());
    heroSwordStorage.save(player, result.sword());

    if (result.levelsGained() > 0) {
      player.sendMessage(
          Component.text(
              "Your Hero Sword reached Level " + result.level() + "!", NamedTextColor.GREEN));
    }
    player.sendActionBar(
        DungeonHeroMessages.compactSwordActionBar(
            result.sword(), heroItemService, this, playerLevelCap));
    return true;
  }

  public ProgressionResult addExperience(ItemStack sword, int xpAmount) {
    return addExperience(sword, xpAmount, maxSwordLevel);
  }

  public ProgressionResult addExperience(ItemStack sword, int xpAmount, int levelCap) {
    int level = heroItemService.getSwordLevel(sword);
    int xp = heroItemService.getSwordXp(sword);
    SwordProgressionCalculator.ProgressionResult progression =
        progressionCalculator.addExperience(
            heroItemService.getSwordState(sword), xpAmount, levelCap);
    ItemStack updatedSword = heroItemService.withSwordState(sword, progression.state());
    return new ProgressionResult(
        updatedSword,
        progression.state().level(),
        progression.state().xp(),
        progression.levelsGained());
  }

  public int requiredXp(int level) {
    return progressionCalculator.requiredXp(level);
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
    return Math.max(0.0f, Math.min(0.999f, heroItemService.getSwordXp(sword) / (float) required));
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
    return tier
        + " Hero Sword Level "
        + level
        + " - XP "
        + heroItemService.getSwordXp(sword)
        + "/"
        + requiredXp(level)
        + " - Cap "
        + effectiveCap
        + " - Prestige "
        + prestige;
  }

  public int findStrongestSwordSlot(PlayerInventory inventory) {
    int strongestSlot = -1;
    for (int slot = 0; slot < inventory.getSize(); slot++) {
      ItemStack item = inventory.getItem(slot);
      if (!heroItemService.isHeroSword(item)) {
        continue;
      }
      if (strongestSlot < 0 || isStronger(item, inventory.getItem(strongestSlot))) {
        strongestSlot = slot;
      }
    }
    return strongestSlot;
  }

  private boolean isStronger(ItemStack first, ItemStack second) {
    return swordComparator.isStronger(
        heroItemService.getSwordState(first), heroItemService.getSwordState(second));
  }

  public record ProgressionResult(ItemStack sword, int level, int xp, int levelsGained) {}
}
