package com.dungeonhero.command;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Shared permission, argument, player-resolution, usage, and completion helpers. */
public final class CommandSupport {

  public static final String ADMIN_PERMISSION = "dungeonhero.admin";
  public static final String RELOAD_PERMISSION = "dungeonhero.admin.reload";
  public static final String GIVE_PERMISSION = "dungeonhero.admin.give";
  public static final String DUMMY_REMOVE_PERMISSION = "dungeonhero.admin.dummy.remove";
  public static final String DUMMY_REMOVE_ALL_PERMISSION = "dungeonhero.admin.dummy.remove-all";
  public static final String BALANCE_PERMISSION = "dungeonhero.coins.balance";
  public static final String BALANCE_OTHERS_PERMISSION = "dungeonhero.coins.balance.others";
  public static final String TRANSFER_PERMISSION = "dungeonhero.coins.transfer";
  public static final String ADMIN_COINS_PERMISSION = "dungeonhero.admin.coins";

  private final DungeonHeroCommandContext context;

  public CommandSupport(DungeonHeroCommandContext context) {
    this.context = context;
  }

  public boolean requirePermission(CommandSender sender, String permission) {
    if (sender.hasPermission(ADMIN_PERMISSION) || sender.hasPermission(permission)) return true;
    sender.sendMessage(
        context
            .messageService()
            .text("command.no_permission", "You do not have permission to use that command.")
            .color(NamedTextColor.RED));
    return false;
  }

  public Player requirePlayer(CommandSender sender, String message) {
    if (sender instanceof Player player) return player;
    sender.sendMessage(Component.text(message, NamedTextColor.RED));
    return null;
  }

  public void usage(CommandSender sender, String usage) {
    sender.sendMessage(Component.text(usage, NamedTextColor.YELLOW));
  }

  public Player onlinePlayer(CommandSender sender, String name, String message) {
    Player player = name == null ? null : Bukkit.getPlayerExact(name);
    if (player == null) sender.sendMessage(Component.text(message, NamedTextColor.RED));
    return player;
  }

  /** Resolves an optional party target without pre-empting the service's domain error message. */
  public Player onlinePlayer(String name) {
    return name == null ? null : Bukkit.getPlayerExact(name);
  }

  public OfflinePlayer offlinePlayer(CommandSender sender, String name) {
    OfflinePlayer target = Bukkit.getOfflinePlayer(name);
    if (!target.isOnline() && !target.hasPlayedBefore()) {
      sender.sendMessage(
          Component.text("That player has not joined this server.", NamedTextColor.RED));
      return null;
    }
    return target;
  }

  public Long parseAmount(CommandSender sender, String raw, boolean allowZero) {
    try {
      long amount = Long.parseLong(raw);
      if (amount < 0 || (!allowZero && amount == 0)) throw new NumberFormatException();
      return amount;
    } catch (NumberFormatException exception) {
      sender.sendMessage(
          Component.text(
              allowZero
                  ? "Amount must be a non-negative whole number."
                  : "Amount must be a positive whole number.",
              NamedTextColor.RED));
      return null;
    }
  }

  public List<String> complete(String input, List<String> options) {
    String safeInput = input == null ? "" : input;
    return options.stream()
        .filter(option -> option.regionMatches(true, 0, safeInput, 0, safeInput.length()))
        .sorted()
        .toList();
  }

  public List<String> onlinePlayerNames() {
    return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
  }

  public DungeonHeroCommandContext context() {
    return context;
  }
}
