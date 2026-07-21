package com.dungeonhero;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.UUID;

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
        this.plugin = plugin;
        this.heroItemService = heroItemService;
        this.dummyKey = new NamespacedKey(plugin, "training_dummy");
        this.displayKey = new NamespacedKey(plugin, "training_dummy_display");
        reload();
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("DungeonHero.TargetDummy.Enabled", true);
        dummyHealth = Math.max(1, plugin.getConfig().getDouble("DungeonHero.TargetDummy.Health", 1000));
        searchRadius = Math.max(4, plugin.getConfig().getDouble("DungeonHero.TargetDummy.SearchRadius", 48));
        spawnDistance = Math.max(2, plugin.getConfig().getDouble("DungeonHero.TargetDummy.SpawnDistance", 3));
        long windowSeconds = Math.max(1, plugin.getConfig().getLong(
                "DungeonHero.TargetDummy.DamageWindowSeconds", 5));
        damageWindowMillis = windowSeconds * 1000L;
        hologramEnabled = plugin.getConfig().getBoolean("DungeonHero.TargetDummy.Hologram.Enabled", true);
        hologramHeight = Math.max(1.5, plugin.getConfig().getDouble(
                "DungeonHero.TargetDummy.Hologram.Height", 2.6));
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
            player.sendMessage(Component.text("Training Dummy created. Strike it with your Hero Sword.",
                    NamedTextColor.GREEN));
        } else {
            ensureDisplay(dummy);
            player.sendMessage(Component.text("Training Dummy found. Strike it with your Hero Sword.",
                    NamedTextColor.GREEN));
        }
        player.sendMessage(Component.text("Use /dh dummy stats to view your recent damage.", NamedTextColor.GRAY));
    }

    public void remove(Player player) {
        int removed = 0;
        for (Entity entity : new ArrayList<>(player.getWorld().getEntities())) {
            if (isDummy(entity)) {
                removeDisplay(entity);
                entity.remove();
                removed++;
            }
        }
        hitWindows.clear();
        player.sendMessage(Component.text(
                removed == 0 ? "No Training Dummy found in this world." : "Removed " + removed + " Training Dummy.",
                removed == 0 ? NamedTextColor.YELLOW : NamedTextColor.GREEN));
    }

    public void sendStats(Player player) {
        HitWindow window = hitWindows.get(player.getUniqueId());
        ItemStack sword = heroItemService.findStrongestHeroSword(player);
        if (window == null || window.hits.isEmpty()) {
            player.sendMessage(Component.text("No Training Dummy hits recorded yet.", NamedTextColor.YELLOW));
            return;
        }

        window.trim(System.currentTimeMillis(), damageWindowMillis);
        player.sendMessage(Component.text("Training Dummy | Hits: " + window.hits.size()
                + " | Total Damage: " + format(window.lifetimeDamage)
                + " | DPS: " + format(window.dps(System.currentTimeMillis())), NamedTextColor.AQUA));
        if (heroItemService.isHeroSword(sword)) {
            SwordTier tier = heroItemService.getSwordTier(sword);
            player.sendMessage(Component.text("Sword: " + tier.displayName() + " Lv. "
                    + heroItemService.getSwordLevel(sword)
                    + " | Damage Bonus: +" + format(heroItemService.getDamageBonus(sword))
                    + " | Prestige: " + heroItemService.getSwordPrestige(sword), tier.color()));
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
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
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
        player.sendActionBar(Component.text("Dummy Hit: " + format(damage)
                + "  |  DPS: " + format(window.dps(now))
                + "  |  Total Damage: " + format(window.lifetimeDamage), NamedTextColor.GREEN));
        player.playSound(player.getLocation(), "entity.player.attack.crit", 0.7f, 1.2f);
        dummy.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR,
                dummy.getLocation().add(0, 1.1, 0), 6, 0.2, 0.25, 0.2, 0.05);
        updateDisplay(dummy, player, damage, window, now);
    }

    private TextDisplay ensureDisplay(LivingEntity dummy) {
        if (!hologramEnabled) {
            return null;
        }
        for (Entity entity : dummy.getWorld().getNearbyEntities(dummy.getLocation(), 2, 3, 2)) {
            if (entity instanceof TextDisplay display && display.getPersistentDataContainer()
                    .has(displayKey, PersistentDataType.BYTE)) {
                return display;
            }
        }
        Location location = dummy.getLocation().clone().add(0, hologramHeight, 0);
        return dummy.getWorld().spawn(location, TextDisplay.class, display -> {
            display.getPersistentDataContainer().set(displayKey, PersistentDataType.BYTE, (byte) 1);
            display.setText("§6§lTraining Dummy\n§aDPS: §f0.00\n§bTotal Damage: §f0.00\n§7Last Hit: §f0.00");
            display.setBillboard(TextDisplay.Billboard.CENTER);
            display.setSeeThrough(true);
            display.setShadowed(true);
            display.setPersistent(true);
        });
    }

    private void updateDisplay(LivingEntity dummy, Player player, double damage, HitWindow window, long now) {
        TextDisplay display = ensureDisplay(dummy);
        if (display == null) {
            return;
        }
        display.setText("§6§lTraining Dummy\n"
                + "§e" + player.getName() + "\n"
                + "§aDPS: §f" + format(window.dps(now)) + "\n"
                + "§bTotal Damage: §f" + format(window.lifetimeDamage) + "\n"
                + "§7Last Hit: §f" + format(damage));
    }

    private void removeDisplay(Entity dummy) {
        for (Entity entity : dummy.getWorld().getNearbyEntities(dummy.getLocation(), 2, 3, 2)) {
            if (entity instanceof TextDisplay display && display.getPersistentDataContainer()
                    .has(displayKey, PersistentDataType.BYTE)) {
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

    private record Hit(long timestamp, double damage) {
    }
}
