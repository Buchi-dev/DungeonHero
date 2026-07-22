package com.dungeonhero.command;

import com.dungeonhero.feature.armor.ArmorTier;
import com.dungeonhero.feature.armor.HeroArmorListener;
import com.dungeonhero.feature.armor.HeroArmorService;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Handles shared Hero Armor status and the armor-compatible Forge route. */
public final class ArmorCommand implements CommandHandler {

  private final DungeonHeroCommandContext context;
  private final CommandSupport support;

  public ArmorCommand(DungeonHeroCommandContext context, CommandSupport support) {
    this.context = context;
    this.support = support;
  }

  @Override
  public void execute(CommandSender sender, String[] args) {
    Player player = support.requirePlayer(sender, "Only players can view Hero Armor.");
    if (player == null) return;
    if (args.length > 1 && args[1].equalsIgnoreCase("forge")) {
      context.forgeGui().open(player);
      return;
    }
    status(player);
  }

  private void status(Player player) {
    HeroArmorService armor = context.heroArmorService();
    HeroArmorListener listener = context.heroArmorListener();
    listener.normalize(player);
    var state = armor.loadOrDefault(player);
    int rank = context.dungeonRankService().getRank(player);
    int cap = context.armorProgressionService().getMaxArmorLevel(player);
    int pieces = armor.equippedPieceCount(player);
    ArmorTier tier = ArmorTier.fromLevel(state.level());
    String xp =
        state.level() >= cap
            ? "MAX"
            : state.xp() + " / " + context.armorProgressionService().requiredXp(state.level());
    String bonuses =
        pieces >= 4
            ? "2-piece, 3-piece, Last Stand"
            : pieces >= 3 ? "2-piece, 3-piece" : pieces >= 2 ? "2-piece" : "None";
    player.sendMessage(Component.text("━━ AEGIS OF THE FALLEN ━━", tier.color()));
    line(player, "Armor Tier", tier.displayName(), tier.color());
    line(player, "Armor Level", state.level() + " / " + cap, NamedTextColor.AQUA);
    line(player, "Armor XP", xp, NamedTextColor.GREEN);
    line(player, "Armor Level Cap", String.valueOf(cap), NamedTextColor.YELLOW);
    line(player, "Armor Bonus", "+" + format(state.armorBonus()), NamedTextColor.BLUE);
    line(
        player,
        "Effective Armor Bonus",
        "+" + format(armor.effectiveBonus(state, rank)),
        NamedTextColor.GREEN);
    line(
        player,
        "Inactive Overflow",
        "+" + format(armor.inactiveOverflow(state, rank)),
        NamedTextColor.GRAY);
    line(player, "Equipped Hero Armor Pieces", pieces + " / 4", NamedTextColor.AQUA);
    line(player, "Active Set Bonuses", bonuses, NamedTextColor.LIGHT_PURPLE);
    if (pieces >= 4) {
      long remaining = context.armorProtectionListener().remainingCooldown(player);
      line(
          player,
          "Last Stand",
          remaining == 0 ? "Ready" : (remaining / 1000 + 1) + "s cooldown",
          NamedTextColor.GOLD);
    }
  }

  private void line(Player player, String label, String value, NamedTextColor color) {
    player.sendMessage(
        Component.text("» " + label + ": ", NamedTextColor.GRAY)
            .append(Component.text(value, color)));
  }

  private String format(double value) {
    return value == Math.rint(value)
        ? String.format(Locale.ROOT, "%.0f", value)
        : String.format(Locale.ROOT, "%.2f", value);
  }
}
