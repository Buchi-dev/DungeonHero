package com.dungeonhero.feature.quest;

import com.dungeonhero.feature.coins.DungeonCoinService;
import com.dungeonhero.feature.sword.SwordProgressionService;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Runs automatic five-minute competitive dungeon quests. */
public final class DungeonRushService implements Listener {

    private static final long MILLIS_PER_MINUTE = 60_000L;

    private final JavaPlugin plugin;
    private final DungeonCoinService coinService;
    private final SwordProgressionService swordProgressionService;
    private final Set<UUID> mythicDeathsAwaitingVanillaCheck = new HashSet<>();
    private final Map<UUID, Score> scores = new LinkedHashMap<>();

    private List<String> worlds = List.of();
    private List<String> biomes = List.of();
    private List<QuestType> questTypes = List.of(QuestType.MOST_DUNGEON_MOBS_KILLED);
    private boolean enabled;
    private int durationMinutes;
    private int intervalMinutes;
    private int firstDelayMinutes;
    private int minimumKills;
    private Map<Integer, List<RewardDefinition>> rewardsByPlace = Map.of();
    private boolean active;
    private long endsAt;
    private long nextStartAt;
    private QuestType currentQuest;
    private String currentBiome;
    private long roundNumber;
    private List<ScoreView> lastResults = List.of();
    private QuestType lastQuest;
    private String lastBiome;
    private BukkitTask tickTask;

    public DungeonRushService(JavaPlugin plugin,
                              DungeonCoinService coinService,
                              SwordProgressionService swordProgressionService) {
        this.plugin = plugin;
        this.coinService = coinService;
        this.swordProgressionService = swordProgressionService;
        reload();
    }

    public synchronized void start() {
        if (!enabled || tickTask != null) {
            return;
        }
        if (nextStartAt <= 0) {
            nextStartAt = System.currentTimeMillis() + firstDelayMinutes * MILLIS_PER_MINUTE;
        }
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public synchronized void close() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        active = false;
        scores.clear();
    }

