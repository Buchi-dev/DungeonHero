package com.dungeonhero.feature.reputation;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import com.dungeonhero.feature.party.PartyService;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tracks the shared Dungeon World reputation and the weekly contribution of
 * individual adventurers. Routine activity is capped by biome and day so the
 * reputation cannot be rushed by grinding one mob type.
 */
public final class DungeonReputationService implements Listener {

    private static final String REPUTATION_PATH = "Dungeon.Reputation";
    private static final String DAILY_PATH = "Daily";
    private static final String PLAYER_PATH = "Players.";
    private static final long MILLIS_PER_MINUTE = 60_000L;
    private static final double PARTICIPATION_RADIUS = 32.0;

    private static final List<RankDefinition> DEFAULT_RANKS = List.of(
            new RankDefinition("Uncharted", 0),
            new RankDefinition("Recognized", 500),
            new RankDefinition("Dangerous", 1_500),
            new RankDefinition("Notorious", 3_500),
            new RankDefinition("Renowned", 6_000),
            new RankDefinition("Legendary", 10_000)
    );

    private static final Set<String> DEFAULT_ELITES = ids(
            "DH_HeartwoodBrute", "DH_DuneColossus", "DH_MireAbomination", "DH_GlacierRavager",
            "DH_JadebackSentinel", "DH_AbyssalLeviathan", "DH_ManorRelicKeeper", "DH_CavernTyrant",
            "DW_SoulReaver");
    private static final Set<String> DEFAULT_MINIBOSSES = ids(
            "DH_BriarMatriarch", "DH_SunkenPharaoh", "DH_BogWitchQueen", "DH_FrostboundGoliath",
            "DH_TempleOverlord", "DH_OceanicLeviathan", "DH_PaleGardenWarden", "DH_DeepstoneBehemoth");
    private static final Set<String> DEFAULT_RARE_BOSSES = ids(
            "DH_VerdantSovereign", "DH_SandstormTyrant", "DH_MireSovereign", "DH_WinterColossus",
            "DH_OvergrownTitan", "DH_AbyssalEmperor", "DH_PaleheartReaper", "DH_DeepstoneOverlord",
            "DW_CryptLord");
    private static final Set<String> DEFAULT_VARIANTS = ids(
            "DH_GroveStalker", "DH_MossboundSkeleton", "DH_AridHusk", "DH_BadlandsStray",
            "DH_BogboundZombie", "DH_MireWitch", "DH_FrostStray", "DH_FrostSpider",
            "DH_VineSpider", "DH_TempleWitch", "DH_TideDrowned", "DH_AbyssalGuardian",
            "DH_CursedVindicator", "DH_ShadowSpider", "DH_CaveSpider", "DH_DeepSilverfish",
            "DW_Cryptling", "DW_BoneArcher");

    private final JavaPlugin plugin;
    private final PartyService partyService;
    private final File storageFile;
    private final Set<UUID> mythicDeathsAwaitingVanillaCheck = new HashSet<>();

    private YamlConfiguration data;
    private BukkitTask tickTask;
    private boolean dirty;
    private boolean enabled;
    private boolean trackDungeonHeroPrefixes;
    private Set<String> worlds;
    private Set<String> eliteIds;
    private Set<String> minibossIds;
    private Set<String> rareBossIds;
    private Set<String> variantIds;
    private List<RankDefinition> ranks;

    private int hostileTarget;
    private int mythicTarget;
    private int eliteDailyLimit;
    private int minibossDailyLimit;
    private int rareBossDailyLimit;
    private int contractHostileTarget;
    private int contractMythicTarget;
    private int contractEliteTarget;
    private int contractReputation;
    private int contractContribution;
    private int dailyContributionCap;
    private int minibossReputation;
    private int rareBossReputation;
    private int publicEventGoal;
    private int publicEventReward;
    private int publicEventDurationMinutes;
    private int publicEventIntervalMinutes;
    private int publicEventFirstDelayMinutes;
    private boolean publicEventsEnabled;

    public DungeonReputationService(JavaPlugin plugin, PartyService partyService) {
        this.plugin = plugin;
        this.partyService = partyService;
        this.storageFile = new File(plugin.getDataFolder(), "reputation.yml");
        ensureDataDirectory();
        reload();
    }

