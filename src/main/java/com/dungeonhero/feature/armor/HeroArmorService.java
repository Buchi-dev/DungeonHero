package com.dungeonhero.feature.armor;

import com.dungeonhero.common.ItemDeliveryService;
import com.dungeonhero.config.DungeonHeroConfiguration;
import com.dungeonhero.feature.sword.ForgePolicy;
import com.dungeonhero.integration.mythicmobs.MythicFragmentService;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Bukkit-edge armor item operations; progression calculations live in policy classes. */
public final class HeroArmorService {

  private HeroArmorItemCodec codec;
  private final HeroArmorStorage storage;
  private ArmorCapPolicy capPolicy;
  private final ItemDeliveryService itemDeliveryService;

  public HeroArmorService(
      JavaPlugin plugin,
      ItemDeliveryService itemDeliveryService,
      DungeonHeroConfiguration.Armor configuration) {
    this.itemDeliveryService = itemDeliveryService;
    this.capPolicy = createCapPolicy(configuration);
    codec = new HeroArmorItemCodec(plugin, capPolicy);
    storage = new HeroArmorStorage(plugin);
  }

  public HeroArmorStorage storage() {
    return storage;
  }

  public void reload(DungeonHeroConfiguration.Armor configuration, JavaPlugin plugin) {
    capPolicy = createCapPolicy(configuration);
    codec = new HeroArmorItemCodec(plugin, capPolicy);
  }

  public ArmorCapPolicy capPolicy() {
    return capPolicy;
  }

  public ItemStack createPiece(ArmorTier.ArmorSlot slot, HeroArmorState state) {
    return codec.create(slot, state);
  }

  public ItemStack createPiece(ArmorTier.ArmorSlot slot) {
    return createPiece(slot, HeroArmorState.defaults());
  }

  public boolean isHeroArmor(ItemStack item) {
    return codec.isHeroArmor(item);
  }

  public ArmorTier.ArmorSlot getSlot(ItemStack item) {
    return codec.slot(item);
  }

  public HeroArmorState getState(ItemStack item) {
    return codec.read(item);
  }

  public ItemStack withState(ItemStack item, HeroArmorState state) {
    ArmorTier.ArmorSlot slot = getSlot(item);
    return slot == null ? item : codec.write(item, slot, state);
  }

  public ItemStack normalize(ItemStack item, int rank) {
    if (!isHeroArmor(item)) return item;
    HeroArmorState state = getState(item).withFragmentRank(rank);
    return withState(item, state);
  }

  public double effectiveBonus(HeroArmorState state, int rank) {
    return capPolicy.effective(state == null ? 0 : state.armorBonus(), rank);
  }

  public double inactiveOverflow(HeroArmorState state, int rank) {
    return capPolicy.overflow(state == null ? 0 : state.armorBonus(), rank);
  }

  public ItemStack forge(
      ItemStack armor, MythicFragmentService.FragmentUpgrade upgrade, int quantity) {
    if (!isHeroArmor(armor) || upgrade == null || !upgrade.isArmorSupported() || quantity <= 0) {
      throw new IllegalArgumentException(
          "The Hero Forge requires one Hero Armor piece and an ARMOR fragment.");
    }
    HeroArmorState state = getState(armor);
    double bonus =
        ForgePolicy.apply(
                state.armorBonus(), upgrade.amount(), quantity, capPolicy.maximumStoredArmor())
            .totalDamage();
    return withState(
        armor, new HeroArmorState(state.level(), state.xp(), bonus, state.fragmentRank()));
  }

  public int equippedPieceCount(Player player) {
    if (player == null) return 0;
    int count = 0;
    for (ArmorTier.ArmorSlot slot : ArmorTier.ArmorSlot.values()) {
      ItemStack item = equipped(player, slot);
      if (isHeroArmor(item) && getSlot(item) == slot) count++;
    }
    return count;
  }

  public List<ItemStack> equippedPieces(Player player) {
    if (player == null) return List.of();
    List<ItemStack> pieces = new ArrayList<>();
    for (ArmorTier.ArmorSlot slot : ArmorTier.ArmorSlot.values()) {
      ItemStack item = equipped(player, slot);
      if (isHeroArmor(item) && getSlot(item) == slot) pieces.add(item);
    }
    return List.copyOf(pieces);
  }

  public void giveOrDrop(Player player, ItemStack item) {
    itemDeliveryService.giveOrDrop(player, item);
  }

  public void save(Player player, HeroArmorState state) {
    storage.save(player, state);
  }

  public void syncPlayerItems(Player player, HeroArmorState state) {
    if (player == null) return;
    for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
      ItemStack item = player.getInventory().getItem(slot);
      if (isHeroArmor(item)) player.getInventory().setItem(slot, withState(item, state));
    }
  }

  public HeroArmorState loadOrDefault(Player player) {
    return storage.loadOrDefault(player);
  }

  private ArmorCapPolicy createCapPolicy(DungeonHeroConfiguration.Armor configuration) {
    double[] caps = new double[11];
    for (int rank = 1; rank <= 10; rank++) {
      caps[rank] = configuration.rankCaps().getOrDefault(rank, Double.NaN);
    }
    return new ArmorCapPolicy(caps, configuration.maximumStoredArmor());
  }

  private ItemStack equipped(Player player, ArmorTier.ArmorSlot slot) {
    return switch (slot) {
      case HELMET -> player.getInventory().getHelmet();
      case CHESTPLATE -> player.getInventory().getChestplate();
      case LEGGINGS -> player.getInventory().getLeggings();
      case BOOTS -> player.getInventory().getBoots();
    };
  }
}
