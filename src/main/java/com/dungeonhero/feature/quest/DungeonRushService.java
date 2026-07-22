package com.dungeonhero.feature.quest;

import com.dungeonhero.common.BukkitPlayerResolver;
import com.dungeonhero.common.ItemDeliveryService;
import com.dungeonhero.common.PlayerResolver;
import com.dungeonhero.config.DungeonHeroConfiguration;
import com.dungeonhero.feature.armor.ArmorProgressionService;
import com.dungeonhero.feature.coins.DungeonCoinService;
import com.dungeonhero.feature.sword.SwordProgressionService;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Application service for Dungeon Rush lifecycle, presentation, and orchestration. */
public final class DungeonRushService {

  private static final long MILLIS_PER_MINUTE = 60_000L;

  private final JavaPlugin plugin;
  private final DungeonCoinService coinService;
  private final SwordProgressionService swordProgressionService;
  private final ArmorProgressionService armorProgressionService;
  private final QuestScoringPolicy scoringPolicy = new QuestScoringPolicy();
  private final DungeonRushRoundState roundState = new DungeonRushRoundState(scoringPolicy);
  private final PlayerResolver playerResolver;
  private final ItemDeliveryService itemDeliveryService;
  private final Random random = ThreadLocalRandom.current();
  private DungeonRushRewardDistributor rewardDistributor;
  private DungeonRushConfiguration configuration;
  private BukkitTask tickTask;

  public DungeonRushService(
      JavaPlugin plugin,
      DungeonCoinService coinService,
      SwordProgressionService swordProgressionService) {
    this(
        plugin,
        coinService,
        swordProgressionService,
        null,
        new BukkitPlayerResolver(),
        new ItemDeliveryService(),
        DungeonHeroConfiguration.load(plugin).dungeonRush());
  }

  public DungeonRushService(
      JavaPlugin plugin,
      DungeonCoinService coinService,
      SwordProgressionService swordProgressionService,
      PlayerResolver playerResolver,
      ItemDeliveryService itemDeliveryService) {
    this(
        plugin,
        coinService,
        swordProgressionService,
        null,
        playerResolver,
        itemDeliveryService,
        DungeonHeroConfiguration.load(plugin).dungeonRush());
  }

  public DungeonRushService(
      JavaPlugin plugin,
      DungeonCoinService coinService,
      SwordProgressionService swordProgressionService,
      ArmorProgressionService armorProgressionService,
      PlayerResolver playerResolver,
      ItemDeliveryService itemDeliveryService,
      DungeonRushConfiguration configuration) {
    this.plugin = plugin;
    this.coinService = coinService;
    this.swordProgressionService = swordProgressionService;
    this.armorProgressionService = armorProgressionService;
    this.playerResolver = playerResolver;
    this.itemDeliveryService = itemDeliveryService;
    reload(configuration);
  }

  public synchronized void start() {
    if (!configuration.enabled() || tickTask != null) return;
    tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
  }

  public synchronized void close() {
    if (tickTask != null) {
      tickTask.cancel();
      tickTask = null;
    }
    roundState.clear();
  }

  public synchronized void reload() {
    reload(DungeonHeroConfiguration.load(plugin).dungeonRush());
  }

  public synchronized void reload(DungeonRushConfiguration configuration) {
    if (tickTask != null) {
      tickTask.cancel();
      tickTask = null;
    }
    this.configuration = configuration;
    rewardDistributor =
        new DungeonRushRewardDistributor(
            plugin,
            coinService,
            swordProgressionService,
            armorProgressionService,
            playerResolver,
            itemDeliveryService);
    roundState.reset(System.currentTimeMillis(), configuration.firstDelayMinutes());
  }

