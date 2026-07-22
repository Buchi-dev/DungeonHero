package com.dungeonhero.integration.mythicmobs;

import com.dungeonhero.feature.arena.ArenaSessionManager;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.INoTargetSkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.core.skills.SkillExecutor;
import io.lumine.mythic.core.skills.SkillMechanic;
import org.bukkit.entity.LivingEntity;

/** MythicMobs adapter for dungeonhero_arena{arena=...;radius=...;duration=...}. */
public final class MythicArenaSkillMechanic extends SkillMechanic implements INoTargetSkill {

  private final ArenaSessionManager sessions;
  private final String arenaId;
  private final double radius;
  private final long durationSeconds;

  public MythicArenaSkillMechanic(
      SkillExecutor manager, String line, MythicLineConfig config, ArenaSessionManager sessions) {
    super(manager, line, config);
    this.sessions = sessions;
    this.arenaId = config.getString("arena", "");
    this.radius = config.getDouble("radius", 0);
    this.durationSeconds = Math.max(0, config.getLong(new String[] {"duration"}, 0));
  }

  @Override
  public SkillResult cast(SkillMetadata metadata) {
    if (metadata == null
        || metadata.getCaster() == null
        || metadata.getCaster().getEntity() == null) {
      return SkillResult.INVALID_TARGET;
    }
    if (!(metadata.getCaster().getEntity().getBukkitEntity() instanceof LivingEntity boss)) {
      return SkillResult.INVALID_TARGET;
    }
    return sessions.startArena(boss, radius, arenaId, durationSeconds).isPresent()
        ? SkillResult.SUCCESS
        : SkillResult.ERROR;
  }
}
