package com.dungeonhero.feature.arena;

import com.dungeonhero.integration.mythicmobs.MythicArenaSkillMechanic;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.core.skills.mechanics.CustomMechanic;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Enforces the player-only safety boundary and connects lifecycle events to session cleanup. */
public final class ArenaBoundaryListener implements Listener {

  private final ArenaSessionManager sessions;

  public ArenaBoundaryListener(ArenaSessionManager sessions) {
    this.sessions = sessions;
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onMove(PlayerMoveEvent event) {
    if (event.getTo() == null || !sessions.isOutsideArena(event.getPlayer(), event.getTo())) return;
    sessions.returnToArena(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onTeleport(PlayerTeleportEvent event) {
    if (sessions.consumeInternalTeleport(event.getPlayer().getUniqueId())) return;
    if (sessions.isOutsideArena(event.getPlayer(), event.getTo())) event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onPortal(PlayerPortalEvent event) {
    if (sessions.sessionForPlayer(event.getPlayer().getUniqueId()).isPresent())
      event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onPearl(ProjectileLaunchEvent event) {
    if (!(event.getEntity() instanceof EnderPearl pearl)
        || !(pearl.getShooter() instanceof Player player)) return;
    if (sessions.preventPearls() && sessions.sessionForPlayer(player.getUniqueId()).isPresent()) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onDeath(PlayerDeathEvent event) {
    sessions.handlePlayerDeath(event.getEntity());
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onRespawn(PlayerRespawnEvent event) {
    sessions.handlePlayerRespawn(event.getPlayer(), event::setRespawnLocation);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuit(PlayerQuitEvent event) {
    sessions.handlePlayerQuit(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onEntityDeath(EntityDeathEvent event) {
    if (event.getEntity() instanceof LivingEntity entity)
      sessions
          .sessionForBoss(entity.getUniqueId())
          .ifPresent(session -> sessions.finishBoss(session));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onMythicDeath(MythicMobDeathEvent event) {
    if (event.getEntity() instanceof LivingEntity entity)
      sessions
          .sessionForBoss(entity.getUniqueId())
          .ifPresent(session -> sessions.finishBoss(session));
  }

  @EventHandler
  public void onMechanicLoad(MythicMechanicLoadEvent event) {
    if (event.getMechanicName().equalsIgnoreCase("dungeonhero_arena")
        || event.getMechanicName().equalsIgnoreCase("blood_arena")) {
      CustomMechanic container = event.getContainer();
      event.register(
          new MythicArenaSkillMechanic(
              container.getManager(), event.getConfig().getLine(), event.getConfig(), sessions));
    }
  }
}
