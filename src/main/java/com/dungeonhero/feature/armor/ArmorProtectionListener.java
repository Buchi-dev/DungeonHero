package com.dungeonhero.feature.armor;

import com.dungeonhero.config.DungeonHeroConfiguration;
import com.dungeonhero.feature.rank.DungeonRankService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Applies Hero Armor defense and Last Stand at the incoming-damage boundary. */
public final class ArmorProtectionListener implements Listener {

  private final HeroArmorService armorService;
  private final HeroArmorStorage storage;
  private final DungeonRankService rankService;
  private final ArmorSetBonusPolicy setBonusPolicy = new ArmorSetBonusPolicy();
  private final Map<UUID, Long> lastStandCooldowns = new HashMap<>();
  private ArmorProtectionPolicy protectionPolicy;
  private LastStandPolicy lastStandPolicy;
  private boolean enabled;
  private long cooldownMillis;

  public ArmorProtectionListener(
      JavaPlugin plugin,
      HeroArmorService armorService,
      DungeonRankService rankService,
      DungeonHeroConfiguration.Armor configuration) {
    this.armorService = armorService;
    storage = armorService.storage();
    this.rankService = rankService;
    reload(configuration);
  }

  public void reload(DungeonHeroConfiguration.Armor configuration) {
    enabled = configuration.enabled();
    protectionPolicy =
        new ArmorProtectionPolicy(
            configuration.levelReductionPerLevel(),
            configuration.maxLevelReduction(),
            configuration.fragmentReductionPerPoint(),
            configuration.maxFragmentReduction(),
            configuration.maxTotalReduction());
    lastStandPolicy = new LastStandPolicy(configuration.lastStandHealthThreshold());
    cooldownMillis = Math.max(1, configuration.lastStandCooldownSeconds()) * 1000L;
  }

  public long remainingCooldown(Player player) {
    if (player == null) return 0;
    return Math.max(
        0, lastStandCooldowns.getOrDefault(player.getUniqueId(), 0L) - System.currentTimeMillis());
  }

  @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
  public void onPlayerDamage(EntityDamageEvent event) {
    if (!enabled || event.isCancelled() || !(event.getEntity() instanceof Player player)) return;
    int pieces = armorService.equippedPieceCount(player);
    if (pieces < 1) return;
    HeroArmorState state =
        storage.loadOrDefault(player).withFragmentRank(rankService.getRank(player));
    double effective = armorService.effectiveBonus(state, rankService.getRank(player));
    double reduced = protectionPolicy.apply(event.getDamage(), state.level(), effective, pieces);
    if (pieces >= 4) {
      double maximumHealth = maximumHealth(player);
      LastStandPolicy.Decision decision =
          lastStandPolicy.evaluate(
              player.getHealth(),
              maximumHealth,
              reduced,
              setBonusPolicy.hasLastStand(pieces),
              remainingCooldown(player) == 0);
      if (decision.activate()) {
        event.setCancelled(true);
        lastStandCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + cooldownMillis);
        player.setHealth(Math.max(0, Math.min(maximumHealth, decision.resultingHealth())));
        player.sendActionBar(Component.text("Last Stand activated!", NamedTextColor.GOLD));
        return;
      }
    }
    event.setDamage(Math.max(0, reduced));
  }

  private double maximumHealth(Player player) {
    var attribute = player.getAttribute(Attribute.MAX_HEALTH);
    double value = attribute == null ? 20 : attribute.getValue();
    return Double.isFinite(value) ? Math.max(1, value) : 20;
  }
}
