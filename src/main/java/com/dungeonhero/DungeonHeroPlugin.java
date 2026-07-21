package com.dungeonhero;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class DungeonHeroPlugin extends JavaPlugin implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of(
            "help", "reload", "menu", "loadout", "vault", "forge", "give", "sword", "rank", "rankup", "party", "prestige", "dummy", "version"
    );
    private static final List<String> PARTY_SUBCOMMANDS = List.of(
            "create", "invite", "accept", "info", "leave", "kick", "disband", "help"
    );

    private HeroItemService heroItemService;
    private HeroSwordStorage heroSwordStorage;
    private HeroPlayerListener heroPlayerListener;
    private DungeonInventoryService dungeonInventoryService;
    private MythicFragmentService mythicFragmentService;
    private HeroSwordMobScaler heroSwordMobScaler;
    private SwordProgressionService swordProgressionService;
    private SwordHudService swordHudService;
    private DungeonRankService dungeonRankService;
    private PartyService partyService;
    private TrainingDummyService trainingDummyService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        heroItemService = new HeroItemService(this);
        heroSwordStorage = new HeroSwordStorage(this, heroItemService);
        mythicFragmentService = new MythicFragmentService(this);
        dungeonInventoryService = new DungeonInventoryService(this, heroItemService,
                heroSwordStorage, mythicFragmentService);
        dungeonRankService = new DungeonRankService(this, heroItemService);
        partyService = new PartyService(this);
        trainingDummyService = new TrainingDummyService(this, heroItemService);
        swordProgressionService = new SwordProgressionService(this, heroItemService, mythicFragmentService,
                dungeonRankService, heroSwordStorage);
        heroSwordMobScaler = new HeroSwordMobScaler(this, heroItemService, partyService);
        swordHudService = new SwordHudService(this, heroItemService, swordProgressionService);
        heroPlayerListener = new HeroPlayerListener(this, heroItemService, heroSwordStorage);
        getServer().getPluginManager().registerEvents(heroPlayerListener, this);
        getServer().getPluginManager().registerEvents(dungeonInventoryService, this);
        getServer().getPluginManager().registerEvents(
                new ForgeMenu.Listener(this), this);
        getServer().getPluginManager().registerEvents(swordProgressionService, this);
        getServer().getPluginManager().registerEvents(heroSwordMobScaler, this);
        getServer().getPluginManager().registerEvents(swordHudService, this);
        getServer().getPluginManager().registerEvents(partyService, this);
        getServer().getPluginManager().registerEvents(trainingDummyService, this);
        long hudUpdateTicks = Math.max(1, getConfig().getLong("DungeonHero.Hud.UpdateTicks", 10));
        getServer().getScheduler().runTaskTimer(this, swordHudService::syncOnlinePlayers,
                hudUpdateTicks, hudUpdateTicks);
        getServer().getScheduler().runTask(this, dungeonInventoryService::syncOnlinePlayers);
        if (getCommand("dungeonhero") != null) {
            getCommand("dungeonhero").setExecutor(this);
            getCommand("dungeonhero").setTabCompleter(this);
        }

        getLogger().info("DungeonHero enabled. Hero Sword, Forge, party, rank, and MythicMobs systems are ready.");
    }

    @Override
    public void onDisable() {
        if (dungeonInventoryService != null) {
            dungeonInventoryService.saveOnlineDungeonPlayers();
        }
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
            if (!requireAdmin(sender)) {
                return true;
            }
            reloadConfig();
            mythicFragmentService.reload();
            dungeonRankService.reload();
            partyService.reload();
            swordProgressionService.reload();
            heroSwordMobScaler.reload();
            swordHudService.reload();
            trainingDummyService.reload();
            dungeonInventoryService.reload();
            dungeonInventoryService.syncOnlinePlayers();
            sender.sendMessage(Component.text("DungeonHero configuration reloaded. Use /mm reload for MythicMobs changes.",
                    NamedTextColor.GREEN));
            return true;
        }

        if (args[0].equalsIgnoreCase("version")) {
            sender.sendMessage(Component.text("DungeonHero v" + getDescription().getVersion(), NamedTextColor.GOLD));
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

        if (args[0].equalsIgnoreCase("loadout")) {
            if (sender instanceof Player player) {
                dungeonInventoryService.openLoadout(player);
            } else {
                sender.sendMessage(Component.text("Only players can open the Supply Loadout.", NamedTextColor.RED));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("vault")) {
            if (sender instanceof Player player) {
                dungeonInventoryService.openFragmentVault(player);
            } else {
                sender.sendMessage(Component.text("Only players can open the Fragment Vault.", NamedTextColor.RED));
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

    private void giveItem(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
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
            sender.sendMessage(Component.text("MythicMobs item IDs must use the mm:<item-id> format.", NamedTextColor.YELLOW));
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
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use Training Dummies.", NamedTextColor.RED));
            return;
        }

        String action = args.length >= 2 ? args[1].toLowerCase(java.util.Locale.ROOT) : "spawn";
        switch (action) {
            case "spawn", "open" -> trainingDummyService.open(player);
            case "stats" -> trainingDummyService.sendStats(player);
            case "remove" -> {
                if (requireAdmin(sender)) {
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
                        + " and has reset to Level 1 (Wood tier).",
                NamedTextColor.LIGHT_PURPLE));
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

        String action = args[1].toLowerCase(java.util.Locale.ROOT);
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

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("dungeonhero.admin")) {
            return true;
        }
        sender.sendMessage(Component.text("You do not have permission to use that command.", NamedTextColor.RED));
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
            return complete(args[1], List.of("spawn", "stats", "remove"));
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("give") && sender.hasPermission("dungeonhero.admin")) {
            return complete(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give") && sender.hasPermission("dungeonhero.admin")) {
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
