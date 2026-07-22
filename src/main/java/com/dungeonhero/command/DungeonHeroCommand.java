package com.dungeonhero.command;

import com.dungeonhero.feature.arena.ArenaSessionManager;
import com.dungeonhero.feature.armor.ArmorProgressionService;
import com.dungeonhero.feature.armor.ArmorProtectionListener;
import com.dungeonhero.feature.armor.HeroArmorListener;
import com.dungeonhero.feature.armor.HeroArmorService;
import com.dungeonhero.feature.coins.DungeonCoinService;
import com.dungeonhero.feature.forge.ForgeGui;
import com.dungeonhero.feature.mobregistry.MobRegistryService;
import com.dungeonhero.feature.party.PartyService;
import com.dungeonhero.feature.quest.DungeonRushService;
import com.dungeonhero.feature.rank.DungeonRankService;
import com.dungeonhero.feature.sword.HeroAscensionService;
import com.dungeonhero.feature.sword.HeroItemService;
import com.dungeonhero.feature.sword.HeroPlayerListener;
import com.dungeonhero.feature.sword.HeroSwordStorage;
import com.dungeonhero.feature.sword.SwordHudService;
import com.dungeonhero.feature.sword.SwordProgressionService;
import com.dungeonhero.feature.sword.SwordXpItemService;
import com.dungeonhero.feature.trainingdummy.TrainingDummyService;
import com.dungeonhero.framework.GameplayFramework;
import com.dungeonhero.integration.mythicmobs.HeroRareDropBonusListener;
import com.dungeonhero.integration.mythicmobs.HeroSwordMobScaler;
import com.dungeonhero.integration.mythicmobs.MythicFragmentService;
import com.dungeonhero.messaging.DungeonHeroMessages;
import com.dungeonhero.messaging.MessageService;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.java.JavaPlugin;

/** Small command router; business behavior lives in focused command handlers. */
public final class DungeonHeroCommand implements TabExecutor {

  private final JavaPlugin plugin;
  private final Map<String, CommandHandler> handlers;
  private final CommandCompletionService completionService;

  public DungeonHeroCommand(
      JavaPlugin plugin,
      HeroItemService heroItemService,
      HeroSwordStorage heroSwordStorage,
      HeroArmorService heroArmorService,
      HeroArmorListener heroArmorListener,
      ArmorProgressionService armorProgressionService,
      ArmorProtectionListener armorProtectionListener,
      HeroPlayerListener heroPlayerListener,
      MythicFragmentService mythicFragmentService,
      HeroSwordMobScaler heroSwordMobScaler,
      SwordXpItemService swordXpItemService,
      SwordHudService swordHudService,
      SwordProgressionService swordProgressionService,
      DungeonRankService dungeonRankService,
      PartyService partyService,
      TrainingDummyService trainingDummyService,
      ForgeGui forgeGui,
      MessageService messageService,
      DungeonCoinService dungeonCoinService,
      MobRegistryService mobRegistryService,
      GameplayFramework gameplayFramework,
      DungeonRushService dungeonRushService,
      HeroAscensionService heroAscensionService,
      HeroRareDropBonusListener rareDropBonusListener,
      ArenaSessionManager arenaSessionManager) {
    this(
        plugin,
        heroItemService,
        heroSwordStorage,
        heroArmorService,
        heroArmorListener,
        armorProgressionService,
        armorProtectionListener,
        heroPlayerListener,
        mythicFragmentService,
        heroSwordMobScaler,
        swordXpItemService,
        swordHudService,
        swordProgressionService,
        dungeonRankService,
        partyService,
        trainingDummyService,
        forgeGui,
        messageService,
        dungeonCoinService,
        mobRegistryService,
        gameplayFramework,
        dungeonRushService,
        heroAscensionService,
        rareDropBonusListener,
        arenaSessionManager,
        plugin::reloadConfig);
  }