  public synchronized void sendStatus(Player player) {
    if (!configuration.enabled()) {
      player.sendMessage(
          Component.text("Dungeon Rush quests are disabled.", NamedTextColor.YELLOW));
      return;
    }
    if (!roundState.active()) {
      player.sendMessage(
          Component.text(
              "Next Dungeon Rush: "
                  + formatDuration(roundState.nextStartAt() - System.currentTimeMillis()),
              NamedTextColor.GOLD));
      return;
    }
    player.sendMessage(Component.text("â—† DUNGEON RUSH", NamedTextColor.GOLD));
    player.sendMessage(
        Component.text(
            "Objective: " + displayName(roundState.currentQuest()), NamedTextColor.YELLOW));
    player.sendMessage(Component.text("Biome: " + roundState.currentBiome(), NamedTextColor.GREEN));
    player.sendMessage(
        Component.text("Goal: Top 3 players with the most kills", NamedTextColor.LIGHT_PURPLE));
    player.sendMessage(
        Component.text(
            "Time remaining: " + formatDuration(roundState.endsAt() - System.currentTimeMillis()),
            NamedTextColor.AQUA));
    sendLeaderboard(player, roundState.leaderboard());
  }

  public synchronized void sendTop(Player player) {
    if (roundState.active()) {
      player.sendMessage(Component.text("â—† CURRENT DUNGEON RUSH", NamedTextColor.GOLD));
      player.sendMessage(
          Component.text(
              "Objective: " + displayName(roundState.currentQuest()), NamedTextColor.YELLOW));
      player.sendMessage(
          Component.text("Biome: " + roundState.currentBiome(), NamedTextColor.GREEN));
      sendLeaderboard(player, roundState.leaderboard());
      return;
    }
    if (roundState.lastQuest() == null || roundState.lastResults().isEmpty()) {
      player.sendMessage(
          Component.text("No Dungeon Rush results are available yet.", NamedTextColor.YELLOW));
      return;
    }
    player.sendMessage(Component.text("â—† LAST DUNGEON RUSH", NamedTextColor.GOLD));
    player.sendMessage(
        Component.text("Objective: " + displayName(roundState.lastQuest()), NamedTextColor.YELLOW));
    player.sendMessage(Component.text("Biome: " + roundState.lastBiome(), NamedTextColor.GREEN));
    sendLeaderboard(player, roundState.lastResults());
  }

  /** Called by {@link DungeonRushListener} after event filtering and Mythic deduplication. */
  synchronized void recordKill(
      Player player, Location location, QuestScoringPolicy.KillType killType) {
    if (!roundState.active() || !isTrackedWorld(location) || !isTrackedBiome(location)) return;
    roundState.recordKill(
        player.getUniqueId(), player.getName(), killType, System.currentTimeMillis());
  }

  boolean enabled() {
    return configuration.enabled();
  }

  private synchronized void tick() {
    if (!configuration.enabled()) return;
    long now = System.currentTimeMillis();
    if (roundState.active()) {
      if (now >= roundState.endsAt()) finishRound(now);
    } else if (now >= roundState.nextStartAt()) {
      startRound(now);
    }
  }

  private void startRound(long now) {
    roundState.start(now, configuration, random);
    Bukkit.broadcast(
        Component.text("â—† DUNGEON RUSH STARTED! ", NamedTextColor.GOLD)
            .append(
                Component.text(
                    displayName(roundState.currentQuest())
                        + " in "
                        + roundState.currentBiome()
                        + " for "
                        + configuration.durationMinutes()
                        + " minutes.",
                    NamedTextColor.YELLOW)));
  }

  private void finishRound(long now) {
    DungeonRushRoundState.RoundResult result =
        roundState.finish(now, configuration.intervalMinutes());
    Bukkit.broadcast(Component.text("â—† DUNGEON RUSH COMPLETE!", NamedTextColor.GREEN));
    if (result.scores().isEmpty()) {
      Bukkit.broadcast(
          Component.text("No players participated in this round.", NamedTextColor.GRAY));
      return;
    }
    for (int index = 0; index < Math.min(3, result.scores().size()); index++) {
      QuestScoringPolicy.Score score = result.scores().get(index);
      int place = index + 1;
      if (score.kills() < configuration.minimumKills()) {
        Bukkit.broadcast(
            Component.text(
                place
                    + ". "
                    + score.name()
                    + " - "
                    + score.kills()
                    + " kills (minimum requirement not reached)",
                NamedTextColor.GRAY));
        continue;
      }
      List<String> rewardSummary =
          rewardDistributor.distribute(
              place,
              score,
              configuration.rewardsByPlace().getOrDefault(place, List.of()),
              command -> replaceQuestPlaceholders(command, score));
      Bukkit.broadcast(
          Component.text(
              place
                  + ". "
                  + score.name()
                  + " - "
                  + score.kills()
                  + " kills"
                  + (rewardSummary.isEmpty() ? "" : " | " + String.join(", ", rewardSummary)),
              place == 1 ? NamedTextColor.GOLD : NamedTextColor.AQUA));
    }
  }