    public synchronized void reload() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }

        enabled = plugin.getConfig().getBoolean("DungeonHero.DungeonRush.Enabled", true);
        worlds = plugin.getConfig().getStringList("DungeonHero.DungeonRush.Worlds").stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .toList();
        if (worlds.isEmpty()) {
            worlds = List.of("dungeon_world");
        }
        biomes = plugin.getConfig().getStringList("DungeonHero.DungeonRush.Biomes").stream()
                .map(value -> value.toUpperCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .toList();
        durationMinutes = positiveInt("DurationMinutes", 5);
        intervalMinutes = positiveInt("IntervalMinutes", 60);
        firstDelayMinutes = positiveInt("FirstDelayMinutes", 5);
        minimumKills = positiveInt("MinimumKills", 1);
        rewardsByPlace = loadRewards();
        questTypes = loadQuestTypes();

        active = false;
        scores.clear();
        currentQuest = null;
        currentBiome = null;
        nextStartAt = System.currentTimeMillis() + firstDelayMinutes * MILLIS_PER_MINUTE;

        if (enabled) {
            start();
        }
    }

    public synchronized void sendStatus(Player player) {
        if (!enabled) {
            player.sendMessage(Component.text("Dungeon Rush quests are disabled.", NamedTextColor.YELLOW));
            return;
        }

        if (!active) {
            player.sendMessage(Component.text("Next Dungeon Rush: "
                    + formatDuration(nextStartAt - System.currentTimeMillis()), NamedTextColor.GOLD));
            return;
        }

        player.sendMessage(Component.text("◆ DUNGEON RUSH", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Objective: " + currentQuest.displayName, NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Biome: " + currentBiome, NamedTextColor.GREEN));
        player.sendMessage(Component.text("Goal: Top 3 players with the most kills", NamedTextColor.LIGHT_PURPLE));
        player.sendMessage(Component.text("Time remaining: "
                + formatDuration(endsAt - System.currentTimeMillis()), NamedTextColor.AQUA));
        sendLeaderboard(player, currentScores());
    }

    public synchronized void sendTop(Player player) {
        if (active) {
            player.sendMessage(Component.text("◆ CURRENT DUNGEON RUSH", NamedTextColor.GOLD));
            player.sendMessage(Component.text("Objective: " + currentQuest.displayName, NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Biome: " + currentBiome, NamedTextColor.GREEN));
            sendLeaderboard(player, currentScores());
            return;
        }

        if (lastQuest == null || lastResults.isEmpty()) {
            player.sendMessage(Component.text("No Dungeon Rush results are available yet.", NamedTextColor.YELLOW));
            return;
        }
        player.sendMessage(Component.text("◆ LAST DUNGEON RUSH", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Objective: " + lastQuest.displayName, NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Biome: " + lastBiome, NamedTextColor.GREEN));
        sendLeaderboard(player, lastResults);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        if (!enabled || !(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }

        UUID entityId = entity.getUniqueId();
        mythicDeathsAwaitingVanillaCheck.add(entityId);
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> mythicDeathsAwaitingVanillaCheck.remove(entityId), 2L);

        if (event.getKiller() instanceof Player player) {
            recordKill(player, entity.getLocation(), KillType.MYTHIC);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVanillaMobDeath(EntityDeathEvent event) {
        if (!enabled || !(event.getEntity() instanceof Monster monster)) {
            return;
        }

        UUID entityId = monster.getUniqueId();
        if (mythicDeathsAwaitingVanillaCheck.contains(entityId) || isActiveMythicMob(entityId)) {
            return;
        }
        Player killer = monster.getKiller();
        if (killer != null) {
            recordKill(killer, monster.getLocation(), KillType.DUNGEON);
        }
    }

    private synchronized void tick() {
        if (!enabled) {
            return;
        }
        long now = System.currentTimeMillis();
        if (active) {
            if (now >= endsAt) {
                finishRound(now);
            }
        } else if (now >= nextStartAt) {
            startRound(now);
        }
    }

    private void startRound(long now) {
        scores.clear();
        currentQuest = questTypes.get(ThreadLocalRandom.current().nextInt(questTypes.size()));
        currentBiome = biomes.isEmpty()
                ? "All Biomes"
                : biomes.get(ThreadLocalRandom.current().nextInt(biomes.size()));
        roundNumber++;
        endsAt = now + durationMinutes * MILLIS_PER_MINUTE;
        active = true;
        Bukkit.broadcast(Component.text("◆ DUNGEON RUSH STARTED! ", NamedTextColor.GOLD)
                .append(Component.text(currentQuest.displayName + " in " + currentBiome + " for "
                                + durationMinutes + " minutes.",
                        NamedTextColor.YELLOW)));
    }

    private void finishRound(long now) {
        List<ScoreView> results = currentScores();
        lastResults = results;
        lastQuest = currentQuest;
        lastBiome = currentBiome;
        active = false;
        currentQuest = null;
        currentBiome = null;
        nextStartAt = now + intervalMinutes * MILLIS_PER_MINUTE;

        Bukkit.broadcast(Component.text("◆ DUNGEON RUSH COMPLETE!", NamedTextColor.GREEN));
        if (results.isEmpty()) {
            Bukkit.broadcast(Component.text("No players participated in this round.", NamedTextColor.GRAY));
            return;
        }

        for (int index = 0; index < Math.min(3, results.size()); index++) {
            ScoreView result = results.get(index);
            int place = index + 1;
            if (result.kills < minimumKills) {
                Bukkit.broadcast(Component.text(place + ". " + result.name + " - " + result.kills
                        + " kills (minimum requirement not reached)", NamedTextColor.GRAY));
                continue;
            }

            List<String> rewardSummary = awardRewards(place, result);
            Bukkit.broadcast(Component.text(place + ". " + result.name + " - " + result.kills
                    + " kills" + (rewardSummary.isEmpty() ? "" : " | " + String.join(", ", rewardSummary)),
                    place == 1 ? NamedTextColor.GOLD : NamedTextColor.AQUA));
        }
    }

    private List<String> awardRewards(int place, ScoreView result) {
        List<String> summary = new ArrayList<>();
        Player player = Bukkit.getPlayer(result.playerId);
        for (RewardDefinition reward : rewardsByPlace.getOrDefault(place, List.of())) {
            switch (reward.type) {
                case "coins" -> {
                    if (coinService.add(result.playerId, reward.amount)) {
                        summary.add("+" + coinService.format(reward.amount) + " Dungeon Coins");
                    }
                }
                case "sword_xp" -> {
                    if (player != null && reward.amount > 0) {
                        swordProgressionService.awardQuestExperience(player, (int) Math.min(Integer.MAX_VALUE,
                                reward.amount));
                        summary.add("+" + reward.amount + " Sword XP");
                    }
                }
                case "item" -> {
                    if (player == null) {
                        continue;
                    }
                    Material material = Material.matchMaterial(reward.material);
                    if (material == null || material.isAir() || reward.amount <= 0) {
                        plugin.getLogger().warning("Invalid Dungeon Rush item reward: " + reward.material);
                        continue;
                    }
                    giveItemReward(player, material, reward.amount);
                    summary.add(reward.amount + " " + material.name());
                }
                case "command" -> {
                    String command = reward.command
                            .replace("%player%", result.name)
                            .replace("%uuid%", result.playerId.toString())
                            .replace("%place%", String.valueOf(place))
                            .replace("%kills%", String.valueOf(result.kills));
                    command = replaceQuestPlaceholders(command, result);
                    if (command.startsWith("/")) {
                        command = command.substring(1);
                    }
                    if (!command.isBlank()) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                        summary.add("custom reward");
                    }
                }
                default -> plugin.getLogger().warning("Unknown Dungeon Rush reward type: " + reward.type);
            }
        }
        return summary;
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private void giveItemReward(Player player, Material material, long amount) {
        long remaining = amount;
        while (remaining > 0) {
            int stackAmount = (int) Math.min(material.getMaxStackSize(), remaining);
            giveOrDrop(player, new ItemStack(material, stackAmount));
            remaining -= stackAmount;
        }
    }

    private synchronized void recordKill(Player player, Location location, KillType killType) {
        if (!active || !isTrackedWorld(location) || !isTrackedBiome(location)
                || !currentQuest.accepts(killType)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        Score score = scores.get(playerId);
        if (score == null) {
            score = new Score(playerId, player.getName(), 0, System.currentTimeMillis());
            scores.put(playerId, score);
        }
        score.kills++;
    }

    private List<ScoreView> currentScores() {
        return scores.values().stream()
                .map(ScoreView::from)
                .sorted(Comparator.comparingInt(ScoreView::kills).reversed()
                        .thenComparingLong(ScoreView::firstKillAt)
                        .thenComparing(ScoreView::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void sendLeaderboard(Player player, List<ScoreView> results) {
        if (results.isEmpty()) {
            player.sendMessage(Component.text("No players have registered a kill yet.", NamedTextColor.GRAY));
            return;
        }
        for (int index = 0; index < Math.min(3, results.size()); index++) {
            ScoreView score = results.get(index);
            player.sendMessage(Component.text((index + 1) + ". " + score.name + " - "
                    + score.kills + " kills", index == 0 ? NamedTextColor.GOLD : NamedTextColor.AQUA));
        }
    }

    private boolean isTrackedWorld(Location location) {
        return location.getWorld() != null
                && worlds.contains(location.getWorld().getName().toLowerCase(Locale.ROOT));
    }

    private boolean isTrackedBiome(Location location) {
        return currentBiome == null || currentBiome.equals("All Biomes")
                || location.getBlock().getBiome().name().equalsIgnoreCase(currentBiome);
    }

    private String replaceQuestPlaceholders(String command, ScoreView result) {
        long timeLeftSeconds = active
                ? Math.max(0, (endsAt - System.currentTimeMillis()) / 1_000L) : 0;
        String questName = active && currentQuest != null
                ? currentQuest.displayName : lastQuest == null ? "Dungeon Rush" : lastQuest.displayName;
        String questType = active && currentQuest != null
                ? currentQuest.name() : lastQuest == null ? "UNKNOWN" : lastQuest.name();
        String questBiome = active && currentBiome != null
                ? currentBiome : lastBiome == null ? "All Biomes" : lastBiome;
        String timeLeft = formatDuration(timeLeftSeconds * 1_000L);
        String duration = formatDuration(durationMinutes * MILLIS_PER_MINUTE);
        String worldsValue = String.join(",", worlds);
        return command
                .replace("%quest_name%", questName)
                .replace("%quest_type%", questType)
                .replace("%quest_time_left%", timeLeft)
                .replace("%quest_time_left_seconds%", String.valueOf(timeLeftSeconds))
                .replace("%quest_duration%", duration)
                .replace("%quest_duration_seconds%", String.valueOf(durationMinutes * 60L))
                .replace("%quest_biome%", questBiome)
                .replace("%quest_world%", worldsValue)
                .replace("%quest_goal%", "Top 3 players with the most kills")
                .replace("%quest_round%", String.valueOf(roundNumber))
                .replace("%objective%", questName)
                .replace("%time_left%", timeLeft)
                .replace("%duration%", duration)
                .replace("%biome%", questBiome)
                .replace("%world%", worldsValue);
    }

    private List<QuestType> loadQuestTypes() {
        List<QuestType> loaded = new ArrayList<>();
        for (String raw : plugin.getConfig().getStringList("DungeonHero.DungeonRush.QuestTypes")) {
            QuestType type = QuestType.fromConfig(raw);
            if (type != null && !loaded.contains(type)) {
                loaded.add(type);
            }
        }
        return loaded.isEmpty() ? List.of(QuestType.MOST_DUNGEON_MOBS_KILLED) : List.copyOf(loaded);
    }

    private int positiveInt(String path, int fallback) {
        return Math.max(1, plugin.getConfig().getInt("DungeonHero.DungeonRush." + path, fallback));
    }

    private boolean isActiveMythicMob(UUID entityId) {
        try {
            return io.lumine.mythic.bukkit.MythicBukkit.inst().getMobManager().isActiveMob(entityId);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private Map<Integer, List<RewardDefinition>> loadRewards() {
        Map<Integer, List<RewardDefinition>> loaded = new LinkedHashMap<>();
        loaded.put(1, readRewards("First", 100));
        loaded.put(2, readRewards("Second", 75));
        loaded.put(3, readRewards("Third", 50));
        return Map.copyOf(loaded);
    }

    private List<RewardDefinition> readRewards(String place, long legacyCoins) {
        String path = "DungeonHero.DungeonRush.Rewards." + place;
        List<RewardDefinition> rewards = new ArrayList<>();
        for (Map<?, ?> raw : plugin.getConfig().getMapList(path)) {
            String type = mapString(raw, "Type", "").toLowerCase(Locale.ROOT);
            if (type.isBlank()) {
                continue;
            }
            rewards.add(new RewardDefinition(type, mapLong(raw, "Amount", 0),
                    mapString(raw, "Material", ""), mapString(raw, "Command", "")));
        }
        if (rewards.isEmpty()) {
            long coins = plugin.getConfig().getLong(
                    "DungeonHero.DungeonRush.Rewards." + place + "Coins", legacyCoins);
            long swordXp = plugin.getConfig().getLong("DungeonHero.DungeonRush.Rewards.SwordXP", 25);
            if (coins > 0) {
                rewards.add(new RewardDefinition("coins", coins, "", ""));
            }
            if (swordXp > 0) {
                rewards.add(new RewardDefinition("sword_xp", swordXp, "", ""));
            }
        }
        return List.copyOf(rewards);
    }

    private String mapString(Map<?, ?> values, String key, String fallback) {
        Object value = values.get(key);
        return value == null ? fallback : String.valueOf(value).trim();
    }

    private long mapLong(Map<?, ?> values, String key, long fallback) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? fallback : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String formatDuration(long millis) {
        long seconds = Math.max(0, millis / 1_000L);
        long minutes = seconds / 60L;
        long remainingSeconds = seconds % 60L;
        return minutes + "m " + remainingSeconds + "s";
    }

    private enum KillType {
        DUNGEON,
        MYTHIC
    }

    private enum QuestType {
        MOST_DUNGEON_MOBS_KILLED("Most Dungeon Mobs Killed") {
            @Override
            boolean accepts(KillType killType) {
                return true;
            }
        },
        MOST_MYTHIC_MOBS_KILLED("Most Mythic Mobs Killed") {
            @Override
            boolean accepts(KillType killType) {
                return killType == KillType.MYTHIC;
            }
        };

        private final String displayName;

        QuestType(String displayName) {
            this.displayName = displayName;
        }

        abstract boolean accepts(KillType killType);

        private static QuestType fromConfig(String raw) {
            String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT)
                    .replace('-', '_').replace(' ', '_');
            return switch (value) {
                case "MOST_DUNGEON_MOBS", "MOST_DUNGEON_MOBS_KILLED" -> MOST_DUNGEON_MOBS_KILLED;
                case "MOST_MYTHIC_MOBS", "MOST_MYTHIC_MOBS_KILLED" -> MOST_MYTHIC_MOBS_KILLED;
                default -> null;
            };
        }
    }

    private static final class Score {
        private final UUID playerId;
        private final String name;
        private int kills;
        private final long firstKillAt;

        private Score(UUID playerId, String name, int kills, long firstKillAt) {
            this.playerId = playerId;
            this.name = name;
            this.kills = kills;
            this.firstKillAt = firstKillAt;
        }
    }

    private record ScoreView(UUID playerId, String name, int kills, long firstKillAt) {
        private static ScoreView from(Score score) {
            return new ScoreView(score.playerId, score.name, score.kills, score.firstKillAt);
        }
    }

    private record RewardDefinition(String type, long amount, String material, String command) {
    }
}
