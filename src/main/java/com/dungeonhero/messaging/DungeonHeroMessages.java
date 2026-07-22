package com.dungeonhero.messaging;

import com.dungeonhero.feature.party.PartyService;
import com.dungeonhero.feature.rank.DungeonRankService;
import com.dungeonhero.feature.sword.HeroItemService;
import com.dungeonhero.feature.sword.SwordProgressionService;
import com.dungeonhero.feature.sword.SwordTier;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class DungeonHeroMessages {

  private static final Component RULE =
      Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY);

  private DungeonHeroMessages() {}

  public static void sendSwordStatus(
      CommandSender sender,
      Player player,
      ItemStack sword,
      HeroItemService heroItemService,
      SwordProgressionService progressionService) {
    int cap = progressionService.getMaxSwordLevel(player);
    if (!heroItemService.isHeroSword(sword)) {
      sendPanel(
          sender,
          "HERO SWORD",
          NamedTextColor.GOLD,
          List.of(line("Status", "You do not have a Hero Sword.", NamedTextColor.RED)));
      return;
    }

    int level = heroItemService.getSwordLevel(sword);
    boolean capped = level >= cap;
    String xp =
        capped
            ? "MAX"
            : heroItemService.getSwordXp(sword) + " / " + progressionService.requiredXp(level);
    String progress =
        capped ? "100%" : Math.round(progressionService.getHudProgress(sword, cap) * 100) + "%";
    SwordTier tier = heroItemService.getSwordTier(sword);

    sendPanel(
        sender,
        "HERO SWORD",
        tier.color(),
        List.of(
            line("Tier", tier.displayName(), tier.color()),
            line("Level", level + " / " + cap, NamedTextColor.AQUA),
            line("XP", xp, NamedTextColor.GREEN),
            line("Progress", progress, NamedTextColor.GREEN),
            Component.empty(),
            section("POWER"),
            line(
                "Damage Bonus",
                "+" + formatNumber(heroItemService.getDamageBonus(sword)),
                NamedTextColor.RED),
            line(
                "Prestige",
                String.valueOf(heroItemService.getSwordPrestige(sword)),
                NamedTextColor.LIGHT_PURPLE),
            Component.empty(),
            Component.text("⚔ Your bound Hero Sword", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, true)));
  }

  public static void sendRankStatus(
      CommandSender sender,
      Player player,
      DungeonRankService rankService,
      HeroItemService heroItemService) {
    DungeonRankService.RankDefinition current = rankService.getCurrentRank(player);
    DungeonRankService.RankDefinition next = rankService.getNextRank(player);
    ItemStack sword = heroItemService.findStrongestHeroSword(player);
    int swordLevel = heroItemService.isHeroSword(sword) ? heroItemService.getSwordLevel(sword) : 0;
    String balance = rankService.formatCoins(rankService.getBalance(player));

    List<Component> lines = new java.util.ArrayList<>();
    lines.add(line("Rank", current.number() + " — " + current.name(), NamedTextColor.GOLD));
    lines.add(
        line(
            "Sword Cap",
            swordLevel + " / " + rankService.getSwordLevelCap(player),
            NamedTextColor.AQUA));
    lines.add(Component.empty());
    if (next == null) {
      lines.add(
          Component.text("★ You have reached the highest rank.", NamedTextColor.LIGHT_PURPLE));
    } else {
      lines.add(section("NEXT RANK"));
      lines.add(line("Unlock", next.number() + " — " + next.name(), NamedTextColor.GREEN));
      lines.add(
          line("Required Level", String.valueOf(next.requiredSwordLevel()), NamedTextColor.YELLOW));
      lines.add(
          line(
              "Cost",
              rankService.formatCoins(next.cost()) + " " + rankService.getCoinName(),
              NamedTextColor.GOLD));
    }
    lines.add(Component.empty());
    lines.add(line("Balance", balance + " " + rankService.getCoinName(), NamedTextColor.GREEN));
    sendPanel(sender, "DUNGEON RANK", NamedTextColor.AQUA, lines);
  }

  public static void sendRankUpResult(
      Player player, DungeonRankService.RankUpResult result, DungeonRankService rankService) {
    switch (result.status()) {
      case SUCCESS ->
          sendPanel(
              player,
              "RANK UP COMPLETE",
              NamedTextColor.GREEN,
              List.of(
                  line(
                      "Rank",
                      result.next().number() + " — " + result.next().name(),
                      NamedTextColor.GOLD),
                  line(
                      "Sword Cap",
                      String.valueOf(result.next().swordLevelCap()),
                      NamedTextColor.AQUA),
                  line(
                      "Coins Paid",
                      rankService.formatCoins(result.cost()) + " " + rankService.getCoinName(),
                      NamedTextColor.YELLOW),
                  Component.empty(),
                  Component.text("Your next dungeon awaits.", NamedTextColor.GRAY)));
      case MAX_RANK ->
          sendPanel(
              player,
              "RANK UP",
              NamedTextColor.LIGHT_PURPLE,
              List.of(
                  Component.text(
                      "★ You have reached the highest Dungeon Rank.", NamedTextColor.YELLOW)));
      case NO_SWORD ->
          sendPanel(
              player,
              "RANK UP FAILED",
              NamedTextColor.RED,
              List.of(
                  Component.text("You need your Hero Sword to rank up.", NamedTextColor.YELLOW)));
      case SWORD_LEVEL ->
          sendPanel(
              player,
              "RANK UP FAILED",
              NamedTextColor.RED,
              List.of(
                  line(
                      "Required Level",
                      String.valueOf(result.requiredSwordLevel()),
                      NamedTextColor.YELLOW),
                  line(
                      "Your Level",
                      String.valueOf(result.actualSwordLevel()),
                      NamedTextColor.RED)));
      case INSUFFICIENT_FUNDS ->
          sendPanel(
              player,
              "RANK UP FAILED",
              NamedTextColor.RED,
              List.of(
                  line(
                      "Required",
                      rankService.formatCoins(result.cost()) + " " + rankService.getCoinName(),
                      NamedTextColor.YELLOW),
                  line(
                      "Balance",
                      rankService.formatCoins(result.balance()) + " " + rankService.getCoinName(),
                      NamedTextColor.RED)));
      case PAYMENT_FAILED ->
          sendPanel(
              player,
              "RANK UP FAILED",
              NamedTextColor.RED,
              List.of(
                  Component.text(
                      "The Dungeon Coin transaction could not be saved.", NamedTextColor.YELLOW),
                  Component.text(
                      "Your balance was not changed by DungeonHero.", NamedTextColor.GRAY)));
    }
  }

  public static void sendHelp(CommandSender sender) {
    sendPanel(
        sender,
        "DUNGEON HERO",
        NamedTextColor.GOLD,
        List.of(
            command("/dh forge", "Open the Hero Forge"),
            command("/dh sword", "Show Hero Sword progression"),
            command("/dh rank", "Show Dungeon Rank and balance"),
            command("/dh rankup", "Spend Dungeon Coins to rank up"),
            command("/dh balance", "Show Dungeon Coin balance"),
            command("/dh transfer <player> <amount>", "Transfer Dungeon Coins"),
            command("/dh quest", "View the automatic Dungeon Rush"),
            command("/dh quest top", "View the current or last quest winners"),
            command("/dh party", "Play together with up to 5 heroes"),
            command("/dh prestige", "Prestige a max-level sword"),
            command("/dh dummy", "Test sword damage on a Training Dummy"),
            command("/dh version", "Show the plugin version"),
            command("/dh reload", "Reload configuration (admin)"),
            command("/dh give", "Give a MythicMobs item (admin)"),
            command("/dh give-xp", "Give a native Sword XP item (admin)")));
  }

  public static void sendPartyHelp(Player player) {
    sendPanel(
        player,
        "PARTY COMMANDS",
        NamedTextColor.AQUA,
        List.of(
            command("/dh party create", "Create a party"),
            command("/dh party invite <player>", "Invite a friend"),
            command("/dh party accept", "Accept an invitation"),
            command("/dh party info", "View party members"),
            command("/dh party leave", "Leave your party"),
            command("/dh party kick <player>", "Remove a member (leader)"),
            command("/dh party disband", "Disband your party (leader)")));
  }

  public static void sendPartyInfo(
      Player player, PartyService partyService, HeroItemService heroItemService) {
    PartyService.Party party = partyService.getParty(player);
    if (party == null) {
      sendPanel(
          player,
          "PARTY",
          NamedTextColor.AQUA,
          List.of(
              Component.text("You are not in a party.", NamedTextColor.YELLOW),
              Component.text(
                  "Create one and bring your friends into the dungeon.", NamedTextColor.GRAY)));
      return;
    }

    List<Component> lines = new java.util.ArrayList<>();
    lines.add(
        line(
            "Members",
            party.members().size() + " / " + partyService.getMaxSize(),
            NamedTextColor.AQUA));
    lines.add(Component.empty());
    for (Player member : partyService.getMembers(party)) {
      ItemStack sword = heroItemService.findStrongestHeroSword(member);
      int level = heroItemService.isHeroSword(sword) ? heroItemService.getSwordLevel(sword) : 0;
      String leader = member.getUniqueId().equals(party.leader()) ? "★ " : "» ";
      lines.add(
          Component.text(
                  leader + member.getName() + "  ",
                  member.getUniqueId().equals(party.leader())
                      ? NamedTextColor.GOLD
                      : NamedTextColor.GRAY)
              .append(Component.text("Sword Lv. " + level, NamedTextColor.AQUA)));
    }
    lines.add(Component.empty());
    lines.add(
        Component.text(
            "Mob scaling follows this party when members are nearby.", NamedTextColor.DARK_GRAY));
    sendPanel(player, "PARTY", NamedTextColor.AQUA, lines);
  }

  public static void sendPartyResult(
      Player player, PartyService.ActionResult result, String successMessage) {
    if (result.status() == PartyService.ActionStatus.SUCCESS) {
      sendPanel(
          player,
          "PARTY",
          NamedTextColor.GREEN,
          List.of(Component.text(successMessage, NamedTextColor.GREEN)));
      return;
    }

    String message =
        switch (result.status()) {
          case ALREADY_IN_PARTY -> "You are already in a party.";
          case PARTY_DISABLED -> "Parties are currently disabled by the server configuration.";
          case NO_PARTY -> "You are not in a party.";
          case NOT_LEADER -> "Only the party leader can do that.";
          case TARGET_NOT_FOUND -> "That player is not online.";
          case TARGET_IN_PARTY -> "That player is already in a party.";
          case PARTY_FULL -> "This party is full (maximum 5 players).";
          case CANNOT_INVITE_SELF -> "You cannot invite yourself.";
          case NO_INVITATION -> "You do not have a pending party invitation.";
          case INVITATION_EXPIRED -> "That party invitation has expired.";
          case PARTY_CLOSED -> "That party no longer exists.";
          case TARGET_NOT_IN_PARTY -> "That player is not in your party.";
          case CANNOT_KICK_LEADER -> "The party leader cannot be kicked.";
          case SUCCESS -> "Party action complete.";
        };
    sendPanel(
        player,
        "PARTY",
        NamedTextColor.RED,
        List.of(Component.text(message, NamedTextColor.YELLOW)));
  }

  public static void sendPartyInvite(Player target, String inviterName, int maxSize) {
    sendPanel(
        target,
        "PARTY INVITATION",
        NamedTextColor.GOLD,
        List.of(
            Component.text(inviterName + " invited you to a party.", NamedTextColor.YELLOW),
            Component.text("Party size: up to " + maxSize + " players.", NamedTextColor.GRAY),
            Component.text("Use /dh party accept to join.", NamedTextColor.GREEN)));
  }

  public static Component partyBroadcast(String message) {
    return Component.text("◆ PARTY  ", NamedTextColor.DARK_AQUA)
        .append(Component.text(message, NamedTextColor.GRAY));
  }

  public static Component compactSwordActionBar(
      ItemStack sword,
      HeroItemService heroItemService,
      SwordProgressionService progressionService,
      int cap) {
    SwordTier tier = heroItemService.getSwordTier(sword);
    int level = heroItemService.getSwordLevel(sword);
    String xp =
        level >= cap
            ? "MAX"
            : heroItemService.getSwordXp(sword) + "/" + progressionService.requiredXp(level);
    return Component.text("⚔ " + tier.displayName() + " Hero Sword", tier.color())
        .append(Component.text("  •  Lv. " + level + "/" + cap, NamedTextColor.AQUA))
        .append(Component.text("  •  XP " + xp, NamedTextColor.GREEN));
  }

  private static Component command(String command, String description) {
    return Component.text("» ", NamedTextColor.DARK_GRAY)
        .append(Component.text(command, NamedTextColor.YELLOW))
        .append(Component.text(" — " + description, NamedTextColor.GRAY));
  }

  private static Component section(String title) {
    return Component.text("◆ " + title, NamedTextColor.DARK_AQUA)
        .decoration(TextDecoration.BOLD, true);
  }

  private static Component line(String label, String value, NamedTextColor valueColor) {
    return Component.text("» " + label + ": ", NamedTextColor.GRAY)
        .append(Component.text(value, valueColor));
  }

  private static void sendPanel(
      CommandSender sender, String title, NamedTextColor color, List<Component> lines) {
    sender.sendMessage(RULE);
    sender.sendMessage(Component.text("◆ " + title, color).decoration(TextDecoration.BOLD, true));
    sender.sendMessage(RULE);
    lines.forEach(sender::sendMessage);
    sender.sendMessage(RULE);
  }

  private static String formatNumber(double value) {
    if (value == Math.rint(value)) {
      return String.format(Locale.ROOT, "%,.0f", value);
    }
    return String.format(Locale.ROOT, "%,.2f", value);
  }
}