    public void start() {
        if (tickTask != null) {
            return;
        }
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickPublicEvent,
                20L, 20L * 20L);
    }

    public void close() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        save();
    }

    public synchronized void reload() {
        loadSettings();
        data = YamlConfiguration.loadConfiguration(storageFile);
        dirty = false;
        ensureCurrentDay();
    }

    public synchronized long getReputation() {
        ensureCurrentDay();
        return Math.max(0, data.getLong(REPUTATION_PATH, 0));
    }

    public synchronized void setReputation(long reputation) {
        data.set(REPUTATION_PATH, Math.max(0, reputation));
        dirty = true;
        save();
    }

    public synchronized void addAdminReputation(long amount) {
        addReputation(Math.max(0, amount), "admin adjustment");
        save();
    }

    public synchronized RankDefinition getCurrentRank() {
        long reputation = getReputation();
        RankDefinition current = ranks.getFirst();
        for (RankDefinition rank : ranks) {
            if (reputation >= rank.requiredReputation()) {
                current = rank;
            }
        }
        return current;
    }

    public synchronized RankDefinition getNextRank() {
        long reputation = getReputation();
        return ranks.stream()
                .filter(rank -> reputation < rank.requiredReputation())
                .findFirst()
                .orElse(null);
    }

    public synchronized ContractStatus getContract(Player player) {
        ensureCurrentDay();
        syncPlayer(player.getUniqueId(), player.getName());
        String path = playerPath(player.getUniqueId()) + ".Contract.";
        Contract contract = contractForDate(LocalDate.now());
        int progress = Math.min(contract.target(), data.getInt(path + "Progress", 0));
        boolean complete = data.getBoolean(path + "Completed", false);
        return new ContractStatus(contract.biome().displayName(), contract.type(), progress, contract.target(), complete);
    }

    public synchronized long getWeeklyContribution(UUID playerId) {
        syncPlayer(playerId, null);
        return Math.max(0, data.getLong(playerPath(playerId) + "WeeklyContribution", 0));
    }

    public synchronized void sendStatus(org.bukkit.command.CommandSender sender) {
        RankDefinition current = getCurrentRank();
        RankDefinition next = getNextRank();
        long reputation = getReputation();
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("◆ DUNGEON REPUTATION", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("» Reputation: ", NamedTextColor.GRAY)
                .append(Component.text(format(reputation), NamedTextColor.GREEN)));
        sender.sendMessage(Component.text("» Rank: ", NamedTextColor.GRAY)
                .append(Component.text(current.name(), rankColor(current))));
        if (next == null) {
            sender.sendMessage(Component.text("★ The dungeon has reached its highest reputation.",
                    NamedTextColor.LIGHT_PURPLE));
        } else {
            sender.sendMessage(Component.text("» Next rank: ", NamedTextColor.GRAY)
                    .append(Component.text(next.name() + " (" + format(next.requiredReputation()) + ")",
                            NamedTextColor.AQUA)));
        }
        PublicEventStatus event = getPublicEventStatus();
        if (event.active()) {
            sender.sendMessage(Component.text("» Event: ", NamedTextColor.GRAY)
                    .append(Component.text(event.biome() + " " + event.progress() + "/" + event.goal(),
                            NamedTextColor.YELLOW)));
            sender.sendMessage(Component.text("  Boss objective: " + (event.bossDefeated() ? "complete" : "incomplete"),
                    event.bossDefeated() ? NamedTextColor.GREEN : NamedTextColor.RED));
        } else {
            sender.sendMessage(Component.text("» Next public event: ", NamedTextColor.GRAY)
                    .append(Component.text(formatDuration(event.nextEventMillis() - System.currentTimeMillis()),
                            NamedTextColor.YELLOW)));
        }
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
    }

    public synchronized void sendContract(Player player) {
        ContractStatus contract = getContract(player);
        senderLine(player, "━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY);
        senderLine(player, "◆ DAILY DUNGEON CONTRACT", NamedTextColor.GOLD);
        senderLine(player, "━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY);
        senderLine(player, "» Biome: " + contract.biome(), NamedTextColor.AQUA);
        senderLine(player, "» Objective: " + contract.type(), NamedTextColor.YELLOW);
        senderLine(player, "» Progress: " + contract.progress() + "/" + contract.target(), NamedTextColor.GREEN);
        if (contract.complete()) {
            senderLine(player, "✓ Contract complete. Return tomorrow for a new contract.", NamedTextColor.GREEN);
        } else {
            senderLine(player, "Reward: " + contractReputation + " Dungeon Reputation + "
                    + contractContribution + " Contribution", NamedTextColor.LIGHT_PURPLE);
        }
        senderLine(player, "━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY);
    }

    public synchronized void sendTop(org.bukkit.command.CommandSender sender) {
        ensureCurrentDay();
        List<ContributionEntry> entries = new ArrayList<>();
        ConfigurationSection players = data.getConfigurationSection("Players");
        if (players != null) {
            for (String key : players.getKeys(false)) {
                UUID id;
                try {
                    id = UUID.fromString(key);
                } catch (IllegalArgumentException exception) {
                    continue;
                }
                syncPlayer(id, data.getString("Players." + key + ".Name"));
                String path = playerPath(id);
                entries.add(new ContributionEntry(
                        data.getString(path + "Name", key),
                        Math.max(0, data.getLong(path + "WeeklyContribution", 0))));
            }
        }
        entries.sort(Comparator.comparingLong(ContributionEntry::contribution).reversed()
                .thenComparing(ContributionEntry::name, String.CASE_INSENSITIVE_ORDER));
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("◆ WEEKLY DUNGEON CONTRIBUTORS", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
        if (entries.isEmpty()) {
            sender.sendMessage(Component.text("No contribution has been recorded this week.", NamedTextColor.GRAY));
        } else {
            int position = 1;
            for (ContributionEntry entry : entries.stream().limit(10).toList()) {
                sender.sendMessage(Component.text(position++ + ". " + entry.name() + "  ", NamedTextColor.GRAY)
                        .append(Component.text(format(entry.contribution()) + " Contribution", NamedTextColor.AQUA)));
            }
        }
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
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

        String internalName = event.getMobType() == null ? "" : event.getMobType().getInternalName();
        EncounterKind kind = classifyMythic(internalName);
        if (kind == null) {
            return;
        }
        Location location = entity.getLocation();
        recordEncounter(kind, internalName, location, participants(event.getKiller(), location));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVanillaMobDeath(EntityDeathEvent event) {
        if (!enabled || !(event.getEntity() instanceof Monster monster)) {
            return;
        }
        UUID entityId = monster.getUniqueId();
        if (mythicDeathsAwaitingVanillaCheck.contains(entityId)
                || isActiveMythicMob(entityId)) {
            return;
        }
        Player killer = monster.getKiller();
        if (killer == null) {
            return;
        }
        Location location = monster.getLocation().clone();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (mythicDeathsAwaitingVanillaCheck.contains(entityId)) {
                return;
            }
            recordEncounter(EncounterKind.VANILLA, "", location, participants(killer, location));
        });
    }

    private synchronized void recordEncounter(EncounterKind kind, String mobId, Location location,
                                               Collection<Player> rawParticipants) {
        if (!enabled || !isTrackedWorld(location.getWorld())) {
            return;
        }
        BiomeCategory biome = BiomeCategory.fromLocation(location);
        if (biome == BiomeCategory.UNKNOWN) {
            biome = BiomeCategory.fromMobId(mobId);
        }
        if (biome == BiomeCategory.UNKNOWN) {
            return;
        }
        List<Player> players = rawParticipants.stream()
                .filter(player -> isValidParticipant(player, location))
                .collect(Collectors.collectingAndThen(Collectors.toMap(Player::getUniqueId,
                        player -> player, (first, ignored) -> first, LinkedHashMap::new),
                        map -> new ArrayList<>(map.values())));
        if (players.isEmpty()) {
            return;
        }

        ensureCurrentDay();
        for (Player player : players) {
            syncPlayer(player.getUniqueId(), player.getName());
            creditContribution(player, kind.personalContribution());
            advanceContract(player, biome, kind);
        }
        advanceDailyActivity(biome, kind);
        updatePublicEvent(biome, kind, mobId, players);
        if (kind.isBossLike()) {
            String label = kind.displayName();
            for (Player player : players) {
                player.sendMessage(Component.text("◆ " + label + " defeated! ", NamedTextColor.GOLD)
                        .append(Component.text("Your contribution has been recorded.", NamedTextColor.GREEN)));
            }
        }
        dirty = true;
    }

    private void advanceDailyActivity(BiomeCategory biome, EncounterKind kind) {
        String base = DAILY_PATH + ".Biomes." + biome.name() + ".";
        switch (kind) {
            case VANILLA -> advanceThreshold(base + "HostileKills", hostileTarget, 15,
                    biome.displayName() + " activity completed");
            case MYTHIC -> advanceThreshold(base + "MythicKills", mythicTarget, 25,
                    biome.displayName() + " Mythic hunt completed");
            case ELITE -> advanceLimited(base + "EliteKills", eliteDailyLimit, 25,
                    biome.displayName() + " elite hunt completed");
            case MINIBOSS -> advanceLimited(base + "MinibossKills", minibossDailyLimit,
                    minibossReputation, biome.displayName() + " miniboss conquered");
            case RARE_BOSS -> advanceRareBoss(biome);
        }
    }

    private void advanceThreshold(String path, int threshold, int reward, String reason) {
        int current = data.getInt(path, 0);
        if (current >= threshold) {
            return;
        }
        int updated = Math.min(threshold, current + 1);
        data.set(path, updated);
        if (updated >= threshold) {
            addReputation(reward, reason);
        }
    }

    private void advanceLimited(String path, int limit, int reward, String reason) {
        int current = data.getInt(path, 0);
        if (current >= limit) {
            return;
        }
        data.set(path, current + 1);
        addReputation(reward, reason);
    }

    private void advanceRareBoss(BiomeCategory biome) {
        List<String> defeated = new ArrayList<>(data.getStringList(DAILY_PATH + ".RareBosses"));
        String key = biome.name();
        if (defeated.contains(key)) {
            return;
        }
        defeated.add(key);
        data.set(DAILY_PATH + ".RareBosses", defeated);
        int awarded = data.getInt(DAILY_PATH + ".RareBossesAwarded", 0);
        if (awarded < rareBossDailyLimit) {
            data.set(DAILY_PATH + ".RareBossesAwarded", awarded + 1);
            addReputation(rareBossReputation, biome.displayName() + " rare boss defeated");
        }
    }

    private void advanceContract(Player player, BiomeCategory biome, EncounterKind kind) {
        String path = playerPath(player.getUniqueId()) + ".Contract.";
        Contract contract = contractForDate(LocalDate.now());
        if (!contract.biome().equals(biome.displayName()) || data.getBoolean(path + "Completed", false)
                || !contractCounts(contract.type(), kind)) {
            return;
        }
        int progress = Math.min(contract.target(), data.getInt(path + "Progress", 0) + 1);
        data.set(path + "Progress", progress);
        if (progress < contract.target()) {
            return;
        }
        data.set(path + "Completed", true);
        creditContribution(player, contractContribution);
        if (!data.getBoolean(DAILY_PATH + ".ContractCompleted", false)) {
            data.set(DAILY_PATH + ".ContractCompleted", true);
            addReputation(contractReputation, "daily contract completed");
        }
        player.sendMessage(Component.text("✓ Daily dungeon contract completed! ", NamedTextColor.GREEN)
                .append(Component.text("+" + contractReputation + " Dungeon Reputation", NamedTextColor.GOLD)));
        dirty = true;
    }

    private boolean contractCounts(String type, EncounterKind kind) {
        return switch (type) {
            case "Biome Survey" -> kind != EncounterKind.RARE_BOSS;
            case "Mythic Hunt" -> kind == EncounterKind.MYTHIC;
            case "Elite Hunt" -> kind == EncounterKind.ELITE;
            default -> false;
        };
    }

    private void updatePublicEvent(BiomeCategory biome, EncounterKind kind, String mobId,
                                   Collection<Player> players) {
        String path = "PublicEvent.";
        if (!data.getBoolean(path + "Active", false)
                || !biome.displayName().equals(data.getString(path + "Biome"))) {
            return;
        }
        for (Player player : players) {
            List<String> participants = new ArrayList<>(data.getStringList(path + "Participants"));
            String id = player.getUniqueId().toString();
            if (!participants.contains(id)) {
                participants.add(id);
                data.set(path + "Participants", participants);
            }
        }
        if (kind == EncounterKind.RARE_BOSS
                && data.getString(path + "BossId", "").equalsIgnoreCase(mobId)) {
            data.set(path + "BossDefeated", true);
            return;
        }
        if (kind != EncounterKind.RARE_BOSS) {
            int goal = data.getInt(path + "Goal", publicEventGoal);
            int previous = data.getInt(path + "Progress", 0);
            int updated = Math.min(goal, previous + 1);
            data.set(path + "Progress", updated);
            if (updated >= goal && previous < goal) {
                for (Player player : players) {
                    player.sendMessage(Component.text("Event objective complete. ", NamedTextColor.GREEN)
                            .append(Component.text("The biome boss is awakening.", NamedTextColor.GOLD)));
                }
            }
        }
        dirty = true;
    }

    private void tickPublicEvent() {
        synchronized (this) {
            if (!enabled || !publicEventsEnabled) {
                return;
            }
            ensureCurrentDay();
            long now = System.currentTimeMillis();
            String path = "PublicEvent.";
            if (data.getBoolean(path + "Active", false)) {
                if (data.getInt(path + "Progress", 0) >= data.getInt(path + "Goal", publicEventGoal)
                        && !data.getBoolean(path + "BossSpawned", false)
                        && !data.getBoolean(path + "BossDefeated", false)) {
                    spawnPublicEventBoss();
                }
                if (now >= data.getLong(path + "EndsAt", now)) {
                    finishPublicEvent();
                }
            } else if (now >= data.getLong(path + "NextStartAt", 0)) {
                startPublicEvent(now);
            }
            saveIfDirty();
        }
    }

    private void startPublicEvent(long now) {
        String path = "PublicEvent.";
        int sequence = data.getInt(path + "Sequence", 0);
        BiomeCategory biome = BiomeCategory.EVENT_BIOMES.get(Math.floorMod(
                LocalDate.now().getDayOfYear() + sequence, BiomeCategory.EVENT_BIOMES.size()));
        data.set(path + "Active", true);
        data.set(path + "Biome", biome.displayName());
        data.set(path + "BossId", biome.rareBossId());
        data.set(path + "Progress", 0);
        data.set(path + "Goal", publicEventGoal);
        data.set(path + "BossDefeated", false);
        data.set(path + "BossSpawned", false);
        data.set(path + "Participants", List.of());
        data.set(path + "StartedAt", now);
        data.set(path + "EndsAt", now + publicEventDurationMinutes * MILLIS_PER_MINUTE);
        data.set(path + "Sequence", sequence + 1);
        dirty = true;
        Bukkit.broadcast(Component.text("◆ DUNGEON EVENT: ", NamedTextColor.GOLD)
                .append(Component.text(biome.displayName() + " Awakens", NamedTextColor.LIGHT_PURPLE)));
        Bukkit.broadcast(Component.text("Defeat " + publicEventGoal + " enemies in the "
                + biome.displayName() + " biome and defeat " + bossName(biome.rareBossId()) + ".",
                NamedTextColor.YELLOW));
    }

    private void spawnPublicEventBoss() {
        String path = "PublicEvent.";
        BiomeCategory biome = BiomeCategory.fromDisplayName(data.getString(path + "Biome", ""));
        if (biome == BiomeCategory.UNKNOWN || biome.rareBossId().isBlank()) {
            data.set(path + "BossSpawned", true);
            dirty = true;
            return;
        }
        Player target = Bukkit.getOnlinePlayers().stream()
                .filter(player -> isTrackedWorld(player.getWorld()))
                .filter(player -> BiomeCategory.fromLocation(player.getLocation()) == biome)
                .findFirst().orElse(null);
        if (target == null) {
            return;
        }
        Location spawnLocation = target.getLocation().clone().add(6, 0, 0);
        try {
            io.lumine.mythic.bukkit.MythicBukkit.inst().getAPIHelper()
                    .spawnMythicMob(data.getString(path + "BossId", biome.rareBossId()), spawnLocation);
            data.set(path + "BossSpawned", true);
            dirty = true;
            Bukkit.broadcast(Component.text("⚔ " + bossName(biome.rareBossId())
                    + " has been summoned for the dungeon event!", NamedTextColor.RED));
        } catch (Exception exception) {
            data.set(path + "BossSpawned", true);
            dirty = true;
            plugin.getLogger().warning("Unable to spawn public Dungeon event boss "
                    + biome.rareBossId() + ": " + exception.getMessage());
        }
    }

    private void finishPublicEvent() {
        String path = "PublicEvent.";
        String biome = data.getString(path + "Biome", "the dungeon");
        int progress = data.getInt(path + "Progress", 0);
        int goal = data.getInt(path + "Goal", publicEventGoal);
        boolean success = progress >= goal && data.getBoolean(path + "BossDefeated", false);
        if (success) {
            addReputation(publicEventReward, biome + " public event completed");
            for (String rawId : data.getStringList(path + "Participants")) {
                try {
                    creditContribution(UUID.fromString(rawId), publicEventReward / 2);
                } catch (IllegalArgumentException ignored) {
                    // Ignore malformed legacy participant IDs.
                }
            }
            Bukkit.broadcast(Component.text("✓ DUNGEON EVENT COMPLETE: ", NamedTextColor.GREEN)
                    .append(Component.text(biome + " has been reclaimed! ", NamedTextColor.GOLD))
                    .append(Component.text("+" + publicEventReward + " Dungeon Reputation", NamedTextColor.AQUA)));
        } else {
            Bukkit.broadcast(Component.text("✗ DUNGEON EVENT FAILED: ", NamedTextColor.RED)
                    .append(Component.text(biome + " remains dangerous.", NamedTextColor.YELLOW)));
        }
        data.set(path + "Active", false);
        data.set(path + "NextStartAt", System.currentTimeMillis() + publicEventIntervalMinutes * MILLIS_PER_MINUTE);
        data.set(path + "Participants", List.of());
        dirty = true;
    }

    private void addReputation(long amount, String reason) {
        if (amount <= 0) {
            return;
        }
        long current = Math.max(0, data.getLong(REPUTATION_PATH, 0));
        RankDefinition before = rankFor(current);
        long updated = current > Long.MAX_VALUE - amount ? Long.MAX_VALUE : current + amount;
        data.set(REPUTATION_PATH, updated);
        RankDefinition after = rankFor(updated);
        dirty = true;
        if (!before.name().equals(after.name())) {
            Bukkit.broadcast(Component.text("★ DUNGEON WORLD RANK UP: ", NamedTextColor.GOLD)
                    .append(Component.text(after.name(), rankColor(after)))
                    .append(Component.text("! The dungeon has earned a new reputation.", NamedTextColor.YELLOW)));
        }
        plugin.getLogger().fine("Dungeon Reputation +" + amount + " (" + reason + ")");
    }

    private void creditContribution(Player player, int amount) {
        creditContribution(player.getUniqueId(), player.getName(), amount);
    }

    private void creditContribution(UUID playerId, int amount) {
        creditContribution(playerId, null, amount);
    }

    private void creditContribution(UUID playerId, String name, int amount) {
        if (amount <= 0) {
            return;
        }
        syncPlayer(playerId, name);
        String path = playerPath(playerId);
        int currentDaily = data.getInt(path + "DailyContribution", 0);
        int awarded = Math.min(amount, Math.max(0, dailyContributionCap - currentDaily));
        if (awarded <= 0) {
            return;
        }
        data.set(path + "DailyContribution", currentDaily + awarded);
        data.set(path + "WeeklyContribution", data.getLong(path + "WeeklyContribution", 0) + awarded);
        data.set(path + "LifetimeContribution", data.getLong(path + "LifetimeContribution", 0) + awarded);
        dirty = true;
    }

    private void syncPlayer(UUID playerId, String name) {
        String path = playerPath(playerId);
        if (name != null && !name.isBlank()) {
            data.set(path + "Name", name);
        }
        LocalDate today = LocalDate.now();
        String date = today.toString();
        if (!date.equals(data.getString(path + "DailyDate"))) {
            data.set(path + "DailyDate", date);
            data.set(path + "DailyContribution", 0);
        }
        String week = today.getYear() + "-W" + today.get(WeekFields.ISO.weekOfWeekBasedYear());
        if (!week.equals(data.getString(path + "ContributionWeek"))) {
            data.set(path + "ContributionWeek", week);
            data.set(path + "WeeklyContribution", 0);
        }
        Contract contract = contractForDate(today);
        String contractPath = path + "Contract.";
        if (!date.equals(data.getString(contractPath + "Date"))) {
            data.set(contractPath + "Date", date);
            data.set(contractPath + "Biome", contract.biome().displayName());
            data.set(contractPath + "Type", contract.type());
            data.set(contractPath + "Progress", 0);
            data.set(contractPath + "Completed", false);
            dirty = true;
        }
    }

    private void ensureCurrentDay() {
        String today = LocalDate.now().toString();
        if (today.equals(data.getString(DAILY_PATH + ".Date"))) {
            return;
        }
        data.set(DAILY_PATH + ".Date", today);
        data.set(DAILY_PATH + ".Biomes", null);
        data.set(DAILY_PATH + ".RareBosses", List.of());
        data.set(DAILY_PATH + ".RareBossesAwarded", 0);
        data.set(DAILY_PATH + ".ContractCompleted", false);
        if (!data.contains("PublicEvent.NextStartAt")) {
            data.set("PublicEvent.NextStartAt", System.currentTimeMillis()
                    + publicEventFirstDelayMinutes * MILLIS_PER_MINUTE);
        }
        dirty = true;
    }

    private Contract contractForDate(LocalDate date) {
        BiomeCategory biome = BiomeCategory.EVENT_BIOMES.get(Math.floorMod(date.getDayOfYear(),
                BiomeCategory.EVENT_BIOMES.size()));
        String[] types = {"Biome Survey", "Mythic Hunt", "Elite Hunt"};
        String type = types[Math.floorMod(date.getDayOfYear() / 2, types.length)];
        int target = switch (type) {
            case "Biome Survey" -> contractHostileTarget;
            case "Mythic Hunt" -> contractMythicTarget;
            case "Elite Hunt" -> contractEliteTarget;
            default -> contractHostileTarget;
        };
        return new Contract(biome, type, target);
    }

    private PublicEventStatus getPublicEventStatus() {
        String path = "PublicEvent.";
        return new PublicEventStatus(data.getBoolean(path + "Active", false),
                data.getString(path + "Biome", "Unknown"),
                data.getInt(path + "Progress", 0), data.getInt(path + "Goal", publicEventGoal),
                data.getBoolean(path + "BossDefeated", false),
                data.getLong(path + "NextStartAt", System.currentTimeMillis()));
    }

    private void loadSettings() {
        enabled = plugin.getConfig().getBoolean("DungeonHero.Reputation.Enabled", true);
        worlds = plugin.getConfig().getStringList("DungeonHero.Reputation.Worlds").stream()
                .map(String::trim).filter(value -> !value.isEmpty()).collect(Collectors.toUnmodifiableSet());
        trackDungeonHeroPrefixes = plugin.getConfig().getBoolean(
                "DungeonHero.Reputation.TrackDungeonHeroPrefixes", true);
        eliteIds = configuredIds("Elites", DEFAULT_ELITES);
        minibossIds = configuredIds("Minibosses", DEFAULT_MINIBOSSES);
        rareBossIds = configuredIds("RareBosses", DEFAULT_RARE_BOSSES);
        variantIds = configuredIds("Variants", DEFAULT_VARIANTS);
        ranks = loadRanks();

        hostileTarget = positiveInt("HostileActivityTarget", 50);
        mythicTarget = positiveInt("MythicActivityTarget", 10);
        eliteDailyLimit = positiveInt("EliteDailyLimit", 2);
        minibossDailyLimit = positiveInt("MinibossDailyLimit", 1);
        rareBossDailyLimit = positiveInt("RareBossDailyLimit", 2);
        contractHostileTarget = positiveInt("Contract.HostileTarget", 40);
        contractMythicTarget = positiveInt("Contract.MythicTarget", 10);
        contractEliteTarget = positiveInt("Contract.EliteTarget", 1);
        contractReputation = positiveInt("Contract.ReputationReward", 30);
        contractContribution = positiveInt("Contract.ContributionReward", 40);
        dailyContributionCap = positiveInt("DailyContributionCap", 500);
        minibossReputation = positiveInt("Rewards.MinibossReputation", 50);
        rareBossReputation = positiveInt("Rewards.RareBossReputation", 150);
        publicEventGoal = positiveInt("PublicEvents.Goal", 75);
        publicEventReward = positiveInt("PublicEvents.ReputationReward", 250);
        publicEventDurationMinutes = positiveInt("PublicEvents.DurationMinutes", 30);
        publicEventIntervalMinutes = positiveInt("PublicEvents.IntervalMinutes", 180);
        publicEventFirstDelayMinutes = positiveInt("PublicEvents.FirstDelayMinutes", 5);
        publicEventsEnabled = plugin.getConfig().getBoolean("DungeonHero.Reputation.PublicEvents.Enabled", true);
    }

    private List<RankDefinition> loadRanks() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("DungeonHero.Reputation.Ranks");
        if (section == null) {
            return DEFAULT_RANKS;
        }
        List<RankDefinition> loaded = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection rank = section.getConfigurationSection(key);
            if (rank == null) {
                continue;
            }
            String name = rank.getString("Name", key);
            long threshold = Math.max(0, rank.getLong("Required", 0));
            loaded.add(new RankDefinition(name, threshold));
        }
        loaded.sort(Comparator.comparingLong(RankDefinition::requiredReputation));
        return loaded.isEmpty() ? DEFAULT_RANKS : List.copyOf(loaded);
    }

    private Set<String> configuredIds(String key, Set<String> defaults) {
        List<String> configured = plugin.getConfig().getStringList("DungeonHero.Reputation.MythicMobIds." + key);
        if (configured.isEmpty()) {
            return defaults;
        }
        return configured.stream().map(this::normalizeId).filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private int positiveInt(String path, int fallback) {
        return Math.max(1, plugin.getConfig().getInt("DungeonHero.Reputation." + path, fallback));
    }

    private EncounterKind classifyMythic(String internalName) {
        String id = normalizeId(internalName);
        if (rareBossIds.contains(id)) {
            return EncounterKind.RARE_BOSS;
        }
        if (minibossIds.contains(id)) {
            return EncounterKind.MINIBOSS;
        }
        if (eliteIds.contains(id)) {
            return EncounterKind.ELITE;
        }
        if (variantIds.contains(id)) {
            return EncounterKind.MYTHIC;
        }
        if (trackDungeonHeroPrefixes && (id.startsWith("dh_") || id.startsWith("dw_"))) {
            return EncounterKind.MYTHIC;
        }
        return null;
    }

    private boolean isActiveMythicMob(UUID entityId) {
        try {
            return io.lumine.mythic.bukkit.MythicBukkit.inst().getMobManager().isActiveMob(entityId);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private List<Player> participants(LivingEntity killer, Location location) {
        if (!(killer instanceof Player primary)) {
            return List.of();
        }
        PartyService.Party party = partyService.getParty(primary);
        if (party == null) {
            return List.of(primary);
        }
        return partyService.getMembers(party).stream()
                .filter(player -> isValidParticipant(player, location)).toList();
    }

    private boolean isValidParticipant(Player player, Location location) {
        return player.isOnline() && player.getWorld().equals(location.getWorld())
                && player.getLocation().distanceSquared(location) <= PARTICIPATION_RADIUS * PARTICIPATION_RADIUS;
    }

    private boolean isTrackedWorld(World world) {
        return world != null && (worlds.isEmpty() || worlds.contains(world.getName()));
    }

    private RankDefinition rankFor(long reputation) {
        RankDefinition current = ranks.getFirst();
        for (RankDefinition rank : ranks) {
            if (reputation >= rank.requiredReputation()) {
                current = rank;
            }
        }
        return current;
    }

    private void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    private synchronized void save() {
        if (data == null || !dirty) {
            return;
        }
        try {
            data.save(storageFile);
            dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().severe("Unable to save Dungeon Reputation: " + exception.getMessage());
        }
    }

    private void ensureDataDirectory() {
        File directory = plugin.getDataFolder();
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().severe("Unable to create DungeonHero data directory.");
        }
    }

    private String playerPath(UUID playerId) {
        return PLAYER_PATH + playerId;
    }

    private String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    private String bossName(String bossId) {
        return switch (bossId.toLowerCase(Locale.ROOT)) {
            case "dh_verdantsovereign" -> "the Verdant Sovereign";
            case "dh_sandstormtyrant" -> "the Sandstorm Tyrant";
            case "dh_miresovereign" -> "the Mire Sovereign";
            case "dh_wintercolossus" -> "the Winter Colossus";
            case "dh_overgrowntitan" -> "the Overgrown Titan";
            case "dh_abyssalemperor" -> "the Abyssal Emperor";
            case "dh_paleheartreaper" -> "the Paleheart Reaper";
            case "dh_deepstoneoverlord" -> "the Deepstone Overlord";
            default -> bossId;
        };
    }

    private NamedTextColor rankColor(RankDefinition rank) {
        return switch (rank.name().toLowerCase(Locale.ROOT)) {
            case "legendary" -> NamedTextColor.LIGHT_PURPLE;
            case "renowned" -> NamedTextColor.GOLD;
            case "notorious" -> NamedTextColor.RED;
            case "dangerous" -> NamedTextColor.YELLOW;
            case "recognized" -> NamedTextColor.AQUA;
            default -> NamedTextColor.GRAY;
        };
    }

    private void senderLine(Player player, String text, NamedTextColor color) {
        player.sendMessage(Component.text(text, color));
    }

    private String format(long amount) {
        return NumberFormat.getIntegerInstance(Locale.ROOT).format(Math.max(0, amount));
    }

    private String formatDuration(long millis) {
        long seconds = Math.max(0, millis / 1_000L);
        long hours = seconds / 3_600L;
        long minutes = (seconds % 3_600L) / 60L;
        long remainingSeconds = seconds % 60L;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m " + remainingSeconds + "s";
    }

    private static Set<String> ids(String... values) {
        return Set.of(values).stream().map(value -> value.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    public record RankDefinition(String name, long requiredReputation) {
    }

    public record ContractStatus(String biome, String type, int progress, int target, boolean complete) {
    }

    public record PublicEventStatus(boolean active, String biome, int progress, int goal,
                                    boolean bossDefeated, long nextEventMillis) {
    }

    private record Contract(BiomeCategory biome, String type, int target) {
    }

    private record ContributionEntry(String name, long contribution) {
    }

    private enum EncounterKind {
        VANILLA(1, "Hostile mob", false),
        MYTHIC(3, "Mythic variant", false),
        ELITE(10, "Elite", true),
        MINIBOSS(25, "Miniboss", true),
        RARE_BOSS(75, "Rare boss", true);

        private final int personalContribution;
        private final String displayName;
        private final boolean bossLike;

        EncounterKind(int personalContribution, String displayName, boolean bossLike) {
            this.personalContribution = personalContribution;
            this.displayName = displayName;
            this.bossLike = bossLike;
        }

        public int personalContribution() {
            return personalContribution;
        }

        public String displayName() {
            return displayName;
        }

        public boolean isBossLike() {
            return bossLike;
        }
    }

    private enum BiomeCategory {
        VERDANT("Verdant", "DH_VerdantSovereign"),
        DESERT("Desert", "DH_SandstormTyrant"),
        SWAMP("Swamp", "DH_MireSovereign"),
        FROST("Frost", "DH_WinterColossus"),
        JUNGLE("Jungle", "DH_OvergrownTitan"),
        OCEAN("Ocean", "DH_AbyssalEmperor"),
        PALE_GARDEN("Pale Garden", "DH_PaleheartReaper"),
        CAVERN("Cavern", "DH_DeepstoneOverlord"),
        UNKNOWN("Unknown", "");

        private static final List<BiomeCategory> EVENT_BIOMES = List.of(
                VERDANT, DESERT, SWAMP, FROST, JUNGLE, OCEAN, PALE_GARDEN, CAVERN);

        private final String displayName;
        private final String rareBossId;

        BiomeCategory(String displayName, String rareBossId) {
            this.displayName = displayName;
            this.rareBossId = rareBossId;
        }

        public String displayName() {
            return displayName;
        }

        public String rareBossId() {
            return rareBossId;
        }

        private static BiomeCategory fromDisplayName(String displayName) {
            for (BiomeCategory category : values()) {
                if (category.displayName.equalsIgnoreCase(displayName)) {
                    return category;
                }
            }
            return UNKNOWN;
        }

        private static BiomeCategory fromLocation(Location location) {
            if (location == null || location.getWorld() == null) {
                return UNKNOWN;
            }
            String biome = location.getBlock().getBiome().name().toUpperCase(Locale.ROOT);
            if (biome.contains("PALE_GARDEN")) {
                return PALE_GARDEN;
            }
            if (biome.contains("DEEP_DARK") || biome.contains("CAVE")) {
                return CAVERN;
            }
            if (biome.contains("OCEAN") || biome.contains("RIVER")) {
                return OCEAN;
            }
            if (biome.contains("SWAMP")) {
                return SWAMP;
            }
            if (biome.contains("DESERT") || biome.contains("BADLANDS")) {
                return DESERT;
            }
            if (biome.contains("JUNGLE")) {
                return JUNGLE;
            }
            if (biome.contains("SNOW") || biome.contains("ICE") || biome.contains("FROZEN")
                    || biome.contains("GROVE") || biome.contains("PEAKS")) {
                return FROST;
            }
            if (biome.contains("FOREST") || biome.contains("TAIGA") || biome.contains("MEADOW")
                    || biome.contains("CHERRY") || biome.contains("WINDSWEPT")) {
                return VERDANT;
            }
            return UNKNOWN;
        }

        private static BiomeCategory fromMobId(String mobId) {
            String id = mobId == null ? "" : mobId.toLowerCase(Locale.ROOT);
            if (id.contains("pale") || id.contains("manor")) return PALE_GARDEN;
            if (id.contains("deepstone") || id.contains("cavern") || id.contains("crypt")) return CAVERN;
            if (id.contains("abyssal") || id.contains("ocean") || id.contains("tide")) return OCEAN;
            if (id.contains("jungle") || id.contains("temple") || id.contains("overgrown")) return JUNGLE;
            if (id.contains("frost") || id.contains("glacier") || id.contains("winter")) return FROST;
            if (id.contains("bog") || id.contains("mire")) return SWAMP;
            if (id.contains("arid") || id.contains("dune") || id.contains("sand") || id.contains("badlands")) return DESERT;
            if (id.contains("verdant") || id.contains("grove") || id.contains("heartwood")) return VERDANT;
            return UNKNOWN;
        }
    }

}
