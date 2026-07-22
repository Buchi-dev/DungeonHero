package com.dungeonhero.command;

import com.dungeonhero.feature.coins.DungeonCoinService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Handles player and admin-facing Dungeon Coin balance and transfer routes. */
public final class EconomyCommand implements CommandHandler {

  private final DungeonCoinService coinService;
  private final CommandSupport support;

  public EconomyCommand(DungeonHeroCommandContext context, CommandSupport support) {
    this.coinService = context.dungeonCoinService();
    this.support = support;
  }

  @Override
  public void execute(CommandSender sender, String[] args) {
    if (args[0].equalsIgnoreCase("balance")) showBalance(sender, args);
    else transfer(sender, args);
  }

  private void showBalance(CommandSender sender, String[] args) {
    if (args.length == 1) {
      if (!(sender instanceof Player player)) {
        support.usage(sender, CommandUsages.BALANCE_PLAYER);
        return;
      }
      if (!support.requirePermission(sender, CommandSupport.BALANCE_PERMISSION)) return;
      sendBalance(sender, player.getUniqueId(), player.getName());
      return;
    }
    if (args.length != 2
        || !support.requirePermission(sender, CommandSupport.BALANCE_OTHERS_PERMISSION)) return;
    OfflinePlayer target = support.offlinePlayer(sender, args[1]);
    if (target != null)
      sendBalance(
          sender, target.getUniqueId(), target.getName() == null ? args[1] : target.getName());
  }

  private void sendBalance(CommandSender sender, java.util.UUID playerId, String name) {
    sender.sendMessage(
        Component.text(
            name
                + " has "
                + coinService.format(coinService.getBalance(playerId))
                + " Dungeon Coins.",
            NamedTextColor.GOLD));
  }

  private void transfer(CommandSender sender, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(
          Component.text("Only players can transfer Dungeon Coins.", NamedTextColor.RED));
      return;
    }
    if (!support.requirePermission(sender, CommandSupport.TRANSFER_PERMISSION)) return;
    if (args.length != 3) {
      support.usage(sender, CommandUsages.TRANSFER);
      return;
    }
    OfflinePlayer target = support.offlinePlayer(sender, args[1]);
    Long amount = support.parseAmount(sender, args[2], false);
    if (target == null || amount == null) return;
    DungeonCoinService.TransferResult result =
        coinService.transfer(player.getUniqueId(), target.getUniqueId(), amount);
    switch (result.status()) {
      case SUCCESS ->
          sender.sendMessage(
              Component.text(
                  "Transferred "
                      + coinService.format(amount)
                      + " Dungeon Coins to "
                      + (target.getName() == null ? args[1] : target.getName())
                      + ".",
                  NamedTextColor.GREEN));
      case SELF_TRANSFER ->
          sender.sendMessage(
              Component.text("You cannot transfer Dungeon Coins to yourself.", NamedTextColor.RED));
      case INSUFFICIENT_FUNDS ->
          sender.sendMessage(
              Component.text("You do not have enough Dungeon Coins.", NamedTextColor.RED));
      case TARGET_BALANCE_LIMIT ->
          sender.sendMessage(
              Component.text("The target balance is too large.", NamedTextColor.RED));
      case STORAGE_FAILURE ->
          sender.sendMessage(
              Component.text("Dungeon Coins could not be saved.", NamedTextColor.RED));
      case INVALID_AMOUNT ->
          sender.sendMessage(Component.text("Amount must be positive.", NamedTextColor.RED));
    }
  }
}
