package com.dungeonhero;

import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent;
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

public final class HeroSwordMobScaler implements Listener {

    private final JavaPlugin plugin;
    private final HeroItemService heroItemService;
    private final PartyService partyService;

    private boolean enabled;
    private double searchRadius;
    private PartyMode partyMode;
    private int maxPlayers;
    private int baseLevel;
    private double swordLevelsPerMobLevel;
    private double damageBonusWeight;
    private double prestigeLevelBonus;
    private int maxLevel;

    public HeroSwordMobScaler(JavaPlugin plugin, HeroItemService heroItemService, PartyService partyService) {
        this.plugin = plugin;
        this.heroItemService = heroItemService;
        this.partyService = partyService;
        reload();
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("DungeonHero.MobScaling.Enabled", true);
        searchRadius = Math.max(1, plugin.getConfig().getDouble("DungeonHero.MobScaling.SearchRadius", 32));
        partyMode = PartyMode.from(plugin.getConfig().getString("DungeonHero.MobScaling.PartyMode", "AVERAGE"));
        maxPlayers = Math.max(1, plugin.getConfig().getInt("DungeonHero.MobScaling.MaxPlayers", 4));
        baseLevel = Math.max(1, plugin.getConfig().getInt("DungeonHero.MobScaling.BaseLevel", 1));
        swordLevelsPerMobLevel = Math.max(0.01,
                plugin.getConfig().getDouble("DungeonHero.MobScaling.SwordLevelsPerMobLevel", 2));
        damageBonusWeight = Math.max(0, plugin.getConfig().getDouble(
                "DungeonHero.MobScaling.DamageBonusWeight", 0.5));
        prestigeLevelBonus = Math.max(0, plugin.getConfig().getDouble(
                "DungeonHero.MobScaling.PrestigeLevelBonus", 5));
        maxLevel = Math.max(baseLevel,
                plugin.getConfig().getInt("DungeonHero.MobScaling.MaxLevel", 100));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMythicMobSpawn(MythicMobSpawnEvent event) {
        if (!enabled) {
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
        int level = baseLevel + (int) Math.floor(Math.max(0, combatPower - 1) / swordLevelsPerMobLevel);
        event.setMobLevel(Math.max(baseLevel, Math.min(maxLevel, level)));
    }

    private List<CombatPower> findNearbyPlayers(Location location) {
        double maxDistanceSquared = searchRadius * searchRadius;
        List<CombatPower> players = new ArrayList<>();
        for (Player player : partyService.findScalingPlayers(location, searchRadius, heroItemService)) {
            double distanceSquared = player.getLocation().distanceSquared(location);
            ItemStack sword = heroItemService.findStrongestHeroSword(player);
            if (sword != null) {
                players.add(new CombatPower(player, combatPower(sword), distanceSquared));
            }
        }
        players.sort(Comparator.comparingDouble(CombatPower::distanceSquared));
        return players.stream().limit(maxPlayers).toList();
    }

    private double combatPower(ItemStack sword) {
        return heroItemService.getSwordLevel(sword)
                + (heroItemService.getDamageBonus(sword) * damageBonusWeight)
                + (heroItemService.getSwordPrestige(sword) * prestigeLevelBonus);
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
