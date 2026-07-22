package com.dungeonhero.feature.quest;

import com.dungeonhero.common.ItemDeliveryService;
import com.dungeonhero.common.PlayerResolver;
import com.dungeonhero.feature.coins.DungeonCoinService;
import com.dungeonhero.feature.sword.SwordProgressionService;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Delivers configured Dungeon Rush rewards at the Bukkit boundary. */
public final class DungeonRushRewardDistributor {

  private final JavaPlugin plugin;
  private final DungeonCoinService coinService;
  private final SwordProgressionService swordProgressionService;
  private final PlayerResolver playerResolver;
  private final ItemDeliveryService itemDeliveryService;

  public DungeonRushRewardDistributor(
      JavaPlugin plugin,
      DungeonCoinService coinService,
      SwordProgressionService swordProgressionService,
      PlayerResolver playerResolver,
      ItemDeliveryService itemDeliveryService) {
    this.plugin = plugin;
    this.coinService = coinService;
    this.swordProgressionService = swordProgressionService;
    this.playerResolver = playerResolver;
    this.itemDeliveryService = itemDeliveryService;
  }

  public List<String> distribute(
      int place,
      QuestScoringPolicy.Score result,
      List<RewardPolicy.Reward> rewards,
      Function<String, String> commandRenderer) {
    List<String> summary = new ArrayList<>();
    Player player = playerResolver.resolve(result.playerId());
    for (RewardPolicy.Reward reward : rewards) {
      switch (reward.type()) {
        case COINS -> {
          if (coinService.add(result.playerId(), reward.amount())) {
            summary.add("+" + coinService.format(reward.amount()) + " Dungeon Coins");
          }
        }
        case SWORD_XP -> {
          if (player != null && reward.amount() > 0) {
            swordProgressionService.awardQuestExperience(
                player, (int) Math.min(Integer.MAX_VALUE, reward.amount()));
            summary.add("+" + reward.amount() + " Sword XP");
          }
        }
        case ITEM -> {
          if (player == null) continue;
          Material material = Material.matchMaterial(reward.material());
          if (material == null || material.isAir() || reward.amount() <= 0) {
            plugin.getLogger().warning("Invalid Dungeon Rush item reward: " + reward.material());
            continue;
          }
          itemDeliveryService.giveStacked(player, material, reward.amount());
          summary.add(reward.amount() + " " + material.name());
        }
        case COMMAND -> {
          String command = commandRenderer.apply(reward.command());
          if (command.startsWith("/")) command = command.substring(1);
          if (!command.isBlank()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            summary.add("custom reward");
          }
        }
        default -> plugin.getLogger().warning("Unknown Dungeon Rush reward type: " + reward.type());
      }
    }
    return summary;
  }
}
