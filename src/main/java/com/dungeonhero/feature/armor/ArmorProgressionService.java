package com.dungeonhero.feature.armor;

import com.dungeonhero.config.DungeonHeroConfiguration;
import com.dungeonhero.feature.mobregistry.MobRegistryService;
import com.dungeonhero.feature.rank.DungeonRankService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Awards the same configured progression events as sword XP to shared Hero Armor. */
public final class ArmorProgressionService {

  private final HeroArmorService armorService;
  private final HeroArmorStorage storage;
  private final DungeonRankService rankService;
  private final MobRegistryService mobRegistryService;
  private int xpPerMobKill;
  private int mythicMobXp;
  private int baseXpRequired;
  private double xpRequiredMultiplier;
  private int maxArmorLevel;
  private boolean enabled;
  private ArmorProgressionCalculator calculator;

  public ArmorProgressionService(
      JavaPlugin plugin,
      HeroArmorService armorService,
      DungeonRankService rankService,
      MobRegistryService mobRegistryService,
      DungeonHeroConfiguration configuration) {
    this.armorService = armorService;
    storage = armorService.storage();
    this.rankService = rankService;
    this.mobRegistryService = mobRegistryService;
    reload(configuration);
  }

  public void reload(DungeonHeroConfiguration configuration) {
    DungeonHeroConfiguration.Progression progression = configuration.progression();
    xpPerMobKill = Math.max(1, progression.xpPerMobKill());
    mythicMobXp = Math.max(0, progression.mythicMobXp());
    baseXpRequired = progression.baseXpRequired();
    xpRequiredMultiplier = progression.xpRequiredMultiplier();
    maxArmorLevel = configuration.armor().maxLevel();
    enabled = configuration.armor().enabled();
    calculator =
        new ArmorProgressionCalculator(baseXpRequired, xpRequiredMultiplier, maxArmorLevel);
  }

  public void awardMobKillExperience(Player player) {
    awardExperience(player, xpPerMobKill);
  }

  public void awardMythicMobExperience(Player player, String internalName) {
    int amount = mythicXpFor(internalName);
    if (amount > 0) awardExperience(player, amount);
  }

  public void awardQuestExperience(Player player, int amount) {
    awardExperience(player, amount);
  }

  public boolean collectArmorXp(Player player, ItemStack xpItem, int xpPerItem) {
    if (xpItem == null || xpPerItem <= 0) return false;
    int amount =
        (int)
            Math.min(
                Integer.MAX_VALUE,
                Math.max(0L, (long) Math.max(0, xpItem.getAmount()) * xpPerItem));
    awardExperience(player, amount);
    return true;
  }

  public void awardExperience(Player player, int amount) {
    if (player == null || amount <= 0 || !armorEnabled()) return;
    int cap = getMaxArmorLevel(player);
    HeroArmorState current =
        storage.loadOrDefault(player).withFragmentRank(rankService.getRank(player));
    if (current.level() >= cap) {
      storage.save(player, current);
      player.sendActionBar(
          Component.text("Your Hero Armor has reached the level cap.", NamedTextColor.YELLOW));
      syncPieces(player, current);
      return;
    }
    ArmorProgressionCalculator.ProgressionResult result =
        calculator.addExperience(current, amount, cap);
    storage.save(player, result.state());
    syncPieces(player, result.state());
    if (result.levelsGained() > 0) {
      player.sendMessage(
          Component.text(
              "Your Hero Armor reached Level " + result.state().level() + "!",
              NamedTextColor.GREEN));
    }
    player.sendActionBar(
        Component.text(
            "Armor XP: "
                + result.state().xp()
                + "/"
                + calculator.requiredXp(result.state().level())
                + "  Armor Lv. "
                + result.state().level()
                + "/"
                + cap,
            NamedTextColor.AQUA));
  }

  public int requiredXp(int level) {
    return calculator.requiredXp(level);
  }

  public int getMaxArmorLevel(Player player) {
    return Math.max(1, Math.min(maxArmorLevel, rankService.getArmorLevelCap(player)));
  }

  public boolean armorEnabled() {
    return enabled;
  }

  public HeroArmorState state(Player player) {
    return storage.loadOrDefault(player).withFragmentRank(rankService.getRank(player));
  }

  private void syncPieces(Player player, HeroArmorState state) {
    for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
      ItemStack item = player.getInventory().getItem(slot);
      if (armorService.isHeroArmor(item))
        player.getInventory().setItem(slot, armorService.withState(item, state));
    }
  }

  private int mythicXpFor(String internalName) {
    if (mobRegistryService.find(internalName).isPresent()) {
      return mobRegistryService.profileOrDefault(internalName).swordXp();
    }
    String id = internalName == null ? "" : internalName.trim().toUpperCase(java.util.Locale.ROOT);
    return id.startsWith("DH_") || id.startsWith("DW_") ? mythicMobXp : 0;
  }
}
