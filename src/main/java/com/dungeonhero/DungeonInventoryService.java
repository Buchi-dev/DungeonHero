package com.dungeonhero;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.block.Container;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Gives configured dungeon worlds their own small RPG loadout inventory.
 * The normal player inventory is stored on disk while the player is inside.
 */
public final class DungeonInventoryService implements Listener {

    private static final int FRAGMENT_BAG_SIZE = 54;
    private static final int SUPPLY_BAG_SIZE = 5;
    private static final int SUPPLY_MENU_SIZE = 54;
    private static final int[] DEFAULT_SUPPLY_HOTBAR_SLOTS = {2, 3, 5, 6, 7};
    private static final int SUPPLY_STAGING_START = 18;

    private final JavaPlugin plugin;
    private final HeroItemService heroItemService;
    private final HeroSwordStorage heroSwordStorage;
    private final MythicFragmentService mythicFragmentService;
    private final NamespacedKey bagTypeKey;
    private final NamespacedKey menuKey;
    private final File dataFile;
    private YamlConfiguration data;

    private boolean enabled;
    private boolean loseSuppliesOnDeath;
    private int heroSwordSlot;
    private int fragmentBagSlot;
    private int supplyBagSlot;
    private int reservedSlot;
    private int[] supplyHotbarSlots = DEFAULT_SUPPLY_HOTBAR_SLOTS.clone();
    private int maxUniqueSupplyItems;
    private int fragmentBagStackSize;
    private Set<String> dungeonWorlds = Set.of();
    private Set<Material> allowedSupplyMaterials = Set.of();

