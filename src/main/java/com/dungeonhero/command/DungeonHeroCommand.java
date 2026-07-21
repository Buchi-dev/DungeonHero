package com.dungeonhero.command;

import com.dungeonhero.feature.dungeoninventory.DungeonInventoryService;
import com.dungeonhero.feature.forge.ForgeMenu;
import com.dungeonhero.feature.party.PartyService;
import com.dungeonhero.feature.rank.DungeonRankService;
import com.dungeonhero.feature.sword.HeroItemService;
import com.dungeonhero.feature.sword.HeroPlayerListener;
import com.dungeonhero.feature.sword.HeroSwordStorage;
import com.dungeonhero.feature.sword.SwordHudService;
import com.dungeonhero.feature.sword.SwordProgressionService;
import com.dungeonhero.feature.trainingdummy.TrainingDummyService;
import com.dungeonhero.integration.mythicmobs.HeroSwordMobScaler;
import com.dungeonhero.integration.mythicmobs.MythicFragmentService;
import com.dungeonhero.messaging.DungeonHeroMessages;
import com.dungeonhero.messaging.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

/** Routes player and administrator commands without coupling command logic to plugin bootstrap. */
public final class DungeonHeroCommand implements TabExecutor {

    private static final String ADMIN_PERMISSION = "dungeonhero.admin";
    private static final String RELOAD_PERMISSION = "dungeonhero.admin.reload";
    private static final String GIVE_PERMISSION = "dungeonhero.admin.give";
    private static final String DUMMY_REMOVE_PERMISSION = "dungeonhero.admin.dummy.remove";
    private static final String DUMMY_REMOVE_ALL_PERMISSION = "dungeonhero.admin.dummy.remove-all";
    private static final List<String> SUBCOMMANDS = List.of(
            "help", "reload", "menu", "forge", "give", "sword", "rank", "rankup", "party", "prestige", "dummy", "version"
    );
    private static final List<String> PARTY_SUBCOMMANDS = List.of(
            "create", "invite", "accept", "info", "leave", "kick", "disband", "help"
    );

    private final JavaPlugin plugin;
    private final HeroItemService heroItemService;
    private final HeroSwordStorage heroSwordStorage;
    private final HeroPlayerListener heroPlayerListener;
    private final DungeonInventoryService dungeonInventoryService;
    private final MythicFragmentService mythicFragmentService;
    private final HeroSwordMobScaler heroSwordMobScaler;
    private final SwordHudService swordHudService;
    private final SwordProgressionService swordProgressionService;
    private final DungeonRankService dungeonRankService;
    private final PartyService partyService;
    private final TrainingDummyService trainingDummyService;
    private final MessageService messageService;

    public DungeonHeroCommand(JavaPlugin plugin,
                              HeroItemService heroItemService,
                              HeroSwordStorage heroSwordStorage,
                              HeroPlayerListener heroPlayerListener,
                              DungeonInventoryService dungeonInventoryService,
                              MythicFragmentService mythicFragmentService,
                              HeroSwordMobScaler heroSwordMobScaler,
                              SwordHudService swordHudService,
                              SwordProgressionService swordProgressionService,
                              DungeonRankService dungeonRankService,
                              PartyService partyService,
                              TrainingDummyService trainingDummyService,
                              MessageService messageService) {
        this.plugin = plugin;
        this.heroItemService = heroItemService;
        this.heroSwordStorage = heroSwordStorage;
        this.heroPlayerListener = heroPlayerListener;
        this.dungeonInventoryService = dungeonInventoryService;
        this.mythicFragmentService = mythicFragmentService;
        this.heroSwordMobScaler = heroSwordMobScaler;
        this.swordHudService = swordHudService;
        this.swordProgressionService = swordProgressionService;
        this.dungeonRankService = dungeonRankService;
        this.partyService = partyService;
        this.trainingDummyService = trainingDummyService;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("dungeonhero")) {
            return false;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            DungeonHeroMessages.sendHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!requireAdmin(sender, RELOAD_PERMISSION)) {
                return true;
            }
            reloadServices(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("version")) {
            sender.sendMessage(Component.text("DungeonHero v" + plugin.getDescription().getVersion(), NamedTextColor.GOLD));
            return true;
        }

        if (args[0].equalsIgnoreCase("menu")) {
            if (sender instanceof Player player) {
                dungeonInventoryService.openMenu(player);
            } else {
                sender.sendMessage(Component.text("Only players can open the Dungeon Menu.", NamedTextColor.RED));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("sword")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can view Sword XP.", NamedTextColor.RED));
                return true;
            }
            DungeonHeroMessages.sendSwordStatus(sender, player,
                    heroPlayerListener.restoreSavedSword(player), heroItemService, swordProgressionService);
            return true;
        }

