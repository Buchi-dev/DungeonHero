package com.dungeonhero.command;

import com.dungeonhero.feature.quest.DungeonRushService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Handles Dungeon Rush status and leaderboard routes. */
public final class QuestCommand implements CommandHandler {

  private final DungeonRushService dungeonRushService;
  private final CommandSupport support;

  public QuestCommand(DungeonHeroCommandContext context, CommandSupport support) {
    this.dungeonRushService = context.dungeonRushService();
    this.support = support;
  }

  @Override
  public void execute(CommandSender sender, String[] args) {
    Player player = support.requirePlayer(sender, "Only players can view Dungeon Rush quests.");
    if (player == null) return;
    if (args.length >= 2 && args[1].equalsIgnoreCase("top")) {
      dungeonRushService.sendTop(player);
    } else {
      dungeonRushService.sendStatus(player);
    }
  }
}
