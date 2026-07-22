package com.dungeonhero.config;

import com.dungeonhero.common.ConfigValues;
import com.dungeonhero.feature.quest.DungeonRushConfiguration;
import com.dungeonhero.feature.quest.QuestScoringPolicy;
import com.dungeonhero.feature.quest.RewardPolicy;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Immutable, typed snapshot of all DungeonHero plugin configuration. */
public record DungeonHeroConfiguration(
    String locale,
    Progression progression,
    SwordXpItem swordXpItem,
    Ascension ascension,
    FragmentCaps fragmentCaps,
    Armor armor,
    Map<String, Fragment> fragments,
    Ranks ranks,
    Hud hud,
    MobScaling mobScaling,
    DamageProtection damageProtection,
    Party party,
    DungeonRushConfiguration dungeonRush,
    TrainingDummy trainingDummy,
    Admin admin,
    ConfigurationSection gameplayFeatures) {

  public static final String DEFAULT_LOCALE = "en_us";

  public static DungeonHeroConfiguration load(JavaPlugin plugin) {
    return load(plugin.getConfig(), plugin.getLogger());
  }

  public static DungeonHeroConfiguration load(FileConfiguration config, Logger logger) {
    Reader reader = new Reader(config, logger);
    SwordXpItem swordXpItem = readSwordXpItem(reader);
    Progression progression = readProgression(reader, swordXpItem.xp());
    Ascension ascension = readAscension(reader);
    FragmentCaps fragmentCaps = readFragmentCaps(reader);
    Armor armor = readArmor(reader);
    return new DungeonHeroConfiguration(
        reader.string(Keys.LOCALE, DEFAULT_LOCALE),
        progression,
        swordXpItem,
        ascension,
        fragmentCaps,
        armor,
        readFragments(reader),
        readRanks(reader),
        readHud(reader),
        readMobScaling(reader),
        readDamageProtection(reader),
        readParty(reader),
        readDungeonRush(reader),
        readTrainingDummy(reader),
        new Admin(reader.string(Keys.ADMIN_RESET_PERMISSION, Defaults.ADMIN_RESET_PERMISSION)),
        config.getConfigurationSection(Keys.GAMEPLAY_FEATURES));
  }

  private static Progression readProgression(Reader reader, int configuredItemXp) {
    return new Progression(
        reader.bool(Keys.AUTO_MOB_KILL_XP, Defaults.AUTO_MOB_KILL_XP),
        reader.bool(Keys.HOSTILE_MOB_KILL_XP_ONLY, Defaults.HOSTILE_MOB_KILL_XP_ONLY),
        reader.positiveInt(Keys.XP_PER_MOB_KILL, configuredItemXp),
        reader.positiveInt(Keys.MYTHIC_MOB_XP, Defaults.MYTHIC_MOB_XP),
        reader.atLeastInt(Keys.ELITE_XP, Defaults.ELITE_XP, Defaults.MYTHIC_MOB_XP),
        reader.atLeastInt(Keys.MINIBOSS_XP, Defaults.MINIBOSS_XP, Defaults.ELITE_XP),
        reader.atLeastInt(Keys.RARE_BOSS_XP, Defaults.RARE_BOSS_XP, Defaults.MINIBOSS_XP),
        reader.positiveInt(Keys.BASE_XP_REQUIRED, Defaults.BASE_XP_REQUIRED),
        reader.atLeastDouble(Keys.XP_REQUIRED_MULTIPLIER, Defaults.XP_REQUIRED_MULTIPLIER, 1),
        reader.positiveInt(Keys.MAX_SWORD_LEVEL, Defaults.MAX_SWORD_LEVEL));
  }

  private static SwordXpItem readSwordXpItem(Reader reader) {
    return new SwordXpItem(
        reader.string(Keys.SWORD_XP_MATERIAL, Defaults.SWORD_XP_MATERIAL),
        reader.string(Keys.SWORD_XP_NAME, Defaults.SWORD_XP_NAME),
        List.copyOf(reader.strings(Keys.SWORD_XP_LORE)),
        reader.positiveInt(Keys.SWORD_XP_AMOUNT, Defaults.SWORD_XP_AMOUNT));
  }

  private static Ascension readAscension(Reader reader) {
    Map<Integer, Double> bonuses = new LinkedHashMap<>();
    for (int prestige = 1; prestige <= Defaults.MAX_PRESTIGE_BONUS; prestige++) {
      bonuses.put(
          prestige,
          reader.atLeastDouble(
              Keys.ASCENSION_BONUSES + prestige,
              Defaults.RARE_DROP_BONUSES.getOrDefault(prestige, 0D),
              0));
    }
    Set<Material> materials = EnumSet.noneOf(Material.class);
    for (String raw : reader.strings(Keys.ASCENSION_ELIGIBLE_MATERIALS)) {
      try {
        materials.add(Material.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
      } catch (IllegalArgumentException ignored) {
        reader.warn("Ignoring unknown rare-drop material: " + raw);
      }
    }
    Set<String> items =
        reader.strings(Keys.ASCENSION_ELIGIBLE_ITEMS).stream()
            .map(value -> value.trim().toLowerCase(Locale.ROOT))
            .filter(value -> !value.isBlank())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    return new Ascension(
        reader.bool(
            Keys.ASCENSION_ENABLED, Defaults.ASCENSION_ENABLED, Keys.LEGACY_PRESTIGE_ENABLED),
        reader.positiveInt(
            Keys.ASCENSION_REQUIRED_LEVEL,
            Defaults.ASCENSION_REQUIRED_LEVEL,
            Keys.LEGACY_PRESTIGE_REQUIRED_LEVEL),
        reader.atLeastInt(
            Keys.ASCENSION_MAX_PRESTIGE,
            Defaults.ASCENSION_MAX_PRESTIGE,
            0,
            Keys.LEGACY_PRESTIGE_MAX),
        reader.atLeastLong(
            Keys.ASCENSION_CONFIRMATION_SECONDS, Defaults.ASCENSION_CONFIRMATION_SECONDS, 10),
        reader.atLeastDouble(Keys.ASCENSION_XP_MULTIPLIER, Defaults.ASCENSION_XP_MULTIPLIER, 1),
        Map.copyOf(bonuses),
        Set.copyOf(materials),
        items);
  }

  private static FragmentCaps readFragmentCaps(Reader reader) {
    Map<Integer, Double> caps = new LinkedHashMap<>();
    for (int rank = 1; rank <= Defaults.MAX_RANK; rank++) {
      caps.put(
          rank,
          reader.doubleValue(
              Keys.FRAGMENT_RANK_CAPS + rank,
              Defaults.RANK_CAPS.getOrDefault(rank, Defaults.MAX_RANK_CAP),
              Keys.LEGACY_FRAGMENT_CAPS + rank));
    }
    return new FragmentCaps(
        reader.atLeastDouble(Keys.FRAGMENT_MAX_DAMAGE, Defaults.MAX_STORED_DAMAGE, 280),
        Map.copyOf(caps));
  }

  private static Armor readArmor(Reader reader) {
    Map<Integer, Double> caps = new LinkedHashMap<>();
    for (int rank = 1; rank <= Defaults.MAX_RANK; rank++) {
      caps.put(
          rank,
          reader.atLeastDouble(
              Keys.ARMOR_RANK_CAPS + rank,
              Defaults.RANK_CAPS.getOrDefault(rank, Defaults.MAX_RANK_CAP),
              0));
    }
    return new Armor(
        reader.bool(Keys.ARMOR_ENABLED, true),
        reader.positiveInt(Keys.ARMOR_MAX_LEVEL, Defaults.MAX_ARMOR_LEVEL),
        reader.atLeastDouble(Keys.ARMOR_LEVEL_REDUCTION, .0015, 0),
        reader.atLeastDouble(Keys.ARMOR_MAX_LEVEL_REDUCTION, .15, 0),
        reader.atLeastDouble(Keys.ARMOR_FRAGMENT_REDUCTION, .01, 0),
        reader.atLeastDouble(Keys.ARMOR_MAX_FRAGMENT_REDUCTION, .20, 0),
        reader.atLeastDouble(Keys.ARMOR_MAX_TOTAL_REDUCTION, .40, 0),
        reader.atLeastDouble(Keys.ARMOR_LAST_STAND_THRESHOLD, .30, 0),
        reader.atLeastLong(Keys.ARMOR_LAST_STAND_COOLDOWN, 30, 1),
        reader.atLeastDouble(Keys.ARMOR_MAXIMUM_STORED, 100000, 0),
        Map.copyOf(caps));
  }

  private static Map<String, Fragment> readFragments(Reader reader) {
    ConfigurationSection section = reader.config().getConfigurationSection(Keys.FRAGMENTS);
    if (section == null) return Map.of();
    Map<String, Fragment> fragments = new LinkedHashMap<>();
    for (String configuredId : section.getKeys(false)) {
      ConfigurationSection fragment = section.getConfigurationSection(configuredId);
      if (fragment == null) continue;
      String id = normalizeId(configuredId);
      fragments.put(
          id.toLowerCase(Locale.ROOT),
          new Fragment(
              id,
              fragment.getString("Type", ""),
              fragment.getString("Stat", "").trim().toUpperCase(Locale.ROOT),
              fragment.getDouble("Amount", 0)));
    }
    return Map.copyOf(fragments);
  }

  private static Ranks readRanks(Reader reader) {
    List<Rank> ranks = new ArrayList<>();
    ConfigurationSection section = reader.config().getConfigurationSection(Keys.RANK_LIST);
    if (section != null) {
      for (String key : section.getKeys(false)) {
        ConfigurationSection rank = section.getConfigurationSection(key);
        if (rank == null) continue;
        try {
          int number = Integer.parseInt(key);
          ranks.add(
              new Rank(
                  number,
                  rank.getString("Name", "Rank " + number),
                  Math.max(1, rank.getInt("RequiredSwordLevel", number == 1 ? 1 : 10)),
                  Math.max(1, rank.getInt("SwordLevelCap", 100)),
                  Math.max(
                      1,
                      rank.contains("ArmorLevelCap")
                          ? rank.getInt("ArmorLevelCap", 100)
                          : rank.getInt("SwordLevelCap", 100)),
                  Math.max(0, rank.getLong("Cost", 0))));
        } catch (NumberFormatException ignored) {
          reader.warn("Ignoring invalid DungeonHero rank key: " + key);
        }
      }
    }
    return new Ranks(reader.string(Keys.COIN_NAME, Defaults.COIN_NAME), List.copyOf(ranks));
  }

  private static Hud readHud(Reader reader) {
    return new Hud(
        reader.bool(Keys.HUD_ENABLED, Defaults.HUD_ENABLED),
        reader.atLeastLong(Keys.HUD_UPDATE_TICKS, Defaults.HUD_UPDATE_TICKS, 1));
  }

  private static MobScaling readMobScaling(Reader reader) {
    return new MobScaling(
        reader.bool(Keys.MOB_SCALING_ENABLED, Defaults.MOB_SCALING_ENABLED),
        Set.copyOf(
            reader.strings(Keys.MOB_SCALING_WORLDS).stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList()),
        reader.atLeastDouble(Keys.MOB_SCALING_SEARCH_RADIUS, Defaults.MOB_SCALING_SEARCH_RADIUS, 1),
        reader.atLeastInt(Keys.MOB_SCALING_MAX_PLAYERS, Defaults.MOB_SCALING_MAX_PLAYERS, 1),
        reader.bool(Keys.MOB_SCALING_DEBUG, Defaults.MOB_SCALING_DEBUG),
        reader.string(
            Keys.MOB_PARTY_SCALING_MODE,
            Defaults.MOB_PARTY_SCALING_MODE,
            Keys.LEGACY_MOB_PARTY_MODE),
        reader.atLeastInt(Keys.MOB_MAX_LEVEL, Defaults.MOB_MAX_LEVEL, 1, Keys.LEGACY_MOB_MAX_LEVEL),
        reader.intValue(Keys.MOB_OFFSET_NORMAL_MIN, -2),
        reader.intValue(Keys.MOB_OFFSET_NORMAL_MAX, 4),
        reader.intValue(Keys.MOB_OFFSET_ELITE_MIN, 0),
        reader.intValue(Keys.MOB_OFFSET_ELITE_MAX, 4),
        reader.intValue(Keys.MOB_OFFSET_MINIBOSS_MIN, 1),
        reader.intValue(Keys.MOB_OFFSET_MINIBOSS_MAX, 4),
        reader.intValue(Keys.MOB_OFFSET_RARE_BOSS_MIN, 2),
        reader.intValue(Keys.MOB_OFFSET_RARE_BOSS_MAX, 4),
        reader.atLeastDouble(Keys.MOB_HP_NORMAL_BASE, 400, 0),
        reader.atLeastDouble(Keys.MOB_HP_NORMAL_PER_LEVEL, 40, 0),
        reader.atLeastDouble(Keys.MOB_HP_NORMAL_MULTIPLIER, 1, 0),
        reader.atLeastDouble(Keys.MOB_HP_ELITE_MULTIPLIER, 3, 0),
        reader.atLeastDouble(Keys.MOB_HP_MINIBOSS_MULTIPLIER, 8, 0),
        reader.atLeastDouble(Keys.MOB_HP_RARE_BOSS_MULTIPLIER, 18, 0),
        reader.atLeastDouble(Keys.MOB_ATTACK_NORMAL, 6, 0),
        reader.atLeastDouble(Keys.MOB_ATTACK_ELITE, 12, 0),
        reader.atLeastDouble(Keys.MOB_ATTACK_MINIBOSS, 25, 0),
        reader.atLeastDouble(Keys.MOB_ATTACK_RARE_BOSS, 50, 0),
        reader.atLeastDouble(Keys.MOB_HP_MAX_AMPLIFIER, 0.5, 0),
        reader.atLeastDouble(Keys.MOB_HP_AMPLIFIER_PER_DAMAGE, 0.005, 0),
        reader.atLeastDouble(Keys.POTION_COMPENSATION_PER_LEVEL, 0.02, 0),
        Set.copyOf(reader.strings(Keys.APPROVED_POTION_EFFECTS)));
  }

  private static DamageProtection readDamageProtection(Reader reader) {
    return new DamageProtection(reader.atLeastDouble(Keys.CRITICAL_MULTIPLIER, 4, 1));
  }

  private static Party readParty(Reader reader) {
    return new Party(
        reader.bool(Keys.PARTY_ENABLED, true),
        reader.atLeastInt(Keys.PARTY_MAX_SIZE, 5, 2),
        reader.bool(Keys.PARTY_SAME_WORLD, true),
        reader.atLeastLong(Keys.PARTY_INVITATION_SECONDS, 60, 10));
  }

  private static DungeonRushConfiguration readDungeonRush(Reader reader) {
    List<String> worlds = normalize(reader.strings(Keys.RUSH_WORLDS), false);
    if (worlds.isEmpty()) worlds = List.of("dungeon_world");
    List<String> biomes = normalize(reader.strings(Keys.RUSH_BIOMES), true);
    List<QuestScoringPolicy.QuestType> types =
        reader.strings(Keys.RUSH_QUEST_TYPES).stream()
            .map(DungeonHeroConfiguration::parseQuestType)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
    if (types.isEmpty()) types = List.of(QuestScoringPolicy.QuestType.MOST_DUNGEON_MOBS_KILLED);
    RewardPolicy rewardPolicy = new RewardPolicy();
    Map<Integer, List<RewardPolicy.Reward>> rewards = new LinkedHashMap<>();
    rewards.put(1, readRewards(reader, rewardPolicy, "First", 100));
    rewards.put(2, readRewards(reader, rewardPolicy, "Second", 75));
    rewards.put(3, readRewards(reader, rewardPolicy, "Third", 50));
    return new DungeonRushConfiguration(
        reader.bool(Keys.RUSH_ENABLED, true),
        worlds,
        biomes,
        types,
        reader.positiveInt(Keys.RUSH_DURATION, 5),
        reader.positiveInt(Keys.RUSH_INTERVAL, 60),
        reader.positiveInt(Keys.RUSH_FIRST_DELAY, 5),
        reader.positiveInt(Keys.RUSH_MINIMUM_KILLS, 1),
        Map.copyOf(rewards));
  }

  private static List<RewardPolicy.Reward> readRewards(
      Reader reader, RewardPolicy policy, String place, long legacyCoins) {
    String path = Keys.RUSH_REWARDS + place;
    List<RewardPolicy.Reward> rewards = new ArrayList<>();
    for (Map<?, ?> raw : reader.config().getMapList(path)) {
      RewardPolicy.Reward reward =
          policy.parse(
              ConfigValues.mapString(raw, "Type", "").toLowerCase(Locale.ROOT),
              ConfigValues.mapLong(raw, "Amount", 0),
              ConfigValues.mapString(raw, "Material", ""),
              ConfigValues.mapString(raw, "Command", ""));
      if (reward != null) rewards.add(reward);
    }
    if (rewards.isEmpty()) {
      long coins = reader.longValue(path + "Coins", legacyCoins);
      long swordXp = reader.longValue(Keys.RUSH_SWORD_XP, 25);
      if (coins > 0) rewards.add(policy.fallbackCoins(coins));
      if (swordXp > 0) rewards.add(policy.fallbackSwordXp(swordXp));
    }
    return List.copyOf(rewards);
  }

  private static TrainingDummy readTrainingDummy(Reader reader) {
    return new TrainingDummy(
        reader.bool(Keys.DUMMY_ENABLED, true),
        reader.atLeastDouble(Keys.DUMMY_HEALTH, 1000, 1),
        reader.atLeastDouble(Keys.DUMMY_SEARCH_RADIUS, 48, 4),
        reader.atLeastDouble(Keys.DUMMY_SPAWN_DISTANCE, 3, 2),
        reader.atLeastLong(Keys.DUMMY_DAMAGE_WINDOW, 5, 1),
        reader.bool(Keys.DUMMY_HOLOGRAM_ENABLED, true),
        reader.atLeastDouble(Keys.DUMMY_HOLOGRAM_HEIGHT, 2.6, 1.5));
  }

  private static List<String> normalize(List<String> values, boolean upper) {
    return values.stream()
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .map(value -> upper ? value.toUpperCase(Locale.ROOT) : value.toLowerCase(Locale.ROOT))
        .toList();
  }

  private static QuestScoringPolicy.QuestType parseQuestType(String raw) {
    String value =
        raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    return switch (value) {
      case "MOST_DUNGEON_MOBS", "MOST_DUNGEON_MOBS_KILLED" ->
          QuestScoringPolicy.QuestType.MOST_DUNGEON_MOBS_KILLED;
      case "MOST_MYTHIC_MOBS", "MOST_MYTHIC_MOBS_KILLED" ->
          QuestScoringPolicy.QuestType.MOST_MYTHIC_MOBS_KILLED;
      default -> null;
    };
  }

  private static String normalizeId(String id) {
    String trimmed = id.trim();
    return trimmed.regionMatches(true, 0, "mm:", 0, 3)
        ? "mm:" + trimmed.substring(3)
        : "mm:" + trimmed;
  }

  public record Progression(
      boolean autoMobKillXp,
      boolean hostileMobKillXpOnly,
      int xpPerMobKill,
      int mythicMobXp,
      int eliteXp,
      int minibossXp,
      int rareBossXp,
      int baseXpRequired,
      double xpRequiredMultiplier,
      int maxSwordLevel) {}

  public record SwordXpItem(String material, String name, List<String> lore, int xp) {}

  public record Ascension(
      boolean enabled,
      int requiredLevel,
      int maxPrestige,
      long confirmationSeconds,
      double xpMultiplier,
      Map<Integer, Double> rareDropBonuses,
      Set<Material> eligibleMaterials,
      Set<String> eligibleMythicItems) {}

  public record FragmentCaps(double maximumStoredDamage, Map<Integer, Double> rankCaps) {}

  public record Armor(
      boolean enabled,
      int maxLevel,
      double levelReductionPerLevel,
      double maxLevelReduction,
      double fragmentReductionPerPoint,
      double maxFragmentReduction,
      double maxTotalReduction,
      double lastStandHealthThreshold,
      long lastStandCooldownSeconds,
      double maximumStoredArmor,
      Map<Integer, Double> rankCaps) {}

  public record Fragment(String id, String type, String stat, double amount) {}

  public record Ranks(String coinName, List<Rank> ranks) {}

  public record Rank(
      int number,
      String name,
      int requiredSwordLevel,
      int swordLevelCap,
      int armorLevelCap,
      long cost) {
    public Rank(int number, String name, int requiredSwordLevel, int swordLevelCap, long cost) {
      this(number, name, requiredSwordLevel, swordLevelCap, swordLevelCap, cost);
    }
  }

  public record Hud(boolean useVanillaXpBar, long updateTicks) {}

  public record MobScaling(
      boolean enabled,
      Set<String> worlds,
      double searchRadius,
      int maxPlayers,
      boolean debug,
      String partyScalingMode,
      int maxMobLevel,
      int normalMinOffset,
      int normalMaxOffset,
      int eliteMinOffset,
      int eliteMaxOffset,
      int minibossMinOffset,
      int minibossMaxOffset,
      int rareBossMinOffset,
      int rareBossMaxOffset,
      double normalBaseHp,
      double normalHpPerLevel,
      double normalMultiplier,
      double eliteMultiplier,
      double minibossMultiplier,
      double rareBossMultiplier,
      double normalAttackFloor,
      double eliteAttackFloor,
      double minibossAttackFloor,
      double rareBossAttackFloor,
      double maxAmplifierCompensation,
      double amplifierPerDamage,
      double potionAmplifierCompensation,
      Set<String> approvedPotionEffects) {}

  public record DamageProtection(double criticalDamageMultiplier) {}

  public record Party(
      boolean enabled, int maxSize, boolean requireSameWorld, long invitationSeconds) {}

  public record TrainingDummy(
      boolean enabled,
      double health,
      double searchRadius,
      double spawnDistance,
      long damageWindowSeconds,
      boolean hologramEnabled,
      double hologramHeight) {}

  public record Admin(String resetSwordPermission) {}

  private static final class Defaults {
    private static final String SWORD_XP_MATERIAL = "EXPERIENCE_BOTTLE";
    private static final String SWORD_XP_NAME = "&aHero Sword XP";
    private static final int SWORD_XP_AMOUNT = 25;
    private static final boolean AUTO_MOB_KILL_XP = true;
    private static final boolean HOSTILE_MOB_KILL_XP_ONLY = true;
    private static final int MYTHIC_MOB_XP = 25;
    private static final int ELITE_XP = 100;
    private static final int MINIBOSS_XP = 400;
    private static final int RARE_BOSS_XP = 1000;
    private static final int BASE_XP_REQUIRED = 100;
    private static final double XP_REQUIRED_MULTIPLIER = 1.25;
    private static final int MAX_SWORD_LEVEL = 100;
    private static final boolean ASCENSION_ENABLED = true;
    private static final int ASCENSION_REQUIRED_LEVEL = 100;
    private static final int ASCENSION_MAX_PRESTIGE = 5;
    private static final long ASCENSION_CONFIRMATION_SECONDS = 30;
    private static final double ASCENSION_XP_MULTIPLIER = 2;
    private static final int MAX_PRESTIGE_BONUS = 5;
    private static final int MAX_RANK = 10;
    private static final double MAX_RANK_CAP = 280;
    private static final double MAX_STORED_DAMAGE = 100000;
    private static final int MAX_ARMOR_LEVEL = 100;
    private static final boolean HUD_ENABLED = true;
    private static final long HUD_UPDATE_TICKS = 10;
    private static final boolean MOB_SCALING_ENABLED = true;
    private static final double MOB_SCALING_SEARCH_RADIUS = 32;
    private static final int MOB_SCALING_MAX_PLAYERS = 5;
    private static final boolean MOB_SCALING_DEBUG = false;
    private static final String MOB_PARTY_SCALING_MODE = "HIGHEST";
    private static final int MOB_MAX_LEVEL = 104;
    private static final String ADMIN_RESET_PERMISSION = "dungeonhero.admin.resetsword";
    private static final String COIN_NAME = "Dungeon Coins";
    private static final Map<Integer, Double> RARE_DROP_BONUSES =
        Map.of(1, .10, 2, .15, 3, .20, 4, .25, 5, .30);
    private static final Map<Integer, Double> RANK_CAPS =
        Map.of(
            1, 10D, 2, 20D, 3, 35D, 4, 55D, 5, 80D, 6, 110D, 7, 145D, 8, 185D, 9, 230D, 10, 280D);
  }

  private static final class Keys {
    private static final String LOCALE = "DungeonHero.Locale";
    private static final String GAMEPLAY_FEATURES = "DungeonHero.Gameplay.Features";
    private static final String FRAGMENTS = "DungeonHero.Fragments";
    private static final String AUTO_MOB_KILL_XP = "DungeonHero.Progression.AutoMobKillXP";
    private static final String HOSTILE_MOB_KILL_XP_ONLY =
        "DungeonHero.Progression.HostileMobKillXPOnly";
    private static final String XP_PER_MOB_KILL = "DungeonHero.Progression.XPPerMobKill";
    private static final String MYTHIC_MOB_XP = "DungeonHero.Progression.MythicMobXP";
    private static final String ELITE_XP = "DungeonHero.Progression.EliteXP";
    private static final String MINIBOSS_XP = "DungeonHero.Progression.MinibossXP";
    private static final String RARE_BOSS_XP = "DungeonHero.Progression.RareBossXP";
    private static final String BASE_XP_REQUIRED = "DungeonHero.Progression.BaseXPRequired";
    private static final String XP_REQUIRED_MULTIPLIER =
        "DungeonHero.Progression.XPRequiredMultiplier";
    private static final String MAX_SWORD_LEVEL = "DungeonHero.Progression.MaxSwordLevel";
    private static final String SWORD_XP_MATERIAL = "DungeonHero.Progression.SwordXPItem.Material";
    private static final String SWORD_XP_NAME = "DungeonHero.Progression.SwordXPItem.Name";
    private static final String SWORD_XP_LORE = "DungeonHero.Progression.SwordXPItem.Lore";
    private static final String SWORD_XP_AMOUNT = "DungeonHero.Progression.SwordXPItem.XP";
    private static final String ASCENSION_ENABLED = "DungeonHero.HeroAscension.Enabled";
    private static final String ASCENSION_REQUIRED_LEVEL =
        "DungeonHero.HeroAscension.RequiredSwordLevel";
    private static final String ASCENSION_MAX_PRESTIGE = "DungeonHero.HeroAscension.MaxPrestige";
    private static final String ASCENSION_CONFIRMATION_SECONDS =
        "DungeonHero.HeroAscension.ConfirmationSeconds";
    private static final String ASCENSION_XP_MULTIPLIER = "DungeonHero.HeroAscension.XpMultiplier";
    private static final String ASCENSION_BONUSES = "DungeonHero.HeroAscension.RareDropBonuses.";
    private static final String ASCENSION_ELIGIBLE_MATERIALS =
        "DungeonHero.HeroAscension.RareDropEligibleMaterials";
    private static final String ASCENSION_ELIGIBLE_ITEMS =
        "DungeonHero.HeroAscension.RareDropEligibleMythicItems";
    private static final String LEGACY_PRESTIGE_ENABLED =
        "DungeonHero.Progression.Prestige.Enabled";
    private static final String LEGACY_PRESTIGE_REQUIRED_LEVEL =
        "DungeonHero.Progression.Prestige.RequiredSwordLevel";
    private static final String LEGACY_PRESTIGE_MAX =
        "DungeonHero.Progression.Prestige.MaxPrestige";
    private static final String FRAGMENT_MAX_DAMAGE =
        "DungeonHero.FragmentCaps.MaximumStoredDamage";
    private static final String FRAGMENT_RANK_CAPS = "DungeonHero.FragmentCaps.RankCaps.";
    private static final String ARMOR_ENABLED = "DungeonHero.Armor.Enabled";
    private static final String ARMOR_MAX_LEVEL = "DungeonHero.Armor.MaxLevel";
    private static final String ARMOR_LEVEL_REDUCTION = "DungeonHero.Armor.LevelReductionPerLevel";
    private static final String ARMOR_MAX_LEVEL_REDUCTION = "DungeonHero.Armor.MaxLevelReduction";
    private static final String ARMOR_FRAGMENT_REDUCTION =
        "DungeonHero.Armor.FragmentReductionPerPoint";
    private static final String ARMOR_MAX_FRAGMENT_REDUCTION =
        "DungeonHero.Armor.MaxFragmentReduction";
    private static final String ARMOR_MAX_TOTAL_REDUCTION = "DungeonHero.Armor.MaxTotalReduction";
    private static final String ARMOR_LAST_STAND_THRESHOLD =
        "DungeonHero.Armor.LastStandHealthThreshold";
    private static final String ARMOR_LAST_STAND_COOLDOWN =
        "DungeonHero.Armor.LastStandCooldownSeconds";
    private static final String ARMOR_MAXIMUM_STORED = "DungeonHero.Armor.MaximumStoredArmor";
    private static final String ARMOR_RANK_CAPS = "DungeonHero.Armor.RankCaps.";
    private static final String LEGACY_FRAGMENT_CAPS = "DungeonHero.Fragments.Caps.";
    private static final String COIN_NAME = "DungeonHero.Ranks.CoinName";
    private static final String RANK_LIST = "DungeonHero.Ranks.List";
    private static final String HUD_ENABLED = "DungeonHero.Hud.UseVanillaXpBar";
    private static final String HUD_UPDATE_TICKS = "DungeonHero.Hud.UpdateTicks";
    private static final String MOB_SCALING_ENABLED = "DungeonHero.MobScaling.Enabled";
    private static final String MOB_SCALING_WORLDS = "DungeonHero.MobScaling.Worlds";
    private static final String MOB_SCALING_SEARCH_RADIUS = "DungeonHero.MobScaling.SearchRadius";
    private static final String MOB_SCALING_MAX_PLAYERS = "DungeonHero.MobScaling.MaxPlayers";
    private static final String MOB_SCALING_DEBUG = "DungeonHero.MobScaling.Debug";
    private static final String MOB_PARTY_SCALING_MODE = "DungeonHero.MobScaling.PartyScalingMode";
    private static final String LEGACY_MOB_PARTY_MODE = "DungeonHero.MobScaling.PartyMode";
    private static final String MOB_MAX_LEVEL = "DungeonHero.MobScaling.MaximumMobLevel";
    private static final String LEGACY_MOB_MAX_LEVEL = "DungeonHero.MobScaling.MaxLevel";
    private static final String MOB_OFFSET_NORMAL_MIN =
        "DungeonHero.MobScaling.LevelOffsets.Normal.Min";
    private static final String MOB_OFFSET_NORMAL_MAX =
        "DungeonHero.MobScaling.LevelOffsets.Normal.Max";
    private static final String MOB_OFFSET_ELITE_MIN =
        "DungeonHero.MobScaling.LevelOffsets.Elite.Min";
    private static final String MOB_OFFSET_ELITE_MAX =
        "DungeonHero.MobScaling.LevelOffsets.Elite.Max";
    private static final String MOB_OFFSET_MINIBOSS_MIN =
        "DungeonHero.MobScaling.LevelOffsets.Miniboss.Min";
    private static final String MOB_OFFSET_MINIBOSS_MAX =
        "DungeonHero.MobScaling.LevelOffsets.Miniboss.Max";
    private static final String MOB_OFFSET_RARE_BOSS_MIN =
        "DungeonHero.MobScaling.LevelOffsets.RareBoss.Min";
    private static final String MOB_OFFSET_RARE_BOSS_MAX =
        "DungeonHero.MobScaling.LevelOffsets.RareBoss.Max";
    private static final String MOB_HP_NORMAL_BASE = "DungeonHero.MobHp.NormalBase";
    private static final String MOB_HP_NORMAL_PER_LEVEL = "DungeonHero.MobHp.NormalHpPerLevel";
    private static final String MOB_HP_NORMAL_MULTIPLIER =
        "DungeonHero.MobHp.ProfileMultipliers.Normal";
    private static final String MOB_HP_ELITE_MULTIPLIER =
        "DungeonHero.MobHp.ProfileMultipliers.Elite";
    private static final String MOB_HP_MINIBOSS_MULTIPLIER =
        "DungeonHero.MobHp.ProfileMultipliers.Miniboss";
    private static final String MOB_HP_RARE_BOSS_MULTIPLIER =
        "DungeonHero.MobHp.ProfileMultipliers.RareBoss";
    private static final String MOB_ATTACK_NORMAL = "DungeonHero.MobHp.MinimumAttacks.Normal";
    private static final String MOB_ATTACK_ELITE = "DungeonHero.MobHp.MinimumAttacks.Elite";
    private static final String MOB_ATTACK_MINIBOSS = "DungeonHero.MobHp.MinimumAttacks.Miniboss";
    private static final String MOB_ATTACK_RARE_BOSS = "DungeonHero.MobHp.MinimumAttacks.RareBoss";
    private static final String MOB_HP_MAX_AMPLIFIER =
        "DungeonHero.MobHp.MaximumAmplifierCompensation";
    private static final String MOB_HP_AMPLIFIER_PER_DAMAGE =
        "DungeonHero.MobHp.DamageAmplifierCompensationPerDamage";
    private static final String POTION_COMPENSATION_PER_LEVEL =
        "DungeonHero.DamageAmplifiers.PotionCompensationPerLevel";
    private static final String APPROVED_POTION_EFFECTS =
        "DungeonHero.DamageAmplifiers.ApprovedPotionEffects";
    private static final String CRITICAL_MULTIPLIER =
        "DungeonHero.DamageProtection.CriticalDamageMultiplier";
    private static final String PARTY_ENABLED = "DungeonHero.Party.Enabled";
    private static final String PARTY_MAX_SIZE = "DungeonHero.Party.MaxSize";
    private static final String PARTY_SAME_WORLD = "DungeonHero.Party.RequireSameWorld";
    private static final String PARTY_INVITATION_SECONDS = "DungeonHero.Party.InvitationSeconds";
    private static final String RUSH_ENABLED = "DungeonHero.DungeonRush.Enabled";
    private static final String RUSH_WORLDS = "DungeonHero.DungeonRush.Worlds";
    private static final String RUSH_BIOMES = "DungeonHero.DungeonRush.Biomes";
    private static final String RUSH_QUEST_TYPES = "DungeonHero.DungeonRush.QuestTypes";
    private static final String RUSH_DURATION = "DungeonHero.DungeonRush.DurationMinutes";
    private static final String RUSH_INTERVAL = "DungeonHero.DungeonRush.IntervalMinutes";
    private static final String RUSH_FIRST_DELAY = "DungeonHero.DungeonRush.FirstDelayMinutes";
    private static final String RUSH_MINIMUM_KILLS = "DungeonHero.DungeonRush.MinimumKills";
    private static final String RUSH_REWARDS = "DungeonHero.DungeonRush.Rewards.";
    private static final String RUSH_SWORD_XP = "DungeonHero.DungeonRush.Rewards.SwordXP";
    private static final String DUMMY_ENABLED = "DungeonHero.TargetDummy.Enabled";
    private static final String DUMMY_HEALTH = "DungeonHero.TargetDummy.Health";
    private static final String DUMMY_SEARCH_RADIUS = "DungeonHero.TargetDummy.SearchRadius";
    private static final String DUMMY_SPAWN_DISTANCE = "DungeonHero.TargetDummy.SpawnDistance";
    private static final String DUMMY_DAMAGE_WINDOW = "DungeonHero.TargetDummy.DamageWindowSeconds";
    private static final String DUMMY_HOLOGRAM_ENABLED = "DungeonHero.TargetDummy.Hologram.Enabled";
    private static final String DUMMY_HOLOGRAM_HEIGHT = "DungeonHero.TargetDummy.Hologram.Height";
    private static final String ADMIN_RESET_PERMISSION = "DungeonHero.Admin.ResetSwordPermission";
  }

  private static final class Reader {
    private final FileConfiguration config;
    private final Logger logger;

    private Reader(FileConfiguration config, Logger logger) {
      this.config = config;
      this.logger = logger;
    }

    private FileConfiguration config() {
      return config;
    }

    private String string(String canonical, String fallback, String... legacy) {
      String selected = select(canonical, legacy);
      return config.getString(selected, fallback);
    }

    private boolean bool(String canonical, boolean fallback, String... legacy) {
      return config.getBoolean(select(canonical, legacy), fallback);
    }

    private int intValue(String canonical, int fallback, String... legacy) {
      return config.getInt(select(canonical, legacy), fallback);
    }

    private int positiveInt(String canonical, int fallback, String... legacy) {
      return Math.max(1, intValue(canonical, fallback, legacy));
    }

    private int atLeastInt(String canonical, int fallback, int minimum, String... legacy) {
      return Math.max(minimum, intValue(canonical, fallback, legacy));
    }

    private long longValue(String canonical, long fallback, String... legacy) {
      return config.getLong(select(canonical, legacy), fallback);
    }

    private long atLeastLong(String canonical, long fallback, long minimum, String... legacy) {
      return Math.max(minimum, longValue(canonical, fallback, legacy));
    }

    private double doubleValue(String canonical, double fallback, String... legacy) {
      double value = config.getDouble(select(canonical, legacy), fallback);
      if (Double.isFinite(value)) return value;
      warn("Configuration key '" + canonical + "' must be finite; using " + fallback + ".");
      return fallback;
    }

    private double atLeastDouble(
        String canonical, double fallback, double minimum, String... legacy) {
      return Math.max(minimum, doubleValue(canonical, fallback, legacy));
    }

    private List<String> strings(String canonical, String... legacy) {
      return List.copyOf(config.getStringList(select(canonical, legacy)));
    }

    private String select(String canonical, String... legacy) {
      boolean canonicalPresent = config.contains(canonical);
      for (String old : legacy) {
        if (config.contains(old)) {
          warn(
              (canonicalPresent
                  ? "Ignoring legacy configuration key '"
                      + old
                      + "' because '"
                      + canonical
                      + "' is configured."
                  : "Configuration key '"
                      + old
                      + "' is deprecated; use '"
                      + canonical
                      + "' instead."));
          if (!canonicalPresent) return old;
        }
      }
      return canonical;
    }

    private void warn(String message) {
      logger.warning("[DungeonHero config] " + message);
    }
  }
}