    public DungeonInventoryService(JavaPlugin plugin, HeroItemService heroItemService,
                                   HeroSwordStorage heroSwordStorage,
                                   MythicFragmentService mythicFragmentService) {
        this.plugin = plugin;
        this.heroItemService = heroItemService;
        this.heroSwordStorage = heroSwordStorage;
        this.mythicFragmentService = mythicFragmentService;
        this.bagTypeKey = new NamespacedKey(plugin, "dungeon_bag_type");
        this.menuKey = new NamespacedKey(plugin, "dungeon_menu");
        this.dataFile = new File(plugin.getDataFolder(), "dungeon-inventories.yml");
        loadData();
        reload();
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("DungeonHero.DungeonInventory.Enabled", true);
        loseSuppliesOnDeath = plugin.getConfig().getBoolean(
                "DungeonHero.DungeonInventory.LoseSuppliesOnDeath", false);
        heroSwordSlot = configuredHotbarSlot("HeroSwordSlot", 5);
        fragmentBagSlot = configuredHotbarSlot("FragmentBagSlot", 1);
        supplyBagSlot = configuredHotbarSlot("SupplyBagSlot", 2);
        reservedSlot = configuredHotbarSlot("ReservedSlot", 9);
        maxUniqueSupplyItems = Math.max(1, plugin.getConfig().getInt(
                "DungeonHero.DungeonInventory.MaxUniqueSupplyItems", 5));
        fragmentBagStackSize = Math.max(1, plugin.getConfig().getInt(
                "DungeonHero.DungeonInventory.FragmentBagStackSize", 64));

        List<String> worlds = plugin.getConfig().getStringList("DungeonHero.DungeonInventory.Worlds");
        dungeonWorlds = worlds.stream()
                .map(String::trim)
                .filter(world -> !world.isBlank())
                .map(world -> world.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        List<String> configuredMaterials = plugin.getConfig().getStringList(
                "DungeonHero.DungeonInventory.AllowedSupplyItems");
        Set<Material> materials = new HashSet<>();
        for (String configuredMaterial : configuredMaterials) {
            Material material = Material.matchMaterial(configuredMaterial);
            if (material == null) {
                plugin.getLogger().warning("Unknown DungeonHero supply material: " + configuredMaterial);
            } else {
                materials.add(material);
            }
        }
        allowedSupplyMaterials = Set.copyOf(materials);

        List<Integer> configuredSupplySlots = plugin.getConfig().getIntegerList(
                "DungeonHero.DungeonInventory.SupplyHotbarSlots");
        if (configuredSupplySlots.size() == SUPPLY_BAG_SIZE) {
            supplyHotbarSlots = configuredSupplySlots.stream()
                    .mapToInt(slot -> Math.max(1, Math.min(9, slot)) - 1)
                    .toArray();
        } else {
            supplyHotbarSlots = DEFAULT_SUPPLY_HOTBAR_SLOTS.clone();
        }
    }

    public boolean isDungeonWorld(Player player) {
        return enabled && player.getWorld().getName() != null
                && dungeonWorlds.contains(player.getWorld().getName().toLowerCase(Locale.ROOT));
    }

    public void syncOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            syncPlayer(player);
        }
    }

    public void saveOnlineDungeonPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isDungeonWorld(player)) {
                saveDungeonLoadout(player);
            }
        }
        saveData();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> syncPlayer(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> syncPlayer(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        if (isDungeonWorld(event.getPlayer())) {
            saveDungeonLoadout(event.getPlayer());
            saveData();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (isDungeonWorld(event.getPlayer())) {
                applyDungeonInventory(event.getPlayer());
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!isDungeonWorld(player)) {
            return;
        }

        saveDungeonLoadout(player);
        if (loseSuppliesOnDeath) {
            Loadout current = loadLoadout(player.getUniqueId());
            saveLoadout(player.getUniqueId(), new Loadout(current.fragments(), new ItemStack[SUPPLY_BAG_SIZE]));
        }
        event.getDrops().clear();
        saveData();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!isDungeonWorld(event.getPlayer())) {
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        ItemStack item = event.getItem();
        if (isDungeonMenuItem(item) && event.getAction().isRightClick()) {
            event.setCancelled(true);
            openMenu(event.getPlayer());
            return;
        }

        if (event.getClickedBlock() != null && event.getAction().isRightClick()
                && event.getClickedBlock().getState() instanceof Container) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isDungeonWorld(player)) {
            return;
        }

        MythicFragmentService.Inspection inspection = mythicFragmentService.inspect(event.getItem().getItemStack());
        if (!inspection.isValid()) {
            event.setCancelled(true);
            return;
        }

        storeFragmentDrop(player, event.getItem().getItemStack());
        event.setCancelled(true);
        event.getItem().remove();
        player.sendActionBar(Component.text("Fragment added to your Fragment Vault.", NamedTextColor.GREEN));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!isDungeonWorld(player)) {
            return;
        }

        if (isDungeonMenuItem(event.getItemDrop().getItemStack())
                || heroItemService.isHeroSword(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            player.sendMessage(Component.text("Your dungeon loadout cannot be dropped.", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (isDungeonWorld(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof DungeonMenuHolder) {
            event.setCancelled(true);
            int rawSlot = event.getRawSlot();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!isDungeonWorld(player)) {
                    return;
                }
                if (rawSlot == 11) {
                    openFragmentVault(player);
                } else if (rawSlot == 13) {
                    openLoadout(player);
                } else if (rawSlot == 15) {
                    ForgeMenu.open(player, heroItemService, mythicFragmentService,
                            heroSwordStorage, this);
                }
            });
            return;
        }

        if (top.getHolder() instanceof BagHolder holder) {
            handleBagClick(event, player, holder);
            return;
        }

        if (!isDungeonWorld(player)) {
            return;
        }

        if (event.getClickedInventory() instanceof PlayerInventory
                && event.getClick().isRightClick()
                && isDungeonMenuItem(event.getCurrentItem())) {
                event.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (isDungeonWorld(player)) {
                        openMenu(player);
                    }
                });
                return;
        }

        // The dungeon profile is intentionally hotbar-only. Bag and sword slots
        // are fixed, and the reserved slot cannot be used as an extra pouch.
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof DungeonMenuHolder) {
            event.setCancelled(true);
        } else if (event.getView().getTopInventory().getHolder() instanceof BagHolder holder) {
            if (holder.type() != BagType.SUPPLIES) {
                event.setCancelled(true);
                return;
            }

            ItemStack cursor = event.getOldCursor();
            boolean validTargets = event.getRawSlots().stream()
                    .allMatch(slot -> slot >= 0 && slot < SUPPLY_MENU_SIZE
                            && (slot < SUPPLY_BAG_SIZE || slot >= SUPPLY_STAGING_START));
            if (!validTargets || !isAllowedSupply(cursor)) {
                event.setCancelled(true);
            }
        } else if (isDungeonWorld(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)
                || !(event.getInventory().getHolder() instanceof BagHolder holder)) {
            return;
        }

        if (holder.type() == BagType.SUPPLIES && isDungeonWorld(player)) {
            finishSupplyMenu(player, event.getInventory());
        }
    }

    public ItemStack createFragmentBag() {
        return createBag(BagType.FRAGMENTS);
    }

    public ItemStack createSupplyBag() {
        return createBag(BagType.SUPPLIES);
    }

    public void openLoadout(Player player) {
        if (!isDungeonWorld(player)) {
            player.sendMessage(Component.text("The Supply Loadout is only available inside a dungeon.",
                    NamedTextColor.YELLOW));
            return;
        }
        openSupplyBag(player);
    }

    public ItemStack createDungeonMenuItem() {
        ItemStack item = namedItem(Material.NETHER_STAR, "Dungeon Menu", NamedTextColor.GOLD,
                "Open the Fragment Vault, Supply Loadout, or Hero Forge.",
                "Right-click or use /dh menu.");
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(menuKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public void openMenu(Player player) {
        if (!isDungeonWorld(player)) {
            player.sendMessage(Component.text("The Dungeon Menu is only available inside a dungeon.",
                    NamedTextColor.YELLOW));
            return;
        }

        Inventory menu = Bukkit.createInventory(new DungeonMenuHolder(player.getUniqueId()), 27,
                Component.text("Dungeon Menu", NamedTextColor.GOLD));
        menu.setItem(11, namedItem(Material.BOOK, "Fragment Vault", NamedTextColor.LIGHT_PURPLE,
                "View fragments stored for your Hero Sword."));
        menu.setItem(13, namedItem(Material.CHEST, "Supply Loadout", NamedTextColor.GREEN,
                "Choose your five unique dungeon items."));
        menu.setItem(15, namedItem(Material.ANVIL, "Hero Forge", NamedTextColor.DARK_PURPLE,
                "Spend fragments to improve your Hero Sword."));
        menu.setItem(22, namedItem(Material.BARRIER, "Close", NamedTextColor.RED));
        player.openInventory(menu);
    }

    public void openFragmentVault(Player player) {
        if (!isDungeonWorld(player)) {
            player.sendMessage(Component.text("The Fragment Vault is only available inside a dungeon.",
                    NamedTextColor.YELLOW));
            return;
        }

        Inventory vault = Bukkit.createInventory(new BagHolder(BagType.VAULT, player.getUniqueId()), 54,
                Component.text("Fragment Vault", NamedTextColor.LIGHT_PURPLE));
        int slot = 0;
        for (String id : mythicFragmentService.getFragmentIds()) {
            int count = getFragmentCount(player, id);
            if (count <= 0 || slot >= vault.getSize()) {
                continue;
            }
            ItemStack icon = mythicFragmentService.createItem(id).orElseGet(
                    () -> namedItem(Material.AMETHYST_SHARD, id, NamedTextColor.LIGHT_PURPLE));
            icon.setAmount(Math.max(1, Math.min(64, count)));
            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = meta.hasLore() && meta.lore() != null
                    ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Component.text("Stored: " + count, NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Fragments are used directly by /dh forge.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            icon.setItemMeta(meta);
            vault.setItem(slot++, icon);
        }
        player.openInventory(vault);
    }

    public int getFragmentCount(Player player, String fragmentId) {
        return Math.max(0, data.getInt(fragmentVaultPath(player.getUniqueId(), fragmentId), 0));
    }

    public ItemStack getAvailableForgeFragment(Player player) {
        for (String id : mythicFragmentService.getFragmentIds()) {
            if (getFragmentCount(player, id) <= 0) {
                continue;
            }
            ItemStack item = mythicFragmentService.createItem(id).orElse(null);
            if (item != null && !item.getType().isAir()) {
                item.setAmount(1);
                return item;
            }
        }
        return null;
    }

    public boolean consumeForgeFragment(Player player, MythicFragmentService.FragmentUpgrade upgrade) {
        if (upgrade == null) {
            return false;
        }
        String path = fragmentVaultPath(player.getUniqueId(), upgrade.id());
        int count = data.getInt(path, 0);
        if (count <= 0) {
            return false;
        }
        data.set(path, count - 1);
        saveData();
        return true;
    }

    public ItemStack takeFragmentForForge(Player player) {
        Loadout loadout = loadLoadout(player.getUniqueId());
        ItemStack[] fragments = loadout.fragments();
        for (int slot = 0; slot < fragments.length; slot++) {
            ItemStack fragment = fragments[slot];
            if (fragment == null || fragment.getType().isAir()
                    || !mythicFragmentService.inspect(fragment).isValid()) {
                continue;
            }

            ItemStack taken = fragment.clone();
            taken.setAmount(1);
            if (fragment.getAmount() <= 1) {
                fragments[slot] = null;
            } else {
                fragment.setAmount(fragment.getAmount() - 1);
            }
            saveLoadout(player.getUniqueId(), new Loadout(fragments, loadout.supply()));
            saveData();
            return taken;
        }
        return null;
    }

    public void returnFragmentFromForge(Player player, ItemStack fragment) {
        if (fragment == null || fragment.getType().isAir()) {
            return;
        }
        if (addToFragmentBag(player, fragment.clone()) < fragment.getAmount()) {
            player.getWorld().dropItemNaturally(player.getLocation(), fragment.clone());
        }
    }

    public ItemStack takeHeroSwordForForge(Player player) {
        ItemStack sword = player.getInventory().getItem(heroSwordSlot);
        if (!heroItemService.isHeroSword(sword)) {
            return null;
        }
        player.getInventory().setItem(heroSwordSlot, null);
        return sword.clone();
    }

    public void returnHeroSwordFromForge(Player player, ItemStack sword) {
        if (!heroItemService.isHeroSword(sword)) {
            return;
        }
        player.getInventory().setItem(heroSwordSlot, sword.clone());
        heroSwordStorage.save(player, sword);
    }

    private void syncPlayer(Player player) {
        if (isDungeonWorld(player)) {
            enterDungeon(player);
        } else if (hasNormalSnapshot(player.getUniqueId())) {
            leaveDungeon(player);
        }
    }

    private void enterDungeon(Player player) {
        UUID uuid = player.getUniqueId();
        InventorySnapshot normal = loadNormalSnapshot(uuid);
        boolean hadNormalSnapshot = normal != null;
        if (normal == null) {
            normal = InventorySnapshot.capture(player);
            saveNormalSnapshot(uuid, normal);
        }

        Loadout loadout = loadLoadout(uuid);
        if (hadNormalSnapshot) {
            depositFragmentsFromInventory(player);
        }
        if (isEmpty(loadout.supply())) {
            List<ItemStack> startingSupplies = takeAllowedSupplies(normal);
            loadout = new Loadout(loadout.fragments(), toSupplyArray(startingSupplies));
            saveNormalSnapshot(uuid, normal);
            saveLoadout(uuid, loadout);
        }

        applyDungeonInventory(player, loadout);
    }

    private void leaveDungeon(Player player) {
        UUID uuid = player.getUniqueId();
        saveDungeonLoadout(player);

        InventorySnapshot normal = loadNormalSnapshot(uuid);
        if (normal != null) {
            ItemStack sword = heroItemService.findStrongestHeroSword(player);
            if (heroItemService.isHeroSword(sword)) {
                removeHeroSwords(normal.storage());
                addItem(normal.storage(), sword.clone());
                heroSwordStorage.save(player, sword);
            }
            clearInventory(player);
            normal.apply(player);
        }

        data.set(playerPath(uuid) + ".normal", null);
        saveData();
    }

    private void applyDungeonInventory(Player player) {
        applyDungeonInventory(player, loadLoadout(player.getUniqueId()));
    }

    private void applyDungeonInventory(Player player, Loadout loadout) {
        ItemStack sword = heroSwordStorage.load(player);
        if (!heroItemService.isHeroSword(sword)) {
            sword = heroItemService.findStrongestHeroSword(player);
        }
        if (!heroItemService.isHeroSword(sword)) {
            sword = heroItemService.createHeroSword();
        }
        sword = heroItemService.normalizeSword(sword);
        heroSwordStorage.save(player, sword);

        clearInventory(player);
        PlayerInventory inventory = player.getInventory();
        inventory.setItem(fragmentBagSlot, createDungeonMenuItem());
        inventory.setItem(supplyBagSlot, namedItem(Material.BARRIER, "Reserved Dungeon Slot",
                NamedTextColor.DARK_GRAY, "Use /dh menu to open your dungeon tools."));
        inventory.setItem(heroSwordSlot, sword);
        inventory.setItem(reservedSlot, namedItem(Material.BARRIER, "Reserved Dungeon Slot",
                NamedTextColor.DARK_GRAY, "Reserved for future dungeon progression."));

        for (int index = 0; index < supplyHotbarSlots.length; index++) {
            ItemStack supply = index < loadout.supply().length ? loadout.supply()[index] : null;
            inventory.setItem(supplyHotbarSlots[index], supply == null ? null : supply.clone());
        }
        inventory.setHeldItemSlot(heroSwordSlot);
    }

    private void openFragmentBag(Player player) {
        Inventory inventory = Bukkit.createInventory(new BagHolder(BagType.FRAGMENTS, player.getUniqueId()),
                FRAGMENT_BAG_SIZE, Component.text("Fragment Bag", NamedTextColor.DARK_PURPLE));
        ItemStack[] fragments = loadLoadout(player.getUniqueId()).fragments();
        for (int slot = 0; slot < fragments.length && slot < inventory.getSize(); slot++) {
            if (fragments[slot] != null) {
                inventory.setItem(slot, fragments[slot].clone());
            }
        }
        player.openInventory(inventory);
    }

    private void openSupplyBag(Player player) {
        Loadout loadout = loadLoadout(player.getUniqueId());
        ItemStack[] currentSupply = new ItemStack[SUPPLY_BAG_SIZE];
        PlayerInventory playerInventory = player.getInventory();
        for (int index = 0; index < supplyHotbarSlots.length; index++) {
            ItemStack item = playerInventory.getItem(supplyHotbarSlots[index]);
            currentSupply[index] = item == null ? null : item.clone();
            playerInventory.setItem(supplyHotbarSlots[index], null);
        }
        loadout = new Loadout(loadout.fragments(), currentSupply);

        InventorySnapshot normal = loadNormalSnapshot(player.getUniqueId());
        List<ItemStack> staging = normal == null ? List.of() : takeAllowedSupplies(normal);
        if (normal != null) {
            saveNormalSnapshot(player.getUniqueId(), normal);
        }

        Inventory inventory = Bukkit.createInventory(new BagHolder(BagType.SUPPLIES, player.getUniqueId()),
                SUPPLY_MENU_SIZE, Component.text("Supply Bag", NamedTextColor.DARK_GREEN));
        for (int slot = 0; slot < SUPPLY_BAG_SIZE; slot++) {
            if (currentSupply[slot] != null) {
                inventory.setItem(slot, currentSupply[slot].clone());
            }
        }
        for (int slot = 5; slot < SUPPLY_STAGING_START; slot++) {
            inventory.setItem(slot, namedItem(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY));
        }
        inventory.setItem(9, namedItem(Material.PAPER, "Selected Supplies", NamedTextColor.GREEN,
                "Choose up to " + maxUniqueSupplyItems + " unique items."));
        int stagingSlot = SUPPLY_STAGING_START;
        for (ItemStack item : staging) {
            if (stagingSlot >= inventory.getSize()) {
                break;
            }
            inventory.setItem(stagingSlot++, item.clone());
        }
        player.openInventory(inventory);
    }

    private void handleBagClick(InventoryClickEvent event, Player player, BagHolder holder) {
        if (holder.type() != BagType.SUPPLIES) {
            event.setCancelled(true);
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= SUPPLY_MENU_SIZE) {
            event.setCancelled(true);
            return;
        }

        if (event.isShiftClick() || event.getClick().isKeyboardClick()) {
            event.setCancelled(true);
            return;
        }

        if (rawSlot < SUPPLY_BAG_SIZE) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir() && !isAllowedSupply(cursor)) {
                event.setCancelled(true);
                player.sendMessage(Component.text("That item cannot enter the dungeon.", NamedTextColor.RED));
            }
            return;
        }

        if (rawSlot < SUPPLY_STAGING_START) {
            event.setCancelled(true);
            return;
        }

        ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir() && !isAllowedSupply(cursor)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("Only configured dungeon supplies are allowed.", NamedTextColor.RED));
        }
    }

    private void finishSupplyMenu(Player player, Inventory menu) {
        ItemStack[] supply = new ItemStack[SUPPLY_BAG_SIZE];
        List<ItemStack> selectedTypes = new ArrayList<>();
        InventorySnapshot normal = loadNormalSnapshot(player.getUniqueId());
        for (int slot = 0; slot < SUPPLY_BAG_SIZE; slot++) {
            ItemStack item = menu.getItem(slot);
            if (item != null && !item.getType().isAir() && isAllowedSupply(item)
                    && (containsSimilar(selectedTypes, item) || selectedTypes.size() < maxUniqueSupplyItems)) {
                supply[slot] = item.clone();
                if (!containsSimilar(selectedTypes, item)) {
                    selectedTypes.add(item.clone());
                }
            } else if (normal != null && item != null && !item.getType().isAir()) {
                addItem(normal.storage(), item.clone());
            }
        }

        if (normal != null) {
            for (int slot = SUPPLY_STAGING_START; slot < menu.getSize(); slot++) {
                ItemStack item = menu.getItem(slot);
                if (item != null && !item.getType().isAir() && isAllowedSupply(item)) {
                    addItem(normal.storage(), item.clone());
                }
            }
            saveNormalSnapshot(player.getUniqueId(), normal);
        }

        Loadout oldLoadout = loadLoadout(player.getUniqueId());
        saveLoadout(player.getUniqueId(), new Loadout(oldLoadout.fragments(), supply));
        saveData();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (isDungeonWorld(player)) {
                applyDungeonInventory(player);
            }
        });
    }

    private void depositFragmentsFromInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || !mythicFragmentService.inspect(item).isValid()) {
                continue;
            }

            MythicFragmentService.Inspection inspection = mythicFragmentService.inspect(item);
            if (!inspection.isValid()) {
                continue;
            }
            storeFragmentDrop(player, item);
            inventory.setItem(slot, null);
        }
    }

    private void storeFragmentDrop(Player player, ItemStack item) {
        MythicFragmentService.Inspection inspection = mythicFragmentService.inspect(item);
        if (!inspection.isValid()) {
            return;
        }
        String path = fragmentVaultPath(player.getUniqueId(), inspection.upgrade().id());
        int current = data.getInt(path, 0);
        data.set(path, current + Math.max(1, item.getAmount()));
        saveData();
    }

    private int addToFragmentBag(Player player, ItemStack item) {
        Loadout loadout = loadLoadout(player.getUniqueId());
        ItemStack[] fragments = loadout.fragments();
        int amount = Math.max(0, item.getAmount());
        int originalAmount = amount;
        for (int slot = 0; slot < fragments.length && amount > 0; slot++) {
            ItemStack existing = fragments[slot];
            if (existing == null || !existing.isSimilar(item)) {
                continue;
            }
            int room = Math.max(0, fragmentBagStackSize - existing.getAmount());
            int moved = Math.min(room, amount);
            existing.setAmount(existing.getAmount() + moved);
            amount -= moved;
        }
        for (int slot = 0; slot < fragments.length && amount > 0; slot++) {
            if (fragments[slot] != null) {
                continue;
            }
            ItemStack added = item.clone();
            int moved = Math.min(fragmentBagStackSize, amount);
            added.setAmount(moved);
            fragments[slot] = added;
            amount -= moved;
        }
        int deposited = originalAmount - amount;
        if (deposited <= 0) {
            return 0;
        }
        saveLoadout(player.getUniqueId(), new Loadout(fragments, loadout.supply()));
        saveData();
        return deposited;
    }

    private List<ItemStack> takeAllowedSupplies(InventorySnapshot snapshot) {
        List<ItemStack> selected = new ArrayList<>();
        for (int slot = 0; slot < snapshot.storage().length && selected.size() < maxUniqueSupplyItems; slot++) {
            ItemStack item = snapshot.storage()[slot];
            if (!isAllowedSupply(item) || containsSimilar(selected, item)) {
                continue;
            }
            selected.add(item.clone());
            snapshot.storage()[slot] = null;
        }
        return selected;
    }

    private boolean containsSimilar(List<ItemStack> items, ItemStack candidate) {
        return items.stream().anyMatch(item -> item.isSimilar(candidate));
    }

    private boolean isAllowedSupply(ItemStack item) {
        return item != null && !item.getType().isAir() && allowedSupplyMaterials.contains(item.getType());
    }

    private ItemStack[] toSupplyArray(List<ItemStack> items) {
        ItemStack[] result = new ItemStack[SUPPLY_BAG_SIZE];
        for (int index = 0; index < items.size() && index < result.length; index++) {
            result[index] = items.get(index).clone();
        }
        return result;
    }

    private void saveDungeonLoadout(Player player) {
        Loadout loadout = loadLoadout(player.getUniqueId());
        ItemStack[] supplies = new ItemStack[SUPPLY_BAG_SIZE];
        PlayerInventory inventory = player.getInventory();
        for (int index = 0; index < supplyHotbarSlots.length; index++) {
            ItemStack item = inventory.getItem(supplyHotbarSlots[index]);
            supplies[index] = item == null ? null : item.clone();
        }
        saveLoadout(player.getUniqueId(), new Loadout(loadout.fragments(), supplies));
    }

    private ItemStack createBag(BagType type) {
        ItemStack bag = new ItemStack(Material.BUNDLE);
        ItemMeta meta = bag.getItemMeta();
        meta.displayName(Component.text(type.displayName(), type.color())
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(type == BagType.FRAGMENTS
                                ? "View fragments earned in the dungeon."
                                : "Prepare five unique dungeon supplies.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click to open.", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(bagTypeKey, PersistentDataType.STRING, type.name());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        bag.setItemMeta(meta);
        return bag;
    }

    private BagType getBagType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String value = item.getItemMeta().getPersistentDataContainer().get(bagTypeKey, PersistentDataType.STRING);
        if (value == null) {
            return null;
        }
        try {
            return BagType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private int configuredHotbarSlot(String path, int fallback) {
        return Math.max(0, Math.min(8,
                plugin.getConfig().getInt("DungeonHero.DungeonInventory." + path, fallback) - 1));
    }

    private void clearInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.setStorageContents(new ItemStack[36]);
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setExtraContents(new ItemStack[1]);
    }

    private void removeHeroSwords(ItemStack[] items) {
        for (int slot = 0; slot < items.length; slot++) {
            if (heroItemService.isHeroSword(items[slot])) {
                items[slot] = null;
            }
        }
    }

    private static void addItem(ItemStack[] items, ItemStack item) {
        int remaining = item.getAmount();
        for (ItemStack existing : items) {
            if (remaining <= 0 || existing == null || !existing.isSimilar(item)) {
                continue;
            }
            int room = Math.max(0, existing.getMaxStackSize() - existing.getAmount());
            int moved = Math.min(room, remaining);
            existing.setAmount(existing.getAmount() + moved);
            remaining -= moved;
        }
        for (int slot = 0; slot < items.length && remaining > 0; slot++) {
            if (items[slot] != null) {
                continue;
            }
            ItemStack added = item.clone();
            int moved = Math.min(added.getMaxStackSize(), remaining);
            added.setAmount(moved);
            items[slot] = added;
            remaining -= moved;
        }
    }

    private boolean isEmpty(ItemStack[] items) {
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                return false;
            }
        }
        return true;
    }

    private String playerPath(UUID uuid) {
        return "players." + uuid;
    }

    private String fragmentVaultPath(UUID uuid, String fragmentId) {
        String encodedId = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(fragmentId.getBytes(StandardCharsets.UTF_8));
        return playerPath(uuid) + ".fragment_vault." + encodedId;
    }

    private boolean isDungeonMenuItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Byte value = item.getItemMeta().getPersistentDataContainer()
                .get(menuKey, PersistentDataType.BYTE);
        return value != null && value == 1;
    }

    private boolean hasNormalSnapshot(UUID uuid) {
        return data.contains(playerPath(uuid) + ".normal.storage");
    }

    private void saveNormalSnapshot(UUID uuid, InventorySnapshot snapshot) {
        String path = playerPath(uuid) + ".normal";
        data.set(path + ".storage", encode(snapshot.storage()));
        data.set(path + ".armor", encode(snapshot.armor()));
        data.set(path + ".extra", encode(snapshot.extra()));
        data.set(path + ".held", snapshot.heldSlot());
        saveData();
    }

    private InventorySnapshot loadNormalSnapshot(UUID uuid) {
        String path = playerPath(uuid) + ".normal";
        String storage = data.getString(path + ".storage");
        if (storage == null) {
            return null;
        }
        return new InventorySnapshot(
                decode(storage, 36),
                decode(data.getString(path + ".armor"), 4),
                decode(data.getString(path + ".extra"), 1),
                Math.max(0, Math.min(8, data.getInt(path + ".held", 0))));
    }

    private void saveLoadout(UUID uuid, Loadout loadout) {
        String path = playerPath(uuid) + ".loadout";
        data.set(path + ".fragments", encode(loadout.fragments()));
        data.set(path + ".supply", encode(loadout.supply()));
    }

    private Loadout loadLoadout(UUID uuid) {
        String path = playerPath(uuid) + ".loadout";
        ItemStack[] legacyFragments = decode(data.getString(path + ".fragments"), FRAGMENT_BAG_SIZE);
        if (!data.getBoolean(path + ".fragment_vault_migrated", false)) {
            for (ItemStack fragment : legacyFragments) {
                MythicFragmentService.Inspection inspection = mythicFragmentService.inspect(fragment);
                if (!inspection.isValid()) {
                    continue;
                }
                String vaultPath = fragmentVaultPath(uuid, inspection.upgrade().id());
                data.set(vaultPath, data.getInt(vaultPath, 0) + Math.max(1, fragment.getAmount()));
            }
            data.set(path + ".fragment_vault_migrated", true);
            data.set(path + ".fragments", null);
            saveData();
        }
        return new Loadout(
                new ItemStack[0],
                decode(data.getString(path + ".supply"), SUPPLY_BAG_SIZE));
    }

    private String encode(ItemStack[] items) {
        return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(items));
    }

    private ItemStack[] decode(String encoded, int size) {
        ItemStack[] result = new ItemStack[size];
        if (encoded == null || encoded.isBlank()) {
            return result;
        }
        try {
            ItemStack[] decoded = ItemStack.deserializeItemsFromBytes(
                    Base64.getDecoder().decode(encoded.getBytes(StandardCharsets.UTF_8)));
            System.arraycopy(decoded, 0, result, 0, Math.min(decoded.length, result.length));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Could not decode DungeonHero inventory data.");
        }
        return result;
    }

    private void loadData() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create the DungeonHero data folder.");
        }
        data = dataFile.exists() ? YamlConfiguration.loadConfiguration(dataFile) : new YamlConfiguration();
    }

    private void saveData() {
        try {
            data.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save DungeonHero dungeon inventories: " + exception.getMessage());
        }
    }

    private ItemStack namedItem(Material material, String name, NamedTextColor color, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        if (loreLines.length > 0) {
            meta.lore(java.util.Arrays.stream(loreLines)
                    .map(line -> Component.text(line, NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false))
                    .toList());
        }
        item.setItemMeta(meta);
        return item;
    }

    private enum BagType {
        FRAGMENTS("Fragment Bag", NamedTextColor.LIGHT_PURPLE),
        VAULT("Fragment Vault", NamedTextColor.LIGHT_PURPLE),
        SUPPLIES("Supply Bag", NamedTextColor.GREEN);

        private final String displayName;
        private final NamedTextColor color;

        BagType(String displayName, NamedTextColor color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String displayName() {
            return displayName;
        }

        public NamedTextColor color() {
            return color;
        }
    }

    private record BagHolder(BagType type, UUID playerId) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record DungeonMenuHolder(UUID playerId) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static final class Loadout {
        private final ItemStack[] fragments;
        private final ItemStack[] supply;

        private Loadout(ItemStack[] fragments, ItemStack[] supply) {
            this.fragments = fragments;
            this.supply = supply;
        }

        public ItemStack[] fragments() {
            return fragments;
        }

        public ItemStack[] supply() {
            return supply;
        }
    }

    private static final class InventorySnapshot {
        private final ItemStack[] storage;
        private final ItemStack[] armor;
        private final ItemStack[] extra;
        private final int heldSlot;

        private InventorySnapshot(ItemStack[] storage, ItemStack[] armor, ItemStack[] extra, int heldSlot) {
            this.storage = storage;
            this.armor = armor;
            this.extra = extra;
            this.heldSlot = heldSlot;
        }

        private static InventorySnapshot capture(Player player) {
            PlayerInventory inventory = player.getInventory();
            return new InventorySnapshot(
                    cloneItems(inventory.getStorageContents()),
                    cloneItems(inventory.getArmorContents()),
                    cloneItems(inventory.getExtraContents()),
                    inventory.getHeldItemSlot());
        }

        private void apply(Player player) {
            PlayerInventory inventory = player.getInventory();
            inventory.setStorageContents(cloneItems(storage));
            inventory.setArmorContents(cloneItems(armor));
            inventory.setExtraContents(cloneItems(extra));
            inventory.setHeldItemSlot(heldSlot);
        }

        private ItemStack[] storage() {
            return storage;
        }

        private ItemStack[] armor() {
            return armor;
        }

        private ItemStack[] extra() {
            return extra;
        }

        private int heldSlot() {
            return heldSlot;
        }

        private static ItemStack[] cloneItems(ItemStack[] items) {
            ItemStack[] result = new ItemStack[items.length];
            for (int slot = 0; slot < items.length; slot++) {
                result[slot] = items[slot] == null ? null : items[slot].clone();
            }
            return result;
        }
    }
}
