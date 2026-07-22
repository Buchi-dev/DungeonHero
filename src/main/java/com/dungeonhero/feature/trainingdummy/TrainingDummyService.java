package com.dungeonhero.feature.trainingdummy;

import com.dungeonhero.config.DungeonHeroConfiguration;
import com.dungeonhero.feature.sword.HeroItemService;
import com.dungeonhero.feature.sword.SwordTier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class TrainingDummyService implements Listener {

  private final JavaPlugin plugin;
  private final HeroItemService heroItemService;
  private final NamespacedKey dummyKey;
  private final NamespacedKey displayKey;
  private final java.util.Map<UUID, HitWindow> hitWindows = new java.util.HashMap<>();

  private boolean enabled;
  private double dummyHealth;
  private double searchRadius;
  private double spawnDistance;
  private long damageWindowMillis;
  private boolean hologramEnabled;
  private double hologramHeight;

  public TrainingDummyService(JavaPlugin plugin, HeroItemService heroItemService) {
    this(plugin, heroItemService, DungeonHeroConfiguration.load(plugin).trainingDummy());
  }

  public TrainingDummyService(
      JavaPlugin plugin,
      HeroItemService heroItemService,
      DungeonHeroConfiguration.TrainingDummy configuration) {
    this.plugin = plugin;
    this.heroItemService = heroItemService;
    this.dummyKey = new NamespacedKey(plugin, "training_dummy");
    this.displayKey = new NamespacedKey(plugin, "training_dummy_display");
    reload(configuration);
  }

  public void reload() {
    reload(DungeonHeroConfiguration.load(plugin).trainingDummy());
  }

  public void reload(DungeonHeroConfiguration.TrainingDummy configuration) {
    enabled = configuration.enabled();
    dummyHealth = configuration.health();
    searchRadius = configuration.searchRadius();
    spawnDistance = configuration.spawnDistance();
    damageWindowMillis = configuration.damageWindowSeconds() * 1000L;
    hologramEnabled = configuration.hologramEnabled();
    hologramHeight = configuration.hologramHeight();
    hitWindows.clear();
  }

  public void open(Player player) {
    if (!enabled) {
      player.sendMessage(Component.text("Training Dummies are disabled.", NamedTextColor.YELLOW));
      return;
    }

    LivingEntity dummy = findNearestDummy(player);
    if (dummy == null) {
      dummy = spawnDummy(player);
      player.sendMessage(
          Component.text(
              "Training Dummy created. Strike it with your Hero Sword.", NamedTextColor.GREEN));
    } else {
      ensureDisplay(dummy);
      player.sendMessage(
          Component.text(
              "Training Dummy found. Strike it with your Hero Sword.", NamedTextColor.GREEN));
    }
    player.sendMessage(
        Component.text("Use /dh dummy stats to view your recent damage.", NamedTextColor.GRAY));
  }

  public void remove(Player player) {
    int removed = removeInWorld(player.getWorld());
    hitWindows.clear();
    player.sendMessage(
        Component.text(
            removed == 0
                ? "No Training Dummy found in this world."
                : "Removed " + removed + " Training Dummy.",
            removed == 0 ? NamedTextColor.YELLOW : NamedTextColor.GREEN));
  }

  /** Removes every DungeonHero-owned dummy from loaded worlds. */
  public int removeAll() {
    int removed = 0;
    for (World world : Bukkit.getWorlds()) {
      removed += removeInWorld(world);
    }
    hitWindows.clear();
    return removed;
  }

  public void sendStats(Player player) {
    HitWindow window = hitWindows.get(player.getUniqueId());
    ItemStack sword = heroItemService.findStrongestHeroSword(player);
    if (window == null || window.hits.isEmpty()) {
      player.sendMessage(
          Component.text("No Training Dummy hits recorded yet.", NamedTextColor.YELLOW));
      return;
    }

    window.trim(System.currentTimeMillis(), damageWindowMillis);
    player.sendMessage(
        Component.text(
            "Training Dummy | Hits: "
                + window.hits.size()
                + " | Total Damage: "
                + format(window.lifetimeDamage)
                + " | DPS: "
                + format(window.dps(System.currentTimeMillis())),
            NamedTextColor.AQUA));
    if (heroItemService.isHeroSword(sword)) {
      SwordTier tier = heroItemService.getSwordTier(sword);
      player.sendMessage(
          Component.text(
              "Sword: "
                  + tier.displayName()
                  + " Lv. "
                  + heroItemService.getSwordLevel(sword)
                  + " | Damage Bonus: +"
                  + format(heroItemService.getDamageBonus(sword))
                  + " | Prestige: "
                  + heroItemService.getSwordPrestige(sword),
              tier.color()));
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void onDummyDamage(EntityDamageEvent event) {
    if (!enabled || !isDummy(event.getEntity()) || event.isCancelled()) {
      return;
    }

    if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
      event.setCancelled(true);
      return;
    }

    Player player = resolvePlayer(byEntity.getDamager());
    if (player == null) {
      event.setCancelled(true);
      return;
    }

    double damage = Math.max(0, event.getFinalDamage());
    event.setCancelled(true);
    LivingEntity dummy = (LivingEntity) event.getEntity();
    resetHealth(dummy);
    recordHit(player, damage);
    showHit(player, dummy, damage);
  }

  private LivingEntity spawnDummy(Player player) {
    Location location = player.getLocation().clone();
    org.bukkit.util.Vector direction = location.getDirection().setY(0).normalize();
    location.add(direction.multiply(spawnDistance));
    location.setYaw(player.getLocation().getYaw() + 180);

    Zombie dummy = (Zombie) player.getWorld().spawnEntity(location, EntityType.ZOMBIE);
    dummy.getPersistentDataContainer().set(dummyKey, PersistentDataType.BYTE, (byte) 1);
    dummy.setAdult();
    dummy.setAI(false);
    dummy.setSilent(true);
    dummy.setPersistent(true);
    dummy.setRemoveWhenFarAway(false);
    dummy.setCanPickupItems(false);
    dummy.setCollidable(false);
    dummy.setCustomName("§6§lTraining Dummy");
    dummy.setCustomNameVisible(true);
    dummy.setMaximumNoDamageTicks(0);
    setMaxHealth(dummy);
    ensureDisplay(dummy);
    return dummy;
  }

  private LivingEntity findNearestDummy(Player player) {
    double radiusSquared = searchRadius * searchRadius;
    LivingEntity nearest = null;
    double nearestDistance = Double.MAX_VALUE;
    for (Entity entity : player.getWorld().getEntities()) {
      if (!isDummy(entity) || !(entity instanceof LivingEntity living)) {
        continue;
      }
      double distance = entity.getLocation().distanceSquared(player.getLocation());
      if (distance <= radiusSquared && distance < nearestDistance) {
        nearest = living;
        nearestDistance = distance;
      }
    }
    return nearest;
  }

  private boolean isDummy(Entity entity) {
    return entity instanceof LivingEntity living
        && living.getPersistentDataContainer().has(dummyKey, PersistentDataType.BYTE);
  }

  private int removeInWorld(World world) {
    int removed = 0;
    for (Entity entity : new ArrayList<>(world.getEntities())) {
      if (isDummy(entity)) {
        removeDisplay(entity);
        entity.remove();
        removed++;
      }
    }
    return removed;
  }

  private void setMaxHealth(LivingEntity dummy) {
    var attribute = dummy.getAttribute(Attribute.MAX_HEALTH);
    if (attribute != null) {
      attribute.setBaseValue(dummyHealth);
      dummy.setHealth(dummyHealth);
    }
  }

  private void resetHealth(LivingEntity dummy) {
    var attribute = dummy.getAttribute(Attribute.MAX_HEALTH);
    if (attribute != null) {
      dummy.setHealth(attribute.getValue());
    }
  }

  private Player resolvePlayer(Entity damager) {
    if (damager instanceof Player player) {
      return player;
    }
    if (damager instanceof Projectile projectile
        && projectile.getShooter() instanceof Player player) {
      return player;
    }
    return null;
  }

  private void recordHit(Player player, double damage) {
    long now = System.currentTimeMillis();
    HitWindow window = hitWindows.computeIfAbsent(player.getUniqueId(), ignored -> new HitWindow());
    window.trim(now, damageWindowMillis);
    window.hits.addLast(new Hit(now, damage));
    window.lifetimeDamage += damage;
  }

  private void showHit(Player player, LivingEntity dummy, double damage) {
    long now = System.currentTimeMillis();
    HitWindow window = hitWindows.get(player.getUniqueId());
    player.sendActionBar(
        Component.text(
            "Dummy Hit: "
                + format(damage)
                + "  |  DPS: "
                + format(window.dps(now))
                + "  |  Total Damage: "
                + format(window.lifetimeDamage),
            NamedTextColor.GREEN));
    player.playSound(player.getLocation(), "entity.player.attack.crit", 0.7f, 1.2f);
    dummy
        .getWorld()
        .spawnParticle(
            Particle.DAMAGE_INDICATOR, dummy.getLocation().add(0, 1.1, 0), 6, 0.2, 0.25, 0.2, 0.05);
    updateDisplay(dummy, player, damage, window, now);
  }

  private TextDisplay ensureDisplay(LivingEntity dummy) {
    if (!hologramEnabled) {
      return null;
    }
    for (Entity entity : dummy.getWorld().getNearbyEntities(dummy.getLocation(), 2, 3, 2)) {
      if (entity instanceof TextDisplay display
          && display.getPersistentDataContainer().has(displayKey, PersistentDataType.BYTE)) {
        return display;
      }
    }
    Location location = dummy.getLocation().clone().add(0, hologramHeight, 0);
    return dummy
        .getWorld()
        .spawn(
            location,
            TextDisplay.class,
            display -> {
              display
                  .getPersistentDataContainer()
                  .set(displayKey, PersistentDataType.BYTE, (byte) 1);
              display.setText(
                  "§6§lTraining Dummy\n§aDPS: §f0.00\n§bTotal Damage: §f0.00\n§7Last Hit: §f0.00");
              display.setBillboard(TextDisplay.Billboard.CENTER);
              display.setSeeThrough(true);
              display.setShadowed(true);
              display.setPersistent(true);
            });
  }

  private void updateDisplay(
      LivingEntity dummy, Player player, double damage, HitWindow window, long now) {
    TextDisplay display = ensureDisplay(dummy);
    if (display == null) {
      return;
    }
    display.setText(
        "§6§lTraining Dummy\n"
            + "§e"
            + player.getName()
            + "\n"
            + "§aDPS: §f"
            + format(window.dps(now))
            + "\n"
            + "§bTotal Damage: §f"
            + format(window.lifetimeDamage)
            + "\n"
            + "§7Last Hit: §f"
            + format(damage));
  }

  private void removeDisplay(Entity dummy) {
    for (Entity entity : dummy.getWorld().getNearbyEntities(dummy.getLocation(), 2, 3, 2)) {
      if (entity instanceof TextDisplay display
          && display.getPersistentDataContainer().has(displayKey, PersistentDataType.BYTE)) {
        display.remove();
      }
    }
  }

  private String format(double value) {
    return String.format(Locale.ROOT, "%.2f", value);
  }

  private static final class HitWindow {
    private final Deque<Hit> hits = new ArrayDeque<>();
    private double lifetimeDamage;

    private void trim(long now, long windowMillis) {
      while (!hits.isEmpty() && now - hits.peekFirst().timestamp > windowMillis) {
        hits.removeFirst();
      }
    }

    private double windowDamage() {
      return hits.stream().mapToDouble(Hit::damage).sum();
    }

    private double dps(long now) {
      if (hits.isEmpty()) {
        return 0;
      }
      double seconds = Math.max(1, (now - hits.peekFirst().timestamp) / 1000.0);
      return windowDamage() / seconds;
    }
  }

  private record Hit(long timestamp, double damage) {}
}
