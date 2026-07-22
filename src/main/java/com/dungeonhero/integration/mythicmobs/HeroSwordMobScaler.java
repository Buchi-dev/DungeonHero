package com.dungeonhero.integration.mythicmobs;

import com.dungeonhero.feature.mobregistry.MobRegistryService;
import com.dungeonhero.feature.party.PartyService;
import com.dungeonhero.feature.sword.HeroItemService;
import io.lumine.mythic.bukkit.events.MythicMobPreSpawnEvent;
import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Selects a mob level once at spawn and applies the bounded HP rebalance once at creation. */
public final class HeroSwordMobScaler implements Listener {

    private final JavaPlugin plugin;
    private final HeroItemService heroItemService;
    private final PartyService partyService;
    private final MobRegistryService mobRegistryService;
    private final NamespacedKey mobLevelKey;
    private final NamespacedKey mobHpKey;
    private boolean enabled;
    private Set<String> worlds;
    private double searchRadius;
    private int maxPlayers;
    private boolean debug;
    private double amplifierPerDamage;
    private double potionAmplifierCompensation;
    private Set<String> approvedPotionEffects;
    private MobCombatBalance balance;

    public HeroSwordMobScaler(JavaPlugin plugin, HeroItemService heroItemService, PartyService partyService,
                              com.dungeonhero.feature.rank.DungeonRankService ignoredRankService,
                              MobRegistryService mobRegistryService) {
        this.plugin = plugin;
        this.heroItemService = heroItemService;
        this.partyService = partyService;
        this.mobRegistryService = mobRegistryService;
        this.mobLevelKey = new NamespacedKey(plugin, "mob_level");
        this.mobHpKey = new NamespacedKey(plugin, "mob_hp");
        reload();
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("DungeonHero.MobScaling.Enabled", true);
        worlds = plugin.getConfig().getStringList("DungeonHero.MobScaling.Worlds").stream()
                .map(String::trim).filter(world -> !world.isEmpty()).collect(Collectors.toUnmodifiableSet());
        searchRadius = Math.max(1, plugin.getConfig().getDouble("DungeonHero.MobScaling.SearchRadius", 32));
        maxPlayers = Math.max(1, plugin.getConfig().getInt("DungeonHero.MobScaling.MaxPlayers", 5));
        debug = plugin.getConfig().getBoolean("DungeonHero.MobScaling.Debug", false);
        amplifierPerDamage = Math.max(0, plugin.getConfig().getDouble(
                "DungeonHero.MobHp.DamageAmplifierCompensationPerDamage", 0.005));
        potionAmplifierCompensation = Math.max(0, plugin.getConfig().getDouble(
                "DungeonHero.DamageAmplifiers.PotionCompensationPerLevel", 0.02));
        approvedPotionEffects = plugin.getConfig().getStringList("DungeonHero.DamageAmplifiers.ApprovedPotionEffects")
                .stream().map(value -> value.trim().toLowerCase(Locale.ROOT)).filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        balance = new MobCombatBalance(
                plugin.getConfig().getInt("DungeonHero.MobScaling.MaximumMobLevel",
                        plugin.getConfig().getInt("DungeonHero.MobScaling.MaxLevel", 104)),
                plugin.getConfig().getInt("DungeonHero.MobScaling.LevelOffsets.Normal.Min", -2),
                plugin.getConfig().getInt("DungeonHero.MobScaling.LevelOffsets.Normal.Max", 4),
                plugin.getConfig().getInt("DungeonHero.MobScaling.LevelOffsets.Elite.Min", 0),
                plugin.getConfig().getInt("DungeonHero.MobScaling.LevelOffsets.Elite.Max", 4),
                plugin.getConfig().getInt("DungeonHero.MobScaling.LevelOffsets.Miniboss.Min", 1),
                plugin.getConfig().getInt("DungeonHero.MobScaling.LevelOffsets.Miniboss.Max", 4),
                plugin.getConfig().getInt("DungeonHero.MobScaling.LevelOffsets.RareBoss.Min", 2),
                plugin.getConfig().getInt("DungeonHero.MobScaling.LevelOffsets.RareBoss.Max", 4),
                plugin.getConfig().getDouble("DungeonHero.MobHp.NormalBase", 400),
                plugin.getConfig().getDouble("DungeonHero.MobHp.NormalHpPerLevel", 40),
                plugin.getConfig().getDouble("DungeonHero.MobHp.ProfileMultipliers.Normal", 1),
                plugin.getConfig().getDouble("DungeonHero.MobHp.ProfileMultipliers.Elite", 3),
                plugin.getConfig().getDouble("DungeonHero.MobHp.ProfileMultipliers.Miniboss", 8),
                plugin.getConfig().getDouble("DungeonHero.MobHp.ProfileMultipliers.RareBoss", 18),
                plugin.getConfig().getDouble("DungeonHero.MobHp.MinimumAttacks.Normal", 6),
                plugin.getConfig().getDouble("DungeonHero.MobHp.MinimumAttacks.Elite", 12),
                plugin.getConfig().getDouble("DungeonHero.MobHp.MinimumAttacks.Miniboss", 25),
                plugin.getConfig().getDouble("DungeonHero.MobHp.MinimumAttacks.RareBoss", 50),
                plugin.getConfig().getDouble("DungeonHero.MobHp.MaximumAmplifierCompensation", 0.5));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMythicMobPreSpawn(MythicMobPreSpawnEvent event) {
        if (!enabled || !isWorldAllowed(event.getLocation())) {
            return;
        }
        List<CombatPower> nearbyPlayers = findNearbyPlayers(event.getLocation());
        if (nearbyPlayers.isEmpty()) {
            return;
        }
        String mobId = event.getMobType() == null ? "" : event.getMobType().getInternalName();
        MobRegistryService.MobProfile profile = mobRegistryService.profileOrDefault(mobId);
        int effectiveCombatLevel = nearbyPlayers.stream().mapToInt(CombatPower::effectiveLevel).max().orElse(1);
        int selectedLevel = balance.selectMobLevel(effectiveCombatLevel, 0, profile.kind());
        event.setMobLevel(selectedLevel);
        if (debug) {
            plugin.getLogger().info("Scaled " + mobId + " using highest active sword level: effective="
                    + effectiveCombatLevel + ", profile=" + profile.name() + ", level=" + selectedLevel);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMythicMobSpawn(MythicMobSpawnEvent event) {
        if (!enabled || !isWorldAllowed(event.getLocation())) {
            return;
        }
        LivingEntity entity = event.getLivingEntity();
        if (entity == null) {
            return;
        }
        String mobId = event.getMobType() == null ? "" : event.getMobType().getInternalName();
        MobRegistryService.MobProfile profile = mobRegistryService.profileOrDefault(mobId);
        int level = balance.clampLevel((int) Math.round(event.getMobLevel()));
        entity.getPersistentDataContainer().set(mobLevelKey, PersistentDataType.INTEGER, level);
        List<CombatPower> players = findNearbyPlayers(event.getLocation());
        double strongestHit = players.stream().mapToDouble(this::serverNormalHitDamage).max().orElse(1);
        double strongestAmplifier = players.stream().mapToDouble(this::amplifierCompensation).max().orElse(0);
        double hp = balance.finalHp(level, profile.kind(), strongestHit, strongestAmplifier);

        var maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(hp);
            entity.setHealth(Math.min(hp, maxHealth.getValue()));
        } else {
            entity.setHealth(Math.min(hp, entity.getHealth()));
        }
        entity.getPersistentDataContainer().set(mobHpKey, PersistentDataType.DOUBLE, hp);
        if (debug) {
            plugin.getLogger().info("Applied HP to " + mobId + ": level=" + level + ", hp=" + hp);
        }
    }

    public MobCombatBalance balance() {
        return balance;
    }

    private boolean isWorldAllowed(Location location) {
        World world = location == null ? null : location.getWorld();
        return world != null && (worlds.isEmpty() || worlds.contains(world.getName()));
    }

    private List<CombatPower> findNearbyPlayers(Location location) {
        return partyService.findScalingPlayers(location, searchRadius, heroItemService).stream()
                .map(player -> {
                    ItemStack sword = heroItemService.findStrongestHeroSword(player);
                    return sword == null ? null : new CombatPower(player,
                            balance.effectiveCombatLevel(heroItemService.getSwordLevel(sword),
                                    heroItemService.getSwordPrestige(sword)),
                            player.getLocation().distanceSquared(location));
                })
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt(CombatPower::effectiveLevel).reversed()
                        .thenComparingDouble(CombatPower::distanceSquared))
                .limit(maxPlayers)
                .toList();
    }

    private double serverNormalHitDamage(CombatPower combatPower) {
        var attribute = combatPower.player().getAttribute(Attribute.ATTACK_DAMAGE);
        double attributeDamage = attribute == null ? 1 : attribute.getValue();
        // Paper's server-side attribute already includes the sanitized main-hand
        // Hero Sword modifier; do not add lore/PDC damage a second time.
        return Math.max(1, Math.min(100000, attributeDamage));
    }

    private double amplifierCompensation(CombatPower combatPower) {
        ItemStack sword = heroItemService.findStrongestHeroSword(combatPower.player());
        double damageBonus = sword == null ? 0 : heroItemService.getEffectiveDamageBonus(sword, 10);
        double requested = damageBonus * amplifierPerDamage;
        for (var effect : combatPower.player().getActivePotionEffects()) {
            String name = effect.getType().getName().toLowerCase(Locale.ROOT);
            if (approvedPotionEffects.contains(name)) {
                requested += (effect.getAmplifier() + 1) * potionAmplifierCompensation;
            }
        }
        return balance.boundedAmplifierCompensation(requested);
    }

    private record CombatPower(Player player, int effectiveLevel, double distanceSquared) {
    }
}
