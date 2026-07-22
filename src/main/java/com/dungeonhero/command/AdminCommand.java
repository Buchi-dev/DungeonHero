package com.dungeonhero.command;

import com.dungeonhero.config.DungeonHeroConfiguration;
import com.dungeonhero.feature.sword.HeroAscensionService;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Handles reload, item grants, coin administration, sword resets, and dummy administration. */
public final class AdminCommand implements CommandHandler {

  private final DungeonHeroCommandContext context;
  private final CommandSupport support;
  private final Supplier<DungeonHeroConfiguration.Admin> adminConfiguration;

  public AdminCommand(DungeonHeroCommandContext context, CommandSupport support) {
    this(context, support, () -> DungeonHeroConfiguration.load(context.plugin()).admin());
  }

  public AdminCommand(
      DungeonHeroCommandContext context,
      CommandSupport support,
      Supplier<DungeonHeroConfiguration.Admin> adminConfiguration) {
    this.context = context;
    this.support = support;
    this.adminConfiguration = adminConfiguration;
  }

  @Override
  public void execute(CommandSender sender, String[] args) {
    switch (args[0].toLowerCase(Locale.ROOT)) {
      case "reload" -> reload(sender);
      case "give" -> give(sender, args);
      case "give-xp" -> giveXp(sender, args);
      case "admin" -> admin(sender, args);
      case "dummy" -> dummy(sender, args);
      default -> {}
    }
  }

  private void reload(CommandSender sender) {
    if (!support.requirePermission(sender, CommandSupport.RELOAD_PERMISSION)) return;
    context.reloadModules().run();
    sender.sendMessage(
        context
            .messageService()
            .text(
                "command.reload_complete",
                "DungeonHero configuration reloaded. Use /mm reload for MythicMobs changes.")
            .color(NamedTextColor.GREEN));
  }

  private void give(CommandSender sender, String[] args) {
    if (!support.requirePermission(sender, CommandSupport.GIVE_PERMISSION)) return;
    if (args.length < 3) {
      support.usage(sender, CommandUsages.GIVE);
      return;
    }
    Player target = support.onlinePlayer(sender, args[1], "That player is not online.");
    if (target == null) return;
    if (!args[2].regionMatches(true, 0, "mm:", 0, 3)) {
      sender.sendMessage(
          Component.text(
              "MythicMobs item IDs must use the mm:<item-id> format.", NamedTextColor.YELLOW));
      return;
    }
    var item = context.mythicFragmentService().createItem(args[2]);
    if (item.isEmpty()) {
      sender.sendMessage(
          Component.text("MythicMobs item not found: " + args[2], NamedTextColor.RED));
      return;
    }
    var inspection = context.mythicFragmentService().inspect(item.get());
    if (!inspection.isValid()) {
      sender.sendMessage(Component.text(inspection.error(), NamedTextColor.RED));
      return;
    }
    context.heroItemService().giveOrDrop(target, item.get());
    sender.sendMessage(
        Component.text("Gave " + args[2] + " to " + target.getName() + ".", NamedTextColor.GREEN));
  }

  private void giveXp(CommandSender sender, String[] args) {
    if (!support.requirePermission(sender, CommandSupport.GIVE_PERMISSION)) return;
    if (args.length < 2 || args.length > 3) {
      support.usage(sender, CommandUsages.GIVE_XP);
      return;
    }
    Player target = support.onlinePlayer(sender, args[1], "That player is not online.");
    if (target == null) return;
    int xpAmount = context.swordXpItemService().getConfiguredXp();
    if (args.length == 3) {
      try {
        xpAmount = Integer.parseInt(args[2]);
      } catch (NumberFormatException exception) {
        sender.sendMessage(
            Component.text("XP must be a positive whole number.", NamedTextColor.RED));
        return;
      }
    }
    if (xpAmount < 1) {
      sender.sendMessage(Component.text("XP must be a positive whole number.", NamedTextColor.RED));
      return;
    }
    context.heroItemService().giveOrDrop(target, context.swordXpItemService().createItem(xpAmount));
    sender.sendMessage(
        Component.text(
            "Gave a " + xpAmount + " XP Sword XP item to " + target.getName() + ".",
            NamedTextColor.GREEN));
  }

