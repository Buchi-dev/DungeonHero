package com.dungeonhero.integration.mythicmobs;

import com.dungeonhero.feature.party.PartyService;
import com.dungeonhero.feature.rank.DungeonRankService;
import com.dungeonhero.feature.sword.HeroItemService;
import com.dungeonhero.feature.mobregistry.MobRegistryService;

import io.lumine.mythic.bukkit.events.MythicMobPreSpawnEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class HeroSwordMobScaler implements Listener {

    private final JavaPlugin plugin;
    private final HeroItemService heroItemService;
    private final PartyService partyService;
    private final DungeonRankService dungeonRankService;
    private final MobRegistryService mobRegistryService;

    private boolean enabled;
    private Set<String> worlds;
    private double searchRadius;
    private PartyMode partyMode;
    private int maxPlayers;
    private int baseLevel;
    private double swordLevelsPerMobLevel;
    private double rankPowerBonus;
    private double damageBonusWeight;
    private double prestigeLevelBonus;
    private int maxLevel;
    private boolean debug;

    public HeroSwordMobScaler(JavaPlugin plugin, HeroItemService heroItemService, PartyService partyService,
                              DungeonRankService dungeonRankService, MobRegistryService mobRegistryService) {
        this.plugin = plugin;
        this.heroItemService = heroItemService;
        this.partyService = partyService;
        this.dungeonRankService = dungeonRankService;
        this.mobRegistryService = mobRegistryService;
        reload();
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("DungeonHero.MobScaling.Enabled", true);
        worlds = plugin.getConfig().getStringList("DungeonHero.MobScaling.Worlds").stream()
                .map(String::trim)
                .filter(world -> !world.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        searchRadius = Math.max(1, plugin.getConfig().getDouble("DungeonHero.MobScaling.SearchRadius", 32));
        partyMode = PartyMode.from(plugin.getConfig().getString("DungeonHero.MobScaling.PartyMode", "AVERAGE"));
        maxPlayers = Math.max(1, plugin.getConfig().getInt("DungeonHero.MobScaling.MaxPlayers", 4));
        baseLevel = Math.max(1, plugin.getConfig().getInt("DungeonHero.MobScaling.BaseLevel", 1));
        swordLevelsPerMobLevel = Math.max(0.01,
                plugin.getConfig().getDouble("DungeonHero.MobScaling.SwordLevelsPerMobLevel", 3.25));
        rankPowerBonus = Math.max(0, plugin.getConfig().getDouble(
                "DungeonHero.MobScaling.RankPowerBonus", 0.75));
        damageBonusWeight = Math.max(0, plugin.getConfig().getDouble(
                "DungeonHero.MobScaling.DamageBonusWeight", 0.12));
        prestigeLevelBonus = Math.max(0, plugin.getConfig().getDouble(
                "DungeonHero.MobScaling.PrestigeLevelBonus", 0));
        maxLevel = Math.max(baseLevel,
                plugin.getConfig().getInt("DungeonHero.MobScaling.MaxLevel", 50));
        debug = plugin.getConfig().getBoolean("DungeonHero.MobScaling.Debug", false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMythicMobPreSpawn(MythicMobPreSpawnEvent event) {
        if (!enabled) {
            return;
        }
        World world = event.getLocation().getWorld();
        if (world == null || (!worlds.isEmpty() && !worlds.contains(world.getName()))) {
            return;
        }

        List<CombatPower> nearbyPlayers = findNearbyPlayers(event.getLocation());
        if (nearbyPlayers.isEmpty()) {
            return;
        }

        double combatPower = switch (partyMode) {
            case NEAREST -> nearbyPlayers.getFirst().power();
            case MAX -> nearbyPlayers.stream().mapToDouble(CombatPower::power).max().orElse(1);
            case AVERAGE -> nearbyPlayers.stream().mapToDouble(CombatPower::power).average().orElse(1);
        };
        String mobId = event.getMobType() == null ? "" : event.getMobType().getInternalName();
        int level = baseLevel + (int) Math.floor(Math.max(0, combatPower - 1) / swordLevelsPerMobLevel)
                + levelOffset(mobId);
        int boundedLevel = Math.max(baseLevel, Math.min(maxLevel, level));
        event.setMobLevel(boundedLevel);
        if (debug) {
            plugin.getLogger().info("Scaled " + mobId + " for " + nearbyPlayers.size()
                    + " player(s): power=" + String.format(Locale.ROOT, "%.2f", combatPower)
                    + ", profile=" + mobRegistryService.profileOrDefault(mobId).name()
                    + ", level=" + boundedLevel);
        }
    }

    private List<CombatPower> findNearbyPlayers(Location location) {
        double maxDistanceSquared = searchRadius * searchRadius;
        List<CombatPower> players = new ArrayList<>();
        for (Player player : partyService.findScalingPlayers(location, searchRadius, heroItemService)) {
            double distanceSquared = player.getLocation().distanceSquared(location);
            ItemStack sword = heroItemService.findStrongestHeroSword(player);
            if (sword != null) {
                players.add(new CombatPower(player, combatPower(player, sword), distanceSquared));
            }
        }
        players.sort(Comparator.comparingDouble(CombatPower::distanceSquared));
        return players.stream().limit(maxPlayers).toList();
    }

    private double combatPower(Player player, ItemStack sword) {
        return heroItemService.getSwordLevel(sword)
                + (Math.max(0, dungeonRankService.getRank(player) - 1) * rankPowerBonus)
                + (heroItemService.getDamageBonus(sword) * damageBonusWeight)
                + (heroItemService.getSwordPrestige(sword) * prestigeLevelBonus);
    }

    private int levelOffset(String internalName) {
        return mobRegistryService.profileOrDefault(internalName).levelOffset();
    }

    private record CombatPower(Player player, double power, double distanceSquared) {
    }

    private enum PartyMode {
        NEAREST,
        AVERAGE,
        MAX;

        private static PartyMode from(String value) {
            try {
                return value == null ? AVERAGE : valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return AVERAGE;
            }
        }
    }
}
