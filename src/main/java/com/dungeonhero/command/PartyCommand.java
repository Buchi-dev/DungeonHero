package com.dungeonhero.command;

import com.dungeonhero.feature.party.PartyService;
import com.dungeonhero.feature.sword.HeroItemService;
import com.dungeonhero.messaging.DungeonHeroMessages;
import java.util.Locale;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Handles party actions, broadcasts, and party help. */
public final class PartyCommand implements CommandHandler {

  private final PartyService partyService;
  private final HeroItemService heroItemService;
  private final CommandSupport support;

  public PartyCommand(DungeonHeroCommandContext context, CommandSupport support) {
    this.partyService = context.partyService();
    this.heroItemService = context.heroItemService();
    this.support = support;
  }

  @Override
  public void execute(CommandSender sender, String[] args) {
    Player player = support.requirePlayer(sender, "Only players can use party commands.");
    if (player == null) return;
    if (args.length < 2 || args[1].equalsIgnoreCase("help")) {
      DungeonHeroMessages.sendPartyHelp(player);
      return;
    }
    switch (args[1].toLowerCase(Locale.ROOT)) {
      case "create" ->
          DungeonHeroMessages.sendPartyResult(
              player, partyService.create(player), "Party created. Invite your friends!");
      case "invite" -> invite(player, args);
      case "accept" -> accept(player);
      case "info" -> DungeonHeroMessages.sendPartyInfo(player, partyService, heroItemService);
      case "leave" -> leave(player);
      case "kick" -> kick(player, args);
      case "disband" -> disband(player);
      default -> DungeonHeroMessages.sendPartyHelp(player);
    }
  }

  private void invite(Player player, String[] args) {
    Player target = args.length >= 3 ? support.onlinePlayer(args[2]) : null;
    var result = partyService.invite(player, target);
    DungeonHeroMessages.sendPartyResult(
        player,
        result,
        target == null ? "Invitation sent." : "Invitation sent to " + target.getName() + ".");
    if (result.status() == PartyService.ActionStatus.SUCCESS && result.target() != null) {
      DungeonHeroMessages.sendPartyInvite(
          result.target(), result.inviterName(), partyService.getMaxSize());
    }
  }

  private void accept(Player player) {
    var result = partyService.accept(player);
    DungeonHeroMessages.sendPartyResult(player, result, "You joined the party.");
    if (result.status() == PartyService.ActionStatus.SUCCESS) {
      partyService.broadcast(
          result.party(),
          DungeonHeroMessages.partyBroadcast(player.getName() + " joined the party."));
    }
  }

  private void leave(Player player) {
    String name = player.getName();
    var result = partyService.leave(player);
    DungeonHeroMessages.sendPartyResult(player, result, "You left the party.");
    if (result.status() == PartyService.ActionStatus.SUCCESS) {
      partyService.broadcast(
          result.party(), DungeonHeroMessages.partyBroadcast(name + " left the party."));
    }
  }

  private void kick(Player player, String[] args) {
    Player target = args.length >= 3 ? support.onlinePlayer(args[2]) : null;
    var result = partyService.kick(player, target);
    DungeonHeroMessages.sendPartyResult(
        player,
        result,
        target == null ? "Player removed." : target.getName() + " was removed from the party.");
    if (result.status() == PartyService.ActionStatus.SUCCESS && result.target() != null) {
      result
          .target()
          .sendMessage(DungeonHeroMessages.partyBroadcast("You were removed from the party."));
      partyService.broadcast(
          result.party(),
          DungeonHeroMessages.partyBroadcast(
              result.target().getName() + " was removed from the party."));
    }
  }

  private void disband(Player player) {
    var result = partyService.disband(player);
    DungeonHeroMessages.sendPartyResult(player, result, "Party disbanded.");
    if (result.status() == PartyService.ActionStatus.SUCCESS) {
      partyService.broadcast(
          result.party(), DungeonHeroMessages.partyBroadcast("The party was disbanded."));
    }
  }
}