  private void admin(CommandSender sender, String[] args) {
    if (args.length >= 2 && args[1].equalsIgnoreCase("resetsword")) {
      resetSword(sender, args);
      return;
    }
    if (args.length < 2 || !args[1].equalsIgnoreCase("coins")) {
      support.usage(sender, CommandUsages.ADMIN_COINS);
      return;
    }
    if (!support.requirePermission(sender, CommandSupport.ADMIN_COINS_PERMISSION)) return;
    if (args.length != 5) {
      support.usage(sender, CommandUsages.ADMIN_COINS);
      return;
    }

    String operation = args[2].toLowerCase(Locale.ROOT);
    OfflinePlayer target = support.offlinePlayer(sender, args[3]);
    Long amount = support.parseAmount(sender, args[4], operation.equals("set"));
    if (target == null || amount == null || !List.of("set", "add", "take").contains(operation)) {
      if (target != null && amount != null) {
        sender.sendMessage(
            Component.text("Operation must be set, add, or take.", NamedTextColor.YELLOW));
      }
      return;
    }

    boolean changed =
        switch (operation) {
          case "set" -> context.dungeonCoinService().setBalance(target.getUniqueId(), amount);
          case "add" -> context.dungeonCoinService().add(target.getUniqueId(), amount);
          case "take" -> context.dungeonCoinService().withdraw(target.getUniqueId(), amount);
          default -> false;
        };
    if (!changed) {
      sender.sendMessage(
          Component.text("The Dungeon Coin operation could not be completed.", NamedTextColor.RED));
      return;
    }

    String targetName = target.getName() == null ? args[3] : target.getName();
    context
        .plugin()
        .getLogger()
        .info(sender.getName() + " used coins " + operation + " on " + targetName + ": " + amount);
    sender.sendMessage(
        Component.text(
            "Updated "
                + targetName
                + " to "
                + context
                    .dungeonCoinService()
                    .format(context.dungeonCoinService().getBalance(target.getUniqueId()))
                + " Dungeon Coins.",
            NamedTextColor.GREEN));
  }

  private void resetSword(CommandSender sender, String[] args) {
    String permission = adminConfiguration.get().resetSwordPermission();
    if (!support.requirePermission(sender, permission)) return;
    if (args.length != 4
        || (!args[3].equalsIgnoreCase("preview") && !args[3].equalsIgnoreCase("confirm"))) {
      support.usage(sender, CommandUsages.RESET_SWORD);
      return;
    }
    Player target =
        support.onlinePlayer(
            sender, args[2], "The target player must be online for a safe atomic reset.");
    if (target == null) return;
    if (args[3].equalsIgnoreCase("preview")) {
      var snapshot = context.heroAscensionService().previewReset(target);
      sender.sendMessage(
          Component.text(
              "Hero Sword reset preview for "
                  + target.getName()
                  + ": "
                  + "Level "
                  + snapshot.currentLevel()
                  + " -> "
                  + snapshot.resultingLevel()
                  + ", XP "
                  + snapshot.currentXp()
                  + " -> "
                  + snapshot.resultingXp()
                  + ", Prestige "
                  + snapshot.currentPrestige()
                  + " -> "
                  + snapshot.resultingPrestige()
                  + ", Fragment Damage +"
                  + formatNumber(snapshot.currentDamage())
                  + " -> +0"
                  + ", Rank "
                  + snapshot.currentRank()
                  + " -> "
                  + snapshot.resultingRank()
                  + ", Coins preserved: "
                  + snapshot.coins(),
              NamedTextColor.YELLOW));
      return;
    }
    var result = context.heroAscensionService().resetSword(target, sender.getName());
    if (result.status() == HeroAscensionService.ResetStatus.RESET) {
      sender.sendMessage(
          Component.text(
              "Hero Sword reset completed for " + target.getName() + ".", NamedTextColor.GREEN));
    } else {
      sender.sendMessage(
          Component.text(
              "Hero Sword reset could not be completed: " + result.status(), NamedTextColor.RED));
    }
  }

  private void dummy(CommandSender sender, String[] args) {
    String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "spawn";
    if (action.equals("remove-all")) {
      if (support.requirePermission(sender, CommandSupport.DUMMY_REMOVE_ALL_PERMISSION)) {
        int removed = context.trainingDummyService().removeAll();
        sender.sendMessage(
            context
                .messageService()
                .text(
                    "command.dummy_remove_all",
                    "Removed {count} DungeonHero Training Dummy target(s) from loaded worlds.")
                .replaceText(
                    builder -> builder.matchLiteral("{count}").replacement(String.valueOf(removed)))
                .color(NamedTextColor.GREEN));
      }
      return;
    }
    Player player = support.requirePlayer(sender, "Only players can use Training Dummies.");
    if (player == null) return;
    switch (action) {
      case "spawn", "open" -> context.trainingDummyService().open(player);
      case "stats" -> context.trainingDummyService().sendStats(player);
      case "remove" -> {
        if (support.requirePermission(sender, CommandSupport.DUMMY_REMOVE_PERMISSION)) {
          context.trainingDummyService().remove(player);
        }
      }
      default -> support.usage(sender, CommandUsages.DUMMY);
    }
  }

  private String formatNumber(double value) {
    return value == Math.rint(value)
        ? String.format(Locale.ROOT, "%.0f", value)
        : String.format(Locale.ROOT, "%.2f", value);
  }
}
