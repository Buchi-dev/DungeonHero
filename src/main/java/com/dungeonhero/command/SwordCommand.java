package com.dungeonhero.command;

import com.dungeonhero.feature.sword.HeroAscensionService;
import com.dungeonhero.messaging.DungeonHeroMessages;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Handles sword status, forge, and prestige routes. */
public final class SwordCommand implements CommandHandler {

  private final DungeonHeroCommandContext context;
  private final CommandSupport support;

  public SwordCommand(DungeonHeroCommandContext context, CommandSupport support) {
    this.context = context;
    this.support = support;
  }

  @Override
  public void execute(CommandSender sender, String[] args) {
    switch (args[0].toLowerCase(Locale.ROOT)) {
      case "sword" -> sword(sender);
      case "forge" -> forge(sender);
      case "prestige" -> prestige(sender, args);
      default -> {}
    }
  }

  private void sword(CommandSender sender) {
    Player player = support.requirePlayer(sender, "Only players can view Sword XP.");
    if (player == null) return;
    DungeonHeroMessages.sendSwordStatus(
        sender,
        player,
        context.heroPlayerListener().restoreSavedSword(player),
        context.heroItemService(),
        context.swordProgressionService());
  }

  private void forge(CommandSender sender) {
    Player player = support.requirePlayer(sender, "Only players can use the Hero Forge.");
    if (player != null) context.forgeGui().open(player);
  }

  private void prestige(CommandSender sender, String[] args) {
    Player player = support.requirePlayer(sender, "Only players can prestige a Hero Sword.");
    if (player == null) return;
    boolean confirm = args.length >= 2 && args[1].equalsIgnoreCase("confirm");
    HeroAscensionService.AscensionResult result =
        confirm
            ? context.heroAscensionService().confirm(player)
            : context.heroAscensionService().request(player);
    switch (result) {
      case CONFIRMATION_REQUIRED ->
          player.sendMessage(
              Component.text(
                  "Hero Ascension will reset your sword to Level 1 and preserve rank, coins, and fragments. "
                      + "Run /dh prestige confirm within 30 seconds to continue.",
                  NamedTextColor.YELLOW));
      case ASCENDED ->
          player.sendMessage(
              Component.text(
                  "Hero Ascension complete. You are now Prestige "
                      + context
                          .heroItemService()
                          .getSwordPrestige(
                              context.heroItemService().findStrongestHeroSword(player))
                      + ".",
                  NamedTextColor.LIGHT_PURPLE));
      case NO_SWORD ->
          player.sendMessage(Component.text("You do not have a Hero Sword.", NamedTextColor.RED));
      case LEVEL_REQUIRED ->
          player.sendMessage(
              Component.text(
                  "Reach Sword Level "
                      + context.heroAscensionService().requiredLevel()
                      + " before Hero Ascension.",
                  NamedTextColor.YELLOW));
      case MAX_PRESTIGE ->
          player.sendMessage(
              Component.text(
                  "Your Hero Sword has reached the Prestige cap of "
                      + context.heroAscensionService().maxPrestige()
                      + ".",
                  NamedTextColor.YELLOW));
      case DISABLED ->
          player.sendMessage(
              Component.text("Hero Ascension is currently disabled.", NamedTextColor.YELLOW));
      case STORAGE_FAILURE ->
          player.sendMessage(
              Component.text(
                  "Hero Ascension could not be saved and was rolled back.", NamedTextColor.RED));
    }
  }
}