  private void sendLeaderboard(Player player, List<QuestScoringPolicy.Score> results) {
    if (results.isEmpty()) {
      player.sendMessage(
          Component.text("No players have registered a kill yet.", NamedTextColor.GRAY));
      return;
    }
    for (int index = 0; index < Math.min(3, results.size()); index++) {
      QuestScoringPolicy.Score score = results.get(index);
      player.sendMessage(
          Component.text(
              (index + 1) + ". " + score.name() + " - " + score.kills() + " kills",
              index == 0 ? NamedTextColor.GOLD : NamedTextColor.AQUA));
    }
  }

  private boolean isTrackedWorld(Location location) {
    return location != null
        && location.getWorld() != null
        && configuration.worlds().contains(location.getWorld().getName().toLowerCase(Locale.ROOT));
  }

  private boolean isTrackedBiome(Location location) {
    return location != null
        && (configuration.biomes().isEmpty()
            || roundState.currentBiome() == null
            || roundState.currentBiome().equals("All Biomes")
            || location.getBlock().getBiome().name().equalsIgnoreCase(roundState.currentBiome()));
  }

  private String replaceQuestPlaceholders(String command, QuestScoringPolicy.Score result) {
    long timeLeftSeconds =
        roundState.active()
            ? Math.max(0, (roundState.endsAt() - System.currentTimeMillis()) / 1_000L)
            : 0;
    QuestScoringPolicy.QuestType quest =
        roundState.active() ? roundState.currentQuest() : roundState.lastQuest();
    String questName = quest == null ? "Dungeon Rush" : displayName(quest);
    String questType = quest == null ? "UNKNOWN" : quest.name();
    String questBiome =
        roundState.active() && roundState.currentBiome() != null
            ? roundState.currentBiome()
            : roundState.lastBiome() == null ? "All Biomes" : roundState.lastBiome();
    String timeLeft = formatDuration(timeLeftSeconds * 1_000L);
    String duration = formatDuration(configuration.durationMinutes() * MILLIS_PER_MINUTE);
    String worlds = String.join(",", configuration.worlds());
    return command
        .replace("%player%", result.name())
        .replace("%uuid%", result.playerId().toString())
        .replace("%place%", String.valueOf(placeFor(result)))
        .replace("%kills%", String.valueOf(result.kills()))
        .replace("%quest_name%", questName)
        .replace("%quest_type%", questType)
        .replace("%quest_time_left%", timeLeft)
        .replace("%quest_time_left_seconds%", String.valueOf(timeLeftSeconds))
        .replace("%quest_duration%", duration)
        .replace("%quest_duration_seconds%", String.valueOf(configuration.durationMinutes() * 60L))
        .replace("%quest_biome%", questBiome)
        .replace("%quest_world%", worlds)
        .replace("%quest_goal%", "Top 3 players with the most kills")
        .replace("%quest_round%", String.valueOf(roundState.roundNumber()))
        .replace("%objective%", questName)
        .replace("%time_left%", timeLeft)
        .replace("%duration%", duration)
        .replace("%biome%", questBiome)
        .replace("%world%", worlds);
  }

  private int placeFor(QuestScoringPolicy.Score result) {
    List<QuestScoringPolicy.Score> results = roundState.lastResults();
    int index = results.indexOf(result);
    return index < 0 ? 0 : index + 1;
  }

  private String displayName(QuestScoringPolicy.QuestType type) {
    return type == QuestScoringPolicy.QuestType.MOST_MYTHIC_MOBS_KILLED
        ? "Most Mythic Mobs Killed"
        : "Most Dungeon Mobs Killed";
  }

  private String formatDuration(long millis) {
    long seconds = Math.max(0, millis / 1_000L);
    return (seconds / 60L) + "m " + (seconds % 60L) + "s";
  }
}
