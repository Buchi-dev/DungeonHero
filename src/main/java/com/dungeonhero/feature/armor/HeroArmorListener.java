package com.dungeonhero.feature.armor;

import com.dungeonhero.feature.rank.DungeonRankService;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Restores the four shared Hero Armor pieces without touching normal inventory items. */
public final class HeroArmorListener implements Listener {

  private final JavaPlugin plugin;
  private final HeroArmorService armorService;
  private final HeroArmorStorage storage;
  private final DungeonRankService rankService;
  private final Map<UUID, HeroArmorState> statesToRestore = new java.util.HashMap<>();

  public HeroArmorListener(
      JavaPlugin plugin, HeroArmorService armorService, DungeonRankService rankService) {
    this.plugin = plugin;
    this.armorService = armorService;
    storage = armorService.storage();
    this.rankService = rankService;
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    ensureArmor(event.getPlayer(), storage.load(event.getPlayer()));
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    HeroArmorState state = storage.load(event.getPlayer());
    if (state != null) storage.save(event.getPlayer(), state);
  }

  @EventHandler
  public void onDrop(PlayerDropItemEvent event) {
    if (armorService.isHeroArmor(event.getItemDrop().getItemStack())) {
      event.setCancelled(true);
      event.getPlayer().sendMessage(Component.text("Hero Armor cannot be dropped."));
    }
  }

  @EventHandler
  public void onDeath(PlayerDeathEvent event) {
    Player player = event.getEntity();
    HeroArmorState state = storage.load(player);
    if (state == null) state = findArmorState(player);
    if (state != null) storage.save(player, state);
    event.getDrops().removeIf(armorService::isHeroArmor);
    if (state != null) statesToRestore.put(player.getUniqueId(), state);
  }

  @EventHandler
  public void onRespawn(PlayerRespawnEvent event) {
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () ->
                ensureArmor(
                    event.getPlayer(), statesToRestore.remove(event.getPlayer().getUniqueId())));
  }

  public void restoreOnlinePlayers() {
    for (Player player : Bukkit.getOnlinePlayers()) ensureArmor(player, storage.load(player));
  }

  public void normalize(Player player) {
    if (player != null) ensureArmor(player, storage.load(player));
  }

  private void ensureArmor(Player player, HeroArmorState preferred) {
    if (player == null) return;
    HeroArmorState state = preferred == null ? findArmorState(player) : preferred;
    if (state == null) state = HeroArmorState.defaults();
    state = state.withFragmentRank(rankService.getRank(player));
    removeHeroArmorFromInventory(player);

    for (ArmorTier.ArmorSlot slot : ArmorTier.ArmorSlot.values()) {
      ItemStack piece = armorService.createPiece(slot, state);
      if (equipped(player, slot) == null || equipped(player, slot).getType().isAir()) {
        setEquipped(player, slot, piece);
      } else {
        armorService.giveOrDrop(player, piece);
      }
    }
    storage.save(player, state);
  }

  private HeroArmorState findArmorState(Player player) {
    for (ItemStack item : player.getInventory().getContents()) {
      if (armorService.isHeroArmor(item)) return armorService.getState(item);
    }
    for (ItemStack item : player.getInventory().getArmorContents()) {
      if (armorService.isHeroArmor(item)) return armorService.getState(item);
    }
    return null;
  }

  private void removeHeroArmorFromInventory(Player player) {
    for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
      if (armorService.isHeroArmor(player.getInventory().getItem(slot)))
        player.getInventory().setItem(slot, null);
    }
  }

  private ItemStack equipped(Player player, ArmorTier.ArmorSlot slot) {
    return switch (slot) {
      case HELMET -> player.getInventory().getHelmet();
      case CHESTPLATE -> player.getInventory().getChestplate();
      case LEGGINGS -> player.getInventory().getLeggings();
      case BOOTS -> player.getInventory().getBoots();
    };
  }

  private void setEquipped(Player player, ArmorTier.ArmorSlot slot, ItemStack item) {
    switch (slot) {
      case HELMET -> player.getInventory().setHelmet(item);
      case CHESTPLATE -> player.getInventory().setChestplate(item);
      case LEGGINGS -> player.getInventory().setLeggings(item);
      case BOOTS -> player.getInventory().setBoots(item);
    }
  }
}
