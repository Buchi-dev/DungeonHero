package com.dungeonhero.command;

import java.util.List;
import org.bukkit.command.CommandSender;

/** Central tab-completion catalog for all DungeonHero command routes. */
public final class CommandCompletionService {

  public static final List<String> SUBCOMMANDS =
      List.of(
          "help",
          "reload",
          "forge",
          "give",
          "give-xp",
          "sword",
          "rank",
          "rankup",
          "balance",
          "transfer",
          "quest",
          "admin",
          "party",
          "prestige",
          "dummy",
          "version");
  private static final List<String> PARTY_SUBCOMMANDS =
      List.of("create", "invite", "accept", "info", "leave", "kick", "disband", "help");

  private final CommandSupport support;

  public CommandCompletionService(CommandSupport support) {
    this.support = support;
  }

  public List<String> complete(CommandSender sender, String[] args) {
    if (args.length == 1) return support.complete(args[0], SUBCOMMANDS);
    if (args.length == 2 && equals(args[0], "party")) {
      return support.complete(args[1], PARTY_SUBCOMMANDS);
    }
    if (args.length == 2 && equals(args[0], "dummy")) {
      return support.complete(args[1], List.of("spawn", "stats", "remove", "remove-all"));
    }
    if (args.length == 2
        && (equals(args[0], "give") || equals(args[0], "give-xp"))
        && (sender.hasPermission(CommandSupport.ADMIN_PERMISSION)
            || sender.hasPermission(CommandSupport.GIVE_PERMISSION))) {
      return support.complete(args[1], support.onlinePlayerNames());
    }
    if (args.length == 2 && (equals(args[0], "balance") || equals(args[0], "transfer"))) {
      return support.complete(args[1], support.onlinePlayerNames());
    }
    if (args.length == 2 && equals(args[0], "quest")) {
      return support.complete(args[1], List.of("top"));
    }
    if (args.length == 2 && equals(args[0], "admin")) {
      return support.complete(args[1], List.of("coins", "resetsword"));
    }
    if (args.length == 3 && equals(args[0], "admin") && equals(args[1], "resetsword")) {
      return support.complete(args[2], support.onlinePlayerNames());
    }
    if (args.length == 4 && equals(args[0], "admin") && equals(args[1], "resetsword")) {
      return support.complete(args[3], List.of("preview", "confirm"));
    }
    if (args.length == 3 && equals(args[0], "admin") && equals(args[1], "coins")) {
      return support.complete(args[2], List.of("set", "add", "take"));
    }
    if (args.length == 4 && equals(args[0], "admin") && equals(args[1], "coins")) {
      return support.complete(args[3], support.onlinePlayerNames());
    }
    if (args.length == 3
        && equals(args[0], "give")
        && (sender.hasPermission(CommandSupport.ADMIN_PERMISSION)
            || sender.hasPermission(CommandSupport.GIVE_PERMISSION))) {
      return support.complete(args[2], support.context().mythicFragmentService().getFragmentIds());
    }
    if (args.length == 3
        && equals(args[0], "party")
        && (equals(args[1], "invite") || equals(args[1], "kick"))) {
      return support.complete(args[2], support.onlinePlayerNames());
    }
    return List.of();
  }

  private boolean equals(String first, String second) {
    return first != null && first.equalsIgnoreCase(second);
  }
}
