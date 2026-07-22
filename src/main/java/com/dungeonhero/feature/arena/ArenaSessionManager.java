package com.dungeonhero.feature.arena;

import io.lumine.mythic.bukkit.MythicBukkit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/** Owns arena sessions and guarantees that all session cleanup is idempotent. */
public final class ArenaSessionManager {

  private final JavaPlugin plugin;
  private final ArenaEffectRenderer effectRenderer;
  private final Map<UUID, ArenaSession> sessions = new LinkedHashMap<>();
  private final Map<UUID, UUID> playerSessions = new HashMap<>();
  private final Set<UUID> internalTeleports = ConcurrentHashMap.newKeySet();
  private final Map<UUID, Location> pendingReturns = new HashMap<>();
  private ArenaConfiguration configuration;
  private BukkitTask tickTask;

  public ArenaSessionManager(JavaPlugin plugin, ArenaConfiguration configuration) {
    this.plugin = plugin;
    this.configuration = configuration;
    this.effectRenderer = new ArenaEffectRenderer();
    effectRenderer.reload(configuration);
  }

  public synchronized void reload(ArenaConfiguration configuration) {
    close();
    this.configuration = configuration;
    effectRenderer.reload(configuration);
  }

  public synchronized void start() {
    if (!configuration.enabled() || tickTask != null) return;
    tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 10L);
  }

  public synchronized void close() {
    if (tickTask != null) {
      tickTask.cancel();
      tickTask = null;
    }
    for (ArenaSession session : new ArrayList<>(sessions.values())) {
      finish(session, false, true);
    }
    sessions.clear();
    playerSessions.clear();
    pendingReturns.clear();
    internalTeleports.clear();
  }

  public synchronized Optional<ArenaSession> startArena(LivingEntity boss, double requestedRadius) {
    return startArena(
        boss, requestedRadius, configuration.arenaId(), configuration.durationSeconds());
  }

  public synchronized Optional<ArenaSession> startArena(
      LivingEntity boss, double requestedRadius, String requestedArenaId, long requestedDuration) {
    if (!configuration.enabled() || boss == null || !boss.isValid()) return Optional.empty();
    if (requestedArenaId != null
        && !requestedArenaId.isBlank()
        && !configuration.arenaId().equalsIgnoreCase(requestedArenaId.trim())) {
      plugin.getLogger().warning("Unknown Blood Arena id: " + requestedArenaId);
      return Optional.empty();
    }
    World arenaWorld = Bukkit.getWorld(configuration.arenaWorld());
    if (arenaWorld == null) {
      plugin
          .getLogger()
          .warning(
              "Blood Arena is enabled but arena world is not loaded: "
                  + configuration.arenaWorld());
      return Optional.empty();
    }
    if (sessions.values().stream()
        .anyMatch(
            session ->
                session.bossId().equals(boss.getUniqueId())
                    || session.arenaId().equalsIgnoreCase(configuration.arenaId()))) {
      return Optional.empty();
    }

    double radius = clampRadius(requestedRadius);
    List<Player> players =
        boss.getWorld().getNearbyPlayers(boss.getLocation(), radius).stream()
            .filter(Player::isOnline)
            .filter(player -> !player.getUniqueId().equals(boss.getUniqueId()))
            .filter(player -> !playerSessions.containsKey(player.getUniqueId()))
            .sorted(
                Comparator.comparingDouble(
                    player -> player.getLocation().distanceSquared(boss.getLocation())))
            .limit(configuration.maximumPlayers())
            .toList();
    if (players.isEmpty()) return Optional.empty();

    Map<UUID, Location> originalPlayers = new LinkedHashMap<>();
    players.forEach(
        player -> originalPlayers.put(player.getUniqueId(), player.getLocation().clone()));
    Location originalBoss = boss.getLocation().clone();
    long now = System.currentTimeMillis();
    ArenaSession session =
        new ArenaSession(
            configuration.arenaId(),
            boss.getUniqueId(),
            originalBoss,
            originalPlayers,
            now,
            now
                + (requestedDuration <= 0
                        ? configuration.durationSeconds()
                        : Math.min(configuration.durationSeconds(), requestedDuration))
                    * 1_000L);
    sessions.put(session.sessionId(), session);
    for (Player player : players) playerSessions.put(player.getUniqueId(), session.sessionId());

    Location bossSpawn = location(arenaWorld, configuration.bossSpawn());
    teleport(boss, bossSpawn);
    for (int index = 0; index < players.size(); index++) {
      Player player = players.get(index);
      teleport(player, playerSpawn(arenaWorld, index));
      player.sendMessage(
          Component.text(
              "Blood Arena: defeat the boss before the arena collapses.", NamedTextColor.RED));
    }
    effectRenderer.playEntrance(players);
    return Optional.of(session);
  }

  public synchronized Optional<ArenaSession> sessionForPlayer(UUID playerId) {
    UUID sessionId = playerSessions.get(playerId);
    return sessionId == null ? Optional.empty() : Optional.ofNullable(sessions.get(sessionId));
  }

  public synchronized Optional<ArenaSession> sessionForBoss(UUID bossId) {
    return sessions.values().stream()
        .filter(session -> session.bossId().equals(bossId))
        .findFirst();
  }

  public synchronized boolean consumeInternalTeleport(UUID playerId) {
    return internalTeleports.remove(playerId);
  }

  public synchronized void handlePlayerDeath(Player player) {
    sessionForPlayer(player.getUniqueId())
        .ifPresent(
            session -> {
              Location returnLocation = session.originalPlayerLocations().get(player.getUniqueId());
              if (returnLocation != null) pendingReturns.put(player.getUniqueId(), returnLocation);
              session.removeParticipant(player.getUniqueId());
              playerSessions.remove(player.getUniqueId());
              if (session.participantIds().isEmpty()) finish(session, false, false);
            });
  }

  public synchronized void handlePlayerRespawn(Player player, Consumer<Location> setLocation) {
    Location location = pendingReturns.remove(player.getUniqueId());
    if (location != null) setLocation.accept(location.clone());
  }

  public synchronized void handlePlayerQuit(Player player) {
    sessionForPlayer(player.getUniqueId())
        .ifPresent(session -> removePlayerFromSession(session, player.getUniqueId()));
    pendingReturns.remove(player.getUniqueId());
  }

  public synchronized boolean isOutsideArena(Player player, Location destination) {
    if (!configuration.preventTeleport() || destination == null || destination.getWorld() == null)
      return false;
    Optional<ArenaSession> session = sessionForPlayer(player.getUniqueId());
    if (session.isEmpty()) return false;
    World arenaWorld = Bukkit.getWorld(configuration.arenaWorld());
    Location center = location(arenaWorld, configuration.playerSpawn());
    return !destination.getWorld().equals(arenaWorld)
        || destination.distanceSquared(center)
            > configuration.targetRadius() * configuration.targetRadius();
  }

  public synchronized void returnToArena(Player player) {
    sessionForPlayer(player.getUniqueId())
        .ifPresent(
            ignored -> {
              World arenaWorld = Bukkit.getWorld(configuration.arenaWorld());
              teleport(player, location(arenaWorld, configuration.playerSpawn()));
            });
  }

  public synchronized void finishBoss(ArenaSession session) {
    finish(session, true, false);
  }

  public synchronized boolean escape(Player player) {
    if (player == null) return false;
    Optional<ArenaSession> session = sessionForPlayer(player.getUniqueId());
    if (session.isEmpty()) return false;
    removePlayerFromSession(session.get(), player.getUniqueId());
    return true;
  }

  public synchronized boolean endForPlayer(UUID playerId) {
    Optional<ArenaSession> session = sessionForPlayer(playerId);
    if (session.isEmpty()) return false;
    finish(session.get(), false, false);
    return true;
  }

  public synchronized boolean preventPearls() {
    return configuration.preventPearls();
  }

  private synchronized void tick() {
    long now = System.currentTimeMillis();
    for (ArenaSession session : new ArrayList<>(sessions.values())) {
      if (session.complete()) continue;
      World arenaWorld = Bukkit.getWorld(configuration.arenaWorld());
      Location center = location(arenaWorld, configuration.playerSpawn());
      effectRenderer.render(session, center, configuration.targetRadius(), now % 3_000L < 500L);
      runPhases(session, now);
      Entity boss = Bukkit.getEntity(session.bossId());
      if (boss == null || !boss.isValid() || boss.isDead()) {
        finish(session, true, false);
      } else if (session.isExpired(now)) {
        finish(session, configuration.rewardOnTimeout(), false);
      }
    }
  }

  private void runPhases(ArenaSession session, long now) {
    long elapsedSeconds = Math.max(0, (now - session.startedAt()) / 1_000L);
    while (session.currentPhase() < configuration.phases().size()) {
      ArenaConfiguration.Phase phase = configuration.phases().get(session.currentPhase());
      if (elapsedSeconds < phase.delaySeconds()) return;
      executePhase(session, phase);
      session.advancePhase();
    }
  }

  private void executePhase(ArenaSession session, ArenaConfiguration.Phase phase) {
    World world = Bukkit.getWorld(configuration.arenaWorld());
    Location center = location(world, configuration.playerSpawn());
    if (center == null) return;
    switch (phase.action()) {
      case "summon_reinforcements", "summon" -> summon(session, phase, center);
      case "apply_effect", "effect" -> applyEffect(session, phase);
      case "message" -> notifyParticipants(session, phase.effect());
      default -> plugin.getLogger().warning("Unknown Blood Arena phase action: " + phase.action());
    }
  }

  private void summon(ArenaSession session, ArenaConfiguration.Phase phase, Location center) {
    if (phase.mob().isBlank() || !Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) return;
    for (int index = 0; index < phase.amount(); index++) {
      try {
        Entity entity =
            MythicBukkit.inst()
                .getAPIHelper()
                .spawnMythicMob(phase.mob(), center.clone().add(index % 3 - 1, 0, index / 3));
        if (entity != null) session.trackTemporaryEffectEntity(entity.getUniqueId());
      } catch (Exception exception) {
        plugin
            .getLogger()
            .warning(
                "Unable to summon Blood Arena reinforcement '"
                    + phase.mob()
                    + "': "
                    + exception.getMessage());
      }
    }
  }

  private void applyEffect(ArenaSession session, ArenaConfiguration.Phase phase) {
    PotionEffectType type =
        PotionEffectType.getByName(phase.effect().toUpperCase(java.util.Locale.ROOT));
    if (type == null) return;
    forEachParticipant(
        session,
        player ->
            player.addPotionEffect(
                new PotionEffect(type, phase.durationSeconds() * 20, 0, false, true, true)));
  }

  private void finish(ArenaSession session, boolean reward, boolean shutdown) {
    if (!session.markComplete()) return;
    sessions.remove(session.sessionId());
    for (UUID playerId : session.participantIds()) {
      playerSessions.remove(playerId);
      Player player = Bukkit.getPlayer(playerId);
      Location original = session.originalPlayerLocations().get(playerId);
      if (configuration.returnPlayersAfterFight()
          && player != null
          && player.isOnline()
          && original != null) {
        teleport(player, original);
        player.sendMessage(
            Component.text(
                shutdown ? "Blood Arena closed safely." : "Blood Arena complete.",
                NamedTextColor.GREEN));
      }
    }
    for (UUID entityId : session.temporaryEffectEntities()) {
      Entity entity = Bukkit.getEntity(entityId);
      if (entity != null && entity.isValid()) entity.remove();
    }
    Entity boss = Bukkit.getEntity(session.bossId());
    if (boss != null
        && boss.isValid()
        && !boss.isDead()
        && session.originalBossLocation() != null) {
      boss.teleport(session.originalBossLocation());
    }
    if (reward) reward(session);
  }

  private void reward(ArenaSession session) {
    for (UUID playerId : session.originalPlayerLocations().keySet()) {
      Player player = Bukkit.getPlayer(playerId);
      if (player == null) continue;
      for (String configuredCommand : configuration.rewardCommands()) {
        String command =
            configuredCommand
                .replace("%player%", player.getName())
                .replace("%uuid%", playerId.toString())
                .replace("%arena%", session.arenaId())
                .replace("%session_id%", session.sessionId().toString());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
      }
    }
  }

  private void removePlayerFromSession(ArenaSession session, UUID playerId) {
    session.removeParticipant(playerId);
    playerSessions.remove(playerId);
    Player player = Bukkit.getPlayer(playerId);
    Location original = session.originalPlayerLocations().get(playerId);
    if (player != null && player.isOnline() && original != null) teleport(player, original);
    if (session.participantIds().isEmpty()) finish(session, false, false);
  }

  private void notifyParticipants(ArenaSession session, String message) {
    forEachParticipant(
        session, player -> player.sendMessage(Component.text(message, NamedTextColor.RED)));
  }

  private void forEachParticipant(ArenaSession session, Consumer<Player> action) {
    for (UUID playerId : session.participantIds()) {
      Player player = Bukkit.getPlayer(playerId);
      if (player != null && player.isOnline()) action.accept(player);
    }
  }

  private void teleport(Entity entity, Location location) {
    if (entity instanceof Player player) internalTeleports.add(player.getUniqueId());
    entity.teleport(location);
  }

  private Location playerSpawn(World world, int index) {
    Location spawn = location(world, configuration.playerSpawn());
    double angle = index * (Math.PI * 2 / Math.max(1, configuration.maximumPlayers()));
    return spawn.add(
        Math.cos(angle) * Math.min(4, configuration.targetRadius() / 4),
        0,
        Math.sin(angle) * Math.min(4, configuration.targetRadius() / 4));
  }

  private Location location(World world, ArenaConfiguration.LocationSpec spec) {
    return world == null
        ? null
        : new Location(world, spec.x(), spec.y(), spec.z(), spec.yaw(), spec.pitch());
  }

  private double clampRadius(double requestedRadius) {
    return !Double.isFinite(requestedRadius) || requestedRadius <= 0
        ? configuration.targetRadius()
        : Math.min(requestedRadius, configuration.targetRadius());
  }
}
