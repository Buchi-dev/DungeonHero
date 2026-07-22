package com.dungeonhero.integration.mythicmobs;

import com.dungeonhero.config.DungeonHeroConfiguration;
import com.dungeonhero.feature.rank.DungeonRankService;
import com.dungeonhero.feature.sword.HeroItemService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Applies the fragment cap to the server damage event, including malformed or duplicated items. */
public final class HeroDamageProtectionListener implements Listener {

  private final HeroItemService heroItemService;
  private final DungeonRankService dungeonRankService;
  private double criticalDamageMultiplier;

  public HeroDamageProtectionListener(
      JavaPlugin plugin, HeroItemService heroItemService, DungeonRankService dungeonRankService) {
    this(
        plugin,
        heroItemService,
        dungeonRankService,
        DungeonHeroConfiguration.load(plugin).damageProtection());
  }

  public HeroDamageProtectionListener(
      JavaPlugin plugin,
      HeroItemService heroItemService,
      DungeonRankService dungeonRankService,
      DungeonHeroConfiguration.DamageProtection configuration) {
    this.heroItemService = heroItemService;
    this.dungeonRankService = dungeonRankService;
    reload(configuration);
  }

  public void reload(DungeonHeroConfiguration.DamageProtection configuration) {
    criticalDamageMultiplier = configuration.criticalDamageMultiplier();
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void onPlayerDamage(EntityDamageByEntityEvent event) {
    if (!(event.getDamager() instanceof Player player)) {
      return;
    }

    // Only the currently held main-hand sword is authoritative. Off-hand swords and
    // duplicate inventory metadata never contribute to this event.
    ItemStack sword = player.getInventory().getItemInMainHand();
    if (!heroItemService.isHeroSword(sword)) {
      return;
    }

    int rank = dungeonRankService.getRank(player);
    double total = heroItemService.getStoredDamageBonus(sword);
    double active = heroItemService.getEffectiveDamageBonus(sword, rank);
    double overflow = Math.max(0, total - active);
    ItemStack normalized = heroItemService.withFragmentRank(sword, rank);
    player.getInventory().setItemInMainHand(normalized);

    var attackAttribute = player.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
    double sanitizedNormalDamage =
        attackAttribute == null ? 1 : Math.max(1, Math.min(100000, attackAttribute.getValue()));
    double criticalMultiplier = Math.max(1, Math.min(4, criticalDamageMultiplier));
    event.setDamage(Math.min(event.getDamage(), sanitizedNormalDamage * criticalMultiplier));
    if (overflow > 0 && Double.isFinite(overflow)) {
      event.setDamage(Math.max(0, event.getDamage() - overflow));
    }
  }
}