        if (args[0].equalsIgnoreCase("rank")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can view Dungeon Rank.", NamedTextColor.RED));
                return true;
            }
            DungeonHeroMessages.sendRankStatus(sender, player, dungeonRankService, heroItemService);
            return true;
        }

        if (args[0].equalsIgnoreCase("rankup")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can rank up.", NamedTextColor.RED));
                return true;
            }
            DungeonHeroMessages.sendRankUpResult(player, dungeonRankService.rankUp(player), dungeonRankService);
            return true;
        }

        if (args[0].equalsIgnoreCase("party")) {
            handleParty(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("prestige")) {
            prestige(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("forge")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can use the Hero Forge.", NamedTextColor.RED));
                return true;
            }
            ForgeMenu.open(player, heroItemService, mythicFragmentService, heroSwordStorage,
                    dungeonInventoryService);
            return true;
        }

        if (args[0].equalsIgnoreCase("dummy")) {
            handleDummy(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            giveItem(sender, args);
            return true;
        }

        sender.sendMessage(Component.text("Unknown DungeonHero command. Use /dh help.", NamedTextColor.RED));
        return true;
    }

    private void reloadServices(CommandSender sender) {
        plugin.reloadConfig();
        messageService.reload();
        mythicFragmentService.reload();
        dungeonRankService.reload();
        partyService.reload();
        swordProgressionService.reload();
        heroSwordMobScaler.reload();
        swordHudService.reload();
        trainingDummyService.reload();
        dungeonInventoryService.reload();
        dungeonInventoryService.syncOnlinePlayers();
        sender.sendMessage(messageService.text("command.reload_complete",
                "DungeonHero configuration reloaded. Use /mm reload for MythicMobs changes.")
                .color(NamedTextColor.GREEN));
    }

    private void giveItem(CommandSender sender, String[] args) {
        if (!requireAdmin(sender, GIVE_PERMISSION)) {
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /dh give <player> mm:<item-id>", NamedTextColor.YELLOW));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("That player is not online.", NamedTextColor.RED));
            return;
        }
        if (!args[2].regionMatches(true, 0, "mm:", 0, 3)) {
            sender.sendMessage(Component.text(
                    "MythicMobs item IDs must use the mm:<item-id> format.", NamedTextColor.YELLOW));
            return;
        }

        var item = mythicFragmentService.createItem(args[2]);
        if (item.isEmpty()) {
            sender.sendMessage(Component.text("MythicMobs item not found: " + args[2], NamedTextColor.RED));
            return;
        }
        var inspection = mythicFragmentService.inspect(item.get());
        if (!inspection.isValid()) {
            sender.sendMessage(Component.text(inspection.error(), NamedTextColor.RED));
            return;
        }

        heroItemService.giveOrDrop(target, item.get());
        sender.sendMessage(Component.text("Gave " + args[2] + " to " + target.getName() + ".", NamedTextColor.GREEN));
    }

    private void handleDummy(CommandSender sender, String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "spawn";
        if (action.equals("remove-all")) {
            if (requireAdmin(sender, DUMMY_REMOVE_ALL_PERMISSION)) {
                int removed = trainingDummyService.removeAll();
                sender.sendMessage(messageService.text("command.dummy_remove_all",
                        "Removed {count} DungeonHero Training Dummy target(s) from loaded worlds.")
                        .replaceText(builder -> builder.matchLiteral("{count}").replacement(String.valueOf(removed)))
                        .color(NamedTextColor.GREEN));
            }
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use Training Dummies.", NamedTextColor.RED));
            return;
        }

        switch (action) {
            case "spawn", "open" -> trainingDummyService.open(player);
            case "stats" -> trainingDummyService.sendStats(player);
            case "remove" -> {
                if (requireAdmin(sender, DUMMY_REMOVE_PERMISSION)) {
                    trainingDummyService.remove(player);
                }
            }
            default -> player.sendMessage(Component.text(
                    "Usage: /dh dummy [spawn|stats|remove]", NamedTextColor.YELLOW));
        }
    }

    private void prestige(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can prestige a Hero Sword.", NamedTextColor.RED));
            return;
        }

        int swordSlot = swordProgressionService.findStrongestSwordSlot(player.getInventory());
        if (swordSlot < 0) {
            player.sendMessage(Component.text("You do not have a Hero Sword.", NamedTextColor.RED));
            return;
        }

        var sword = player.getInventory().getItem(swordSlot);
        if (heroItemService.getSwordLevel(sword) < swordProgressionService.getMaxSwordLevel()) {
            player.sendMessage(Component.text(
                    "Reach Sword Level " + swordProgressionService.getMaxSwordLevel() + " before prestiging.",
                    NamedTextColor.YELLOW));
            return;
        }

        var prestigedSword = heroItemService.withPrestige(sword);
        player.getInventory().setItem(swordSlot, prestigedSword);
        heroSwordStorage.save(player, prestigedSword);
        player.sendMessage(Component.text(
                "Your Hero Sword is now Prestige " + heroItemService.getSwordPrestige(prestigedSword)
                        + " and has reset to Level 1 (Wood tier).", NamedTextColor.LIGHT_PURPLE));
    }

    private void handleParty(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use party commands.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("help")) {
            DungeonHeroMessages.sendPartyHelp(player);
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "create" -> DungeonHeroMessages.sendPartyResult(player, partyService.create(player),
                    "Party created. Invite your friends!");
            case "invite" -> {
                Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : null;
                var result = partyService.invite(player, target);
                DungeonHeroMessages.sendPartyResult(player, result,
                        target == null ? "Invitation sent." : "Invitation sent to " + target.getName() + ".");
                if (result.status() == PartyService.ActionStatus.SUCCESS && result.target() != null) {
                    DungeonHeroMessages.sendPartyInvite(result.target(), result.inviterName(), partyService.getMaxSize());
                }
            }
            case "accept" -> {
                var result = partyService.accept(player);
                DungeonHeroMessages.sendPartyResult(player, result, "You joined the party.");
                if (result.status() == PartyService.ActionStatus.SUCCESS) {
                    partyService.broadcast(result.party(), DungeonHeroMessages.partyBroadcast(
                            player.getName() + " joined the party."));
                }
            }
            case "info" -> DungeonHeroMessages.sendPartyInfo(player, partyService, heroItemService);
            case "leave" -> {
                String name = player.getName();
                var result = partyService.leave(player);
                DungeonHeroMessages.sendPartyResult(player, result, "You left the party.");
                if (result.status() == PartyService.ActionStatus.SUCCESS) {
                    partyService.broadcast(result.party(), DungeonHeroMessages.partyBroadcast(name + " left the party."));
                }
            }
            case "kick" -> {
                Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : null;
                var result = partyService.kick(player, target);
                DungeonHeroMessages.sendPartyResult(player, result,
                        target == null ? "Player removed." : target.getName() + " was removed from the party.");
                if (result.status() == PartyService.ActionStatus.SUCCESS && result.target() != null) {
                    result.target().sendMessage(DungeonHeroMessages.partyBroadcast("You were removed from the party."));
                    partyService.broadcast(result.party(), DungeonHeroMessages.partyBroadcast(
                            result.target().getName() + " was removed from the party."));
                }
            }
            case "disband" -> {
                var result = partyService.disband(player);
                DungeonHeroMessages.sendPartyResult(player, result, "Party disbanded.");
                if (result.status() == PartyService.ActionStatus.SUCCESS) {
                    partyService.broadcast(result.party(), DungeonHeroMessages.partyBroadcast("The party was disbanded."));
                }
            }
            default -> DungeonHeroMessages.sendPartyHelp(player);
        }
    }

    private boolean requireAdmin(CommandSender sender, String permission) {
        if (sender.hasPermission(ADMIN_PERMISSION) || sender.hasPermission(permission)) {
            return true;
        }
        sender.sendMessage(messageService.text("command.no_permission",
                "You do not have permission to use that command.").color(NamedTextColor.RED));
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("dungeonhero")) {
            return List.of();
        }

        if (args.length == 1) {
            return complete(args[0], SUBCOMMANDS);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("party")) {
            return complete(args[1], PARTY_SUBCOMMANDS);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("dummy")) {
            return complete(args[1], List.of("spawn", "stats", "remove", "remove-all"));
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("give")
                && (sender.hasPermission(ADMIN_PERMISSION) || sender.hasPermission(GIVE_PERMISSION))) {
            return complete(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give")
                && (sender.hasPermission(ADMIN_PERMISSION) || sender.hasPermission(GIVE_PERMISSION))) {
            return complete(args[2], mythicFragmentService.getFragmentIds());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("party")
                && (args[1].equalsIgnoreCase("invite") || args[1].equalsIgnoreCase("kick"))) {
            return complete(args[2], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }

        return List.of();
    }

    private List<String> complete(String input, List<String> options) {
        return options.stream()
                .filter(option -> option.regionMatches(true, 0, input, 0, input.length()))
                .sorted()
                .toList();
    }
}