  public DungeonHeroCommand(
      JavaPlugin plugin,
      HeroItemService heroItemService,
      HeroSwordStorage heroSwordStorage,
      HeroArmorService heroArmorService,
      HeroArmorListener heroArmorListener,
      ArmorProgressionService armorProgressionService,
      ArmorProtectionListener armorProtectionListener,
      HeroPlayerListener heroPlayerListener,
      MythicFragmentService mythicFragmentService,
      HeroSwordMobScaler heroSwordMobScaler,
      SwordXpItemService swordXpItemService,
      SwordHudService swordHudService,
      SwordProgressionService swordProgressionService,
      DungeonRankService dungeonRankService,
      PartyService partyService,
      TrainingDummyService trainingDummyService,
      ForgeGui forgeGui,
      MessageService messageService,
      DungeonCoinService dungeonCoinService,
      MobRegistryService mobRegistryService,
      GameplayFramework gameplayFramework,
      DungeonRushService dungeonRushService,
      HeroAscensionService heroAscensionService,
      HeroRareDropBonusListener rareDropBonusListener,
      ArenaSessionManager arenaSessionManager,
      Runnable reloadModules) {
    this.plugin = plugin;
    this.arenaSessionManager = arenaSessionManager;
    DungeonHeroCommandContext context =
        new DungeonHeroCommandContext(
            plugin,
            heroItemService,
            heroSwordStorage,
            heroArmorService,
            heroArmorListener,
            armorProgressionService,
            armorProtectionListener,
            heroPlayerListener,
            mythicFragmentService,
            heroSwordMobScaler,
            swordXpItemService,
            swordHudService,
            swordProgressionService,
            dungeonRankService,
            partyService,
            trainingDummyService,
            forgeGui,
            messageService,
            dungeonCoinService,
            mobRegistryService,
            gameplayFramework,
            dungeonRushService,
            heroAscensionService,
            rareDropBonusListener,
            reloadModules == null ? () -> {} : reloadModules);
    CommandSupport support = new CommandSupport(context);

    SwordCommand sword = new SwordCommand(context, support);
    RankCommand rank = new RankCommand(context, support);
    AdminCommand admin = new AdminCommand(context, support);
    this.handlers =
        Map.ofEntries(
            Map.entry("sword", sword),
            Map.entry("armor", new ArmorCommand(context, support)),
            Map.entry("forge", sword),
            Map.entry("prestige", sword),
            Map.entry("rank", rank),
            Map.entry("rankup", rank),
            Map.entry("quest", new QuestCommand(context, support)),
            Map.entry("party", new PartyCommand(context, support)),
            Map.entry("balance", new EconomyCommand(context, support)),
            Map.entry("transfer", new EconomyCommand(context, support)),
            Map.entry("reload", admin),
            Map.entry("give", admin),
            Map.entry("give-xp", admin),
            Map.entry("admin", admin),
            Map.entry("dummy", admin));
    this.completionService = new CommandCompletionService(support);
  }

  private final ArenaSessionManager arenaSessionManager;

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!command.getName().equalsIgnoreCase("dungeonhero")) return false;
    if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
      DungeonHeroMessages.sendHelp(sender);
      return true;
    }
    if (args[0].equalsIgnoreCase("version")) {
      sender.sendMessage(
          Component.text(
              "DungeonHero v" + plugin.getDescription().getVersion(), NamedTextColor.GOLD));
      return true;
    }
    if (args[0].equalsIgnoreCase("arena")) {
      return handleArena(sender, args);
    }
    CommandHandler handler = handlers.get(args[0].toLowerCase(Locale.ROOT));
    if (handler == null) {
      sender.sendMessage(
          Component.text("Unknown DungeonHero command. Use /dh help.", NamedTextColor.RED));
      return true;
    }
    handler.execute(sender, args);
    return true;
  }

  private boolean handleArena(CommandSender sender, String[] args) {
    if (args.length >= 2 && args[1].equalsIgnoreCase("escape")) {
      if (!(sender instanceof org.bukkit.entity.Player player)) {
        sender.sendMessage(Component.text("Only players can escape an arena.", NamedTextColor.RED));
      } else if (!arenaSessionManager.escape(player)) {
        sender.sendMessage(Component.text("You are not in a Blood Arena.", NamedTextColor.YELLOW));
      } else {
        sender.sendMessage(Component.text("You escaped the Blood Arena.", NamedTextColor.GREEN));
      }
      return true;
    }
    if (args.length >= 3 && args[1].equalsIgnoreCase("end")) {
      if (!sender.hasPermission("dungeonhero.admin.arena")) {
        sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
        return true;
      }
      org.bukkit.entity.Player target = plugin.getServer().getPlayer(args[2]);
      sender.sendMessage(
          Component.text(
              target != null && arenaSessionManager.endForPlayer(target.getUniqueId())
                  ? "Blood Arena ended."
                  : "That player is not in a Blood Arena.",
              target != null ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
      return true;
    }
    sender.sendMessage(
        Component.text("Usage: /dh arena escape|end <player>", NamedTextColor.YELLOW));
    return true;
  }

  @Override
  public java.util.List<String> onTabComplete(
      CommandSender sender, Command command, String alias, String[] args) {
    if (!command.getName().equalsIgnoreCase("dungeonhero")) return java.util.List.of();
    return completionService.complete(sender, args);
  }
}
