package com.dungeonhero.integration.mythicmobs;

import com.dungeonhero.config.DungeonHeroConfiguration;
import com.dungeonhero.feature.mobregistry.MobRegistryService;
import com.dungeonhero.feature.party.PartyService;
import com.dungeonhero.feature.sword.HeroItemService;
import io.lumine.mythic.bukkit.events.MythicMobPreSpawnEvent;
import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

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
  private MobScalingPolicy scalingPolicy;

  public HeroSwordMobScaler(
      JavaPlugin plugin,
      HeroItemService heroItemService,
      PartyService partyService,
      com.dungeonhero.feature.rank.DungeonRankService ignoredRankService,
      MobRegistryService mobRegistryService) {
    this(
        plugin,
        heroItemService,
        partyService,
        ignoredRankService,
        mobRegistryService,
        DungeonHeroConfiguration.load(plugin).mobScaling());
  }

  public HeroSwordMobScaler(
      JavaPlugin plugin,
      HeroItemService heroItemService,
      PartyService partyService,
      com.dungeonhero.feature.rank.DungeonRankService ignoredRankService,
      MobRegistryService mobRegistryService,
      DungeonHeroConfiguration.MobScaling configuration) {
    this.plugin = plugin;
    this.heroItemService = heroItemService;
    this.partyService = partyService;
    this.mobRegistryService = mobRegistryService;
    this.mobLevelKey = new NamespacedKey(plugin, "mob_level");
    this.mobHpKey = new NamespacedKey(plugin, "mob_hp");
    reload(configuration);
  }

  public void reload() {
    reload(DungeonHeroConfiguration.load(plugin).mobScaling());
  }

  public void reload(DungeonHeroConfiguration.MobScaling configuration) {
    enabled = configuration.enabled();
    worlds = configuration.worlds();
    searchRadius = configuration.searchRadius();
    maxPlayers = configuration.maxPlayers();
    debug = configuration.debug();
    amplifierPerDamage = configuration.amplifierPerDamage();
    potionAmplifierCompensation = configuration.potionAmplifierCompensation();
    approvedPotionEffects =
        configuration.approvedPotionEffects().stream()
            .map(value -> value.trim().toLowerCase(Locale.ROOT))
            .filter(value -> !value.isBlank())
            .collect(Collectors.toUnmodifiableSet());
    balance =
        new MobCombatBalance(
            configuration.maxMobLevel(),
            configuration.normalMinOffset(),
            configuration.normalMaxOffset(),
            configuration.eliteMinOffset(),
            configuration.eliteMaxOffset(),
            configuration.minibossMinOffset(),
            configuration.minibossMaxOffset(),
            configuration.rareBossMinOffset(),
            configuration.rareBossMaxOffset(),
            configuration.normalBaseHp(),
            configuration.normalHpPerLevel(),
            configuration.normalMultiplier(),
            configuration.eliteMultiplier(),
            configuration.minibossMultiplier(),
            configuration.rareBossMultiplier(),
            configuration.normalAttackFloor(),
            configuration.eliteAttackFloor(),
            configuration.minibossAttackFloor(),
            configuration.rareBossAttackFloor(),
            configuration.maxAmplifierCompensation());
    scalingPolicy = balance.policy();
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
    int effectiveCombatLevel =
        nearbyPlayers.stream().mapToInt(CombatPower::effectiveLevel).max().orElse(1);
    int selectedLevel =
        scalingPolicy.selectMobLevel(effectiveCombatLevel, 0, domainKind(profile.kind()));
    event.setMobLevel(selectedLevel);
    if (debug) {
      plugin
          .getLogger()
          .info(
              "Scaled "
                  + mobId
                  + " using highest active sword level: effective="
                  + effectiveCombatLevel
                  + ", profile="
                  + profile.name()
                  + ", level="
                  + selectedLevel);
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
    int level = scalingPolicy.clampLevel((int) Math.round(event.getMobLevel()));
    entity.getPersistentDataContainer().set(mobLevelKey, PersistentDataType.INTEGER, level);
    List<CombatPower> players = findNearbyPlayers(event.getLocation());
    double strongestHit = players.stream().mapToDouble(this::serverNormalHitDamage).max().orElse(1);
    double strongestAmplifier =
        players.stream().mapToDouble(this::amplifierCompensation).max().orElse(0);
    double hp =
        scalingPolicy.finalHp(level, domainKind(profile.kind()), strongestHit, strongestAmplifier);

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
        .map(
            player -> {
              ItemStack sword = heroItemService.findStrongestHeroSword(player);
              return sword == null
                  ? null
                  : new CombatPower(
                      player,
                      scalingPolicy.effectiveCombatLevel(
                          heroItemService.getSwordLevel(sword),
                          heroItemService.getSwordPrestige(sword)),
                      player.getLocation().distanceSquared(location));
            })
        .filter(java.util.Objects::nonNull)
        .sorted(
            Comparator.comparingInt(CombatPower::effectiveLevel)
                .reversed()
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
    return scalingPolicy.boundedAmplifierCompensation(requested);
  }

  private MobScalingPolicy.MobKind domainKind(MobCombatBalance.MobKind kind) {
    return MobScalingPolicy.MobKind.valueOf(kind == null ? "NORMAL" : kind.name());
  }

  private record CombatPower(Player player, int effectiveLevel, double distanceSquared) {}
}
