package com.dungeonhero.command;

import com.dungeonhero.messaging.DungeonHeroMessages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Handles rank status and rank-up routes. */
public final class RankCommand implements CommandHandler {

  private final DungeonHeroCommandContext context;
  private final CommandSupport support;

  public RankCommand(DungeonHeroCommandContext context, CommandSupport support) {
    this.context = context;
    this.support = support;
  }

  @Override
  public void execute(CommandSender sender, String[] args) {
    Player player =
        support.requirePlayer(
            sender,
            args[0].equalsIgnoreCase("rankup")
                ? "Only players can rank up."
                : "Only players can view Dungeon Rank.");
    if (player == null) return;
    if (args[0].equalsIgnoreCase("rankup")) {
      DungeonHeroMessages.sendRankUpResult(
          player, context.dungeonRankService().rankUp(player), context.dungeonRankService());
    } else {
      DungeonHeroMessages.sendRankStatus(
          sender, player, context.dungeonRankService(), context.heroItemService());
    }
  }
}
