package com.dungeonhero.feature.arena;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;

/** Mutable state for one isolated arena run. All mutations occur on the server thread. */
public final class ArenaSession {

  private final UUID sessionId;
  private final String arenaId;
  private final UUID bossId;
  private final Location originalBossLocation;
  private final Map<UUID, Location> originalPlayerLocations;
  private final Set<UUID> participantIds;
  private final long startedAt;
  private final long expiresAt;
  private final Set<UUID> temporaryEffectEntities = new LinkedHashSet<>();
  private int currentPhase;
  private boolean complete;

  public ArenaSession(
      String arenaId,
      UUID bossId,
      Location originalBossLocation,
      Map<UUID, Location> originalPlayerLocations,
      long startedAt,
      long expiresAt) {
    this.sessionId = UUID.randomUUID();
    this.arenaId = arenaId;
    this.bossId = bossId;
    this.originalBossLocation = cloneLocation(originalBossLocation);
    this.originalPlayerLocations = cloneLocations(originalPlayerLocations);
    this.participantIds = new LinkedHashSet<>(originalPlayerLocations.keySet());
    this.startedAt = startedAt;
    this.expiresAt = expiresAt;
  }

  public UUID sessionId() {
    return sessionId;
  }

  public String arenaId() {
    return arenaId;
  }

  public UUID bossId() {
    return bossId;
  }

  public Location originalBossLocation() {
    return cloneLocation(originalBossLocation);
  }

  public Map<UUID, Location> originalPlayerLocations() {
    return Collections.unmodifiableMap(cloneLocations(originalPlayerLocations));
  }

  public Set<UUID> participantIds() {
    return Collections.unmodifiableSet(new LinkedHashSet<>(participantIds));
  }

  public long startedAt() {
    return startedAt;
  }

  public long expiresAt() {
    return expiresAt;
  }

  public int currentPhase() {
    return currentPhase;
  }

  public void advancePhase() {
    currentPhase++;
  }

  public Set<UUID> temporaryEffectEntities() {
    return Collections.unmodifiableSet(temporaryEffectEntities);
  }

  public void trackTemporaryEffectEntity(UUID entityId) {
    if (entityId != null) temporaryEffectEntities.add(entityId);
  }

  public void removeParticipant(UUID playerId) {
    participantIds.remove(playerId);
  }

  public boolean contains(UUID playerId) {
    return participantIds.contains(playerId);
  }

  public boolean isExpired(long now) {
    return now >= expiresAt;
  }

  public boolean complete() {
    return complete;
  }

  public boolean markComplete() {
    if (complete) return false;
    complete = true;
    return true;
  }

  private static Location cloneLocation(Location location) {
    return location == null ? null : location.clone();
  }

  private static Map<UUID, Location> cloneLocations(Map<UUID, Location> locations) {
    Map<UUID, Location> copies = new LinkedHashMap<>();
    if (locations != null) {
      locations.forEach((id, location) -> copies.put(id, cloneLocation(location)));
    }
    return copies;
  }
}
