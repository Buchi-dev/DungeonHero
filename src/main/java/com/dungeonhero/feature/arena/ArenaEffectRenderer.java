package com.dungeonhero.feature.arena;

import java.util.Locale;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Renders non-persistent arena presentation without changing blocks in any world. */
public final class ArenaEffectRenderer {

  private Particle boundaryParticle = Particle.DUST;
  private Sound entranceSound = Sound.ENTITY_WITHER_SPAWN;
  private Sound combatSound = Sound.ENTITY_ENDER_DRAGON_GROWL;

  public void reload(ArenaConfiguration configuration) {
    boundaryParticle = parseParticle(configuration.boundaryParticle(), Particle.DUST);
    entranceSound = parseSound(configuration.entranceSound(), Sound.ENTITY_WITHER_SPAWN);
    combatSound = parseSound(configuration.combatSound(), Sound.ENTITY_ENDER_DRAGON_GROWL);
  }

  public void playEntrance(Iterable<Player> players) {
    players.forEach(player -> player.playSound(player.getLocation(), entranceSound, 1.0F, 0.7F));
  }

  public void render(ArenaSession session, Location center, double radius, boolean sound) {
    if (center == null || center.getWorld() == null) return;
    World world = center.getWorld();
    for (int index = 0; index < 48; index++) {
      double angle = (Math.PI * 2 * index) / 48;
      Location point = center.clone().add(Math.cos(angle) * radius, 0.15, Math.sin(angle) * radius);
      if (boundaryParticle == Particle.DUST) {
        world.spawnParticle(boundaryParticle, point, 1, new Particle.DustOptions(Color.RED, 1.4F));
      } else {
        world.spawnParticle(boundaryParticle, point, 1, 0, 0, 0, 0);
      }
    }
    if (sound) {
      world.playSound(center, combatSound, 0.55F, 0.8F);
    }
  }

  private static Particle parseParticle(String value, Particle fallback) {
    try {
      String normalized = value.trim().toUpperCase(Locale.ROOT);
      return normalized.equals("REDSTONE") ? Particle.DUST : Particle.valueOf(normalized);
    } catch (RuntimeException ignored) {
      return fallback;
    }
  }

  private static Sound parseSound(String value, Sound fallback) {
    try {
      return Sound.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (RuntimeException ignored) {
      return fallback;
    }
  }
}
