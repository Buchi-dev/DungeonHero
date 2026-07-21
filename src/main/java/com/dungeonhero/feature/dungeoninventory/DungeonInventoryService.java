package com.dungeonhero.feature.dungeoninventory;

import com.dungeonhero.feature.sword.HeroItemService;
import com.dungeonhero.feature.sword.HeroSwordStorage;
import com.dungeonhero.feature.forge.ForgeMenu;
import com.dungeonhero.integration.mythicmobs.MythicFragmentService;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
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
 * Provides dungeon-world access to the Hero Forge without
 * changing, locking, or replacing the player's normal inventory.
 */
public final class DungeonInventoryService implements Listener {

    private static final int LEGACY_FRAGMENT_BAG_SIZE = 54;
    private static final int DEFAULT_FRAGMENT_VAULT_SLOTS = 27;
    private static final int DEFAULT_FRAGMENT_VAULT_STACK_SIZE = 64;

    private final JavaPlugin plugin;
    private final HeroItemService heroItemService;
    private final HeroSwordStorage heroSwordStorage;
    private final MythicFragmentService mythicFragmentService;
    private final File dataFile;
    private YamlConfiguration data;

    private boolean enabled;
    private boolean fragmentVaultEnabled;
    private int fragmentVaultSlots;
    private int fragmentVaultStackSize;
    private Set<String> dungeonWorlds = Set.of();

    public DungeonInventoryService(JavaPlugin plugin, HeroItemService heroItemService,
                                   HeroSwordStorage heroSwordStorage,
                                   MythicFragmentService mythicFragmentService) {
        this.plugin = plugin;
        this.heroItemService = heroItemService;
        this.heroSwordStorage = heroSwordStorage;
        this.mythicFragmentService = mythicFragmentService;
        this.dataFile = new File(plugin.getDataFolder(), "dungeon-inventories.yml");
        loadData();
        reload();
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("DungeonHero.DungeonInventory.Enabled", true);
        fragmentVaultEnabled = plugin.getConfig().getBoolean(
                "DungeonHero.DungeonInventory.FragmentVault.Enabled", true);
        int configuredVaultSlots = plugin.getConfig().getInt(
                "DungeonHero.DungeonInventory.FragmentVaultSlots", DEFAULT_FRAGMENT_VAULT_SLOTS);
        fragmentVaultSlots = Math.max(9, Math.min(DEFAULT_FRAGMENT_VAULT_SLOTS, (configuredVaultSlots / 9) * 9));
        fragmentVaultStackSize = Math.max(1, Math.min(64, plugin.getConfig().getInt(
                "DungeonHero.DungeonInventory.FragmentVaultStackSize", DEFAULT_FRAGMENT_VAULT_STACK_SIZE)));

        List<String> worlds = plugin.getConfig().getStringList("DungeonHero.DungeonInventory.Worlds");
        dungeonWorlds = worlds.stream()
                .map(String::trim)
                .filter(world -> !world.isBlank())
                .map(world -> world.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public boolean isDungeonWorld(Player player) {
        return enabled && player.getWorld().getName() != null
                && dungeonWorlds.contains(player.getWorld().getName().toLowerCase(Locale.ROOT));
    }

    public boolean isFragmentVaultEnabled() {
        return fragmentVaultEnabled;
    }

    /**
     * Restores inventories left in the old fixed-loadout system and migrates
     * old physical fragment bags. It does not alter normal dungeon players.
     */
    public void syncOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            migrateOrReleaseFragments(player);
            restoreLegacyInventorySnapshot(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            migrateOrReleaseFragments(event.getPlayer());
            restoreLegacyInventorySnapshot(event.getPlayer());
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> restoreLegacyInventorySnapshot(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !isDungeonWorld(player)
                || !fragmentVaultEnabled) {
            return;
        }

        MythicFragmentService.Inspection inspection = mythicFragmentService.inspect(event.getItem().getItemStack());
        if (!inspection.isValid()) {
            return;
        }

        ItemStack dropped = event.getItem().getItemStack();
        int requested = Math.max(1, dropped.getAmount());
        int stored = storeFragmentDrop(player, dropped);
        event.setCancelled(true);
        if (stored > 0) {
            if (stored >= requested) {
                event.getItem().remove();
            } else {
                dropped.setAmount(requested - stored);
                event.getItem().setItemStack(dropped);
            }
            player.sendActionBar(Component.text("Added " + stored + " fragment"
                    + (stored == 1 ? "" : "s") + " to your Fragment Vault.", NamedTextColor.GREEN));
        } else {
            player.sendActionBar(Component.text("Your Fragment Vault is full ("
                    + fragmentVaultSlots + " slots).", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory top = event.getView().getTopInventory();
        int rawSlot = event.getRawSlot();
        if (top.getHolder() instanceof DungeonMenuHolder) {
            if (rawSlot < 0 || rawSlot >= top.getSize()) {
                return;
            }
            event.setCancelled(true);
            if (rawSlot == 11) {
                Bukkit.getScheduler().runTask(plugin, () -> openFragmentVault(player));
            } else if (rawSlot == 15) {
                Bukkit.getScheduler().runTask(plugin, () -> ForgeMenu.open(player, heroItemService,
                        mythicFragmentService, heroSwordStorage, this));
            }
            return;
        }

        if (top.getHolder() instanceof FragmentVaultHolder
                && rawSlot >= 0 && rawSlot < top.getSize()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof DungeonMenuHolder)
                && !(top.getHolder() instanceof FragmentVaultHolder)) {
            return;
        }

        if (event.getRawSlots().stream().anyMatch(slot -> slot >= 0 && slot < top.getSize())) {
            event.setCancelled(true);
        }
    }

    public void openMenu(Player player) {
        if (!requireDungeonWorld(player, "The Dungeon Menu is only available inside a dungeon.")) {
            return;
        }

        Inventory menu = Bukkit.createInventory(new DungeonMenuHolder(), 27,
                Component.text("Dungeon Menu", NamedTextColor.GOLD));
        if (fragmentVaultEnabled) {
            menu.setItem(11, namedItem(Material.BOOK, "Fragment Vault", NamedTextColor.LIGHT_PURPLE,
                    "View fragments stored for your Hero Sword."));
        } else {
            menu.setItem(11, namedItem(Material.BARRIER, "Fragment Vault Removed", NamedTextColor.DARK_GRAY,
                    "Fragments now remain in your normal inventory."));
        }
        menu.setItem(13, namedItem(Material.CHEST, "Free Inventory", NamedTextColor.GREEN,
                "Your normal inventory is fully available in dungeons."));
        menu.setItem(15, namedItem(Material.ANVIL, "Hero Forge", NamedTextColor.DARK_PURPLE,
                "Spend fragments to improve your Hero Sword."));
        menu.setItem(22, namedItem(Material.BARRIER, "Close", NamedTextColor.RED));
        player.openInventory(menu);
    }

    public void openFragmentVault(Player player) {
        if (!fragmentVaultEnabled) {
            player.sendMessage(Component.text("The Fragment Vault has been removed. Fragments stay in your normal inventory.",
                    NamedTextColor.YELLOW));
            return;
        }
        if (!requireDungeonWorld(player, "The Fragment Vault is only available inside a dungeon.")) {
            return;
        }

        migrateLegacyFragments(player.getUniqueId());
        Inventory vault = Bukkit.createInventory(new FragmentVaultHolder(), fragmentVaultSlots,
                Component.text("Fragment Vault", NamedTextColor.LIGHT_PURPLE));
        int slot = 0;
        for (String id : mythicFragmentService.getFragmentIds()) {
            int count = getFragmentCount(player, id);
            int remaining = count;
            while (remaining > 0 && slot < vault.getSize()) {
                ItemStack icon = mythicFragmentService.createItem(id).orElseGet(
                        () -> namedItem(Material.AMETHYST_SHARD, id, NamedTextColor.LIGHT_PURPLE));
                int stackAmount = Math.min(fragmentVaultStackSize, remaining);
                icon.setAmount(stackAmount);
                ItemMeta meta = icon.getItemMeta();
                List<Component> lore = meta.hasLore() && meta.lore() != null
                        ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                lore.add(Component.text("Stored total: " + count, NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Vault slots: " + fragmentVaultSlots
                                + " | Stack size: " + fragmentVaultStackSize, NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Used directly by batch forging.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
                icon.setItemMeta(meta);
                vault.setItem(slot++, icon);
                remaining -= stackAmount;
            }
        }
        player.openInventory(vault);
    }

    public int getFragmentCount(Player player, String fragmentId) {
        if (!fragmentVaultEnabled) {
            return 0;
        }
        migrateLegacyFragments(player.getUniqueId());
        return Math.max(0, data.getInt(fragmentVaultPath(player.getUniqueId(), fragmentId), 0));
    }

    public ItemStack getAvailableForgeFragment(Player player) {
        if (!fragmentVaultEnabled) {
            return null;
        }
        migrateLegacyFragments(player.getUniqueId());
        for (String id : mythicFragmentService.getFragmentIds()) {
            if (getStoredFragmentCount(player.getUniqueId(), id) <= 0) {
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

    public boolean consumeForgeFragments(Player player, MythicFragmentService.FragmentUpgrade upgrade, int amount) {
        if (!fragmentVaultEnabled || upgrade == null) {
            return false;
        }
        migrateLegacyFragments(player.getUniqueId());
        int requested = Math.max(1, amount);
        String path = fragmentVaultPath(player.getUniqueId(), upgrade.id());
        int count = data.getInt(path, 0);
        if (count < requested) {
            return false;
        }
        data.set(path, count - requested);
        saveData();
        return true;
    }

    public ItemStack takeHeroSwordForForge(Player player) {
        ItemStack sword = heroItemService.findStrongestHeroSword(player);
        if (!heroItemService.isHeroSword(sword)) {
            return null;
        }

        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (candidate != sword && (candidate == null || !candidate.isSimilar(sword))) {
                continue;
            }
            inventory.setItem(slot, null);
            return sword.clone();
        }
        return null;
    }

    public void returnHeroSwordFromForge(Player player, ItemStack sword) {
        if (!heroItemService.isHeroSword(sword)) {
            return;
        }
        heroItemService.giveOrDrop(player, sword.clone());
        heroSwordStorage.save(player, sword);
    }

    private boolean requireDungeonWorld(Player player, String message) {
        if (isDungeonWorld(player)) {
            return true;
        }
        player.sendMessage(Component.text(message, NamedTextColor.YELLOW));
        return false;
    }

    private int storeFragmentDrop(Player player, ItemStack item) {
        MythicFragmentService.Inspection inspection = mythicFragmentService.inspect(item);
        if (!inspection.isValid()) {
            return 0;
        }
        migrateLegacyFragments(player.getUniqueId());
        return storeFragmentCount(player.getUniqueId(), inspection.upgrade().id(), Math.max(1, item.getAmount()));
    }

    private void migrateOrReleaseFragments(Player player) {
        if (fragmentVaultEnabled) {
            migrateLegacyFragments(player.getUniqueId());
            return;
        }
        releaseDisabledVaultFragments(player);
    }

    private void releaseDisabledVaultFragments(Player player) {
        String migratedPath = playerPath(player.getUniqueId()) + ".loadout.fragment_vault_disabled_migrated";
        if (data.getBoolean(migratedPath, false)) {
            return;
        }

        for (String id : mythicFragmentService.getFragmentIds()) {
            String path = fragmentVaultPath(player.getUniqueId(), id);
            int count = Math.max(0, data.getInt(path, 0));
            if (count <= 0) {
                continue;
            }
            ItemStack item = mythicFragmentService.createItem(id).orElse(null);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            int remaining = count;
            while (remaining > 0) {
                ItemStack stack = item.clone();
                int amount = Math.min(stack.getMaxStackSize(), remaining);
                stack.setAmount(amount);
                heroItemService.giveOrDrop(player, stack);
                remaining -= amount;
            }
            data.set(path, null);
        }
        data.set(migratedPath, true);
        saveData();
    }

    private int storeFragmentCount(UUID uuid, String fragmentId, int amount) {
        String path = fragmentVaultPath(uuid, fragmentId);
        int current = Math.max(0, data.getInt(path, 0));
        int usedWithoutThis = getFragmentVaultUsedSlots(uuid)
                - (current <= 0 ? 0 : (int) Math.ceil((double) current / fragmentVaultStackSize));
        int availableSlots = Math.max(0, fragmentVaultSlots - usedWithoutThis);
        int capacity = Math.max(0, availableSlots * fragmentVaultStackSize - current);
        int stored = Math.min(Math.max(0, amount), capacity);
        if (stored > 0) {
            data.set(path, current + stored);
            saveData();
        }
        return stored;
    }

    private int getFragmentVaultUsedSlots(UUID uuid) {
        int used = 0;
        for (String id : mythicFragmentService.getFragmentIds()) {
            int count = getStoredFragmentCount(uuid, id);
            used += count <= 0 ? 0 : (int) Math.ceil((double) count / fragmentVaultStackSize);
        }
        return used;
    }

    private int getStoredFragmentCount(UUID uuid, String fragmentId) {
        return Math.max(0, data.getInt(fragmentVaultPath(uuid, fragmentId), 0));
    }

    private void migrateLegacyFragments(UUID uuid) {
        String path = playerPath(uuid) + ".loadout";
        if (data.getBoolean(path + ".fragment_vault_migrated", false)) {
            return;
        }

        ItemStack[] legacyFragments = decode(data.getString(path + ".fragments"), LEGACY_FRAGMENT_BAG_SIZE);
        for (ItemStack fragment : legacyFragments) {
            MythicFragmentService.Inspection inspection = mythicFragmentService.inspect(fragment);
            if (!inspection.isValid()) {
                continue;
            }
            int requested = Math.max(1, fragment.getAmount());
            int migrated = storeFragmentCount(uuid, inspection.upgrade().id(), requested);
            if (migrated < requested) {
                plugin.getLogger().warning("Fragment Vault capacity reached while migrating " + uuid
                        + ". Some legacy fragments were not migrated.");
            }
        }
        data.set(path + ".fragment_vault_migrated", true);
        data.set(path + ".fragments", null);
        saveData();
    }

    private void restoreLegacyInventorySnapshot(Player player) {
        String path = playerPath(player.getUniqueId()) + ".normal";
        String storage = data.getString(path + ".storage");
        if (storage == null) {
            return;
        }

        InventorySnapshot snapshot = new InventorySnapshot(
                decode(storage, 36),
                decode(data.getString(path + ".armor"), 4),
                decode(data.getString(path + ".extra"), 1),
                Math.max(0, Math.min(8, data.getInt(path + ".held", 0))));
        ItemStack sword = heroItemService.findStrongestHeroSword(player);
        if (heroItemService.isHeroSword(sword)) {
            removeHeroSwords(snapshot.storage());
            addItem(snapshot.storage(), sword.clone());
            heroSwordStorage.save(player, sword);
        }
        clearInventory(player);
        snapshot.apply(player);
        data.set(path, null);
        saveData();
        player.sendMessage(Component.text("Your normal inventory has been restored. Dungeon loadouts are disabled.",
                NamedTextColor.GREEN));
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

    private String playerPath(UUID uuid) {
        return "players." + uuid;
    }

    private String fragmentVaultPath(UUID uuid, String fragmentId) {
        String encodedId = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(fragmentId.getBytes(StandardCharsets.UTF_8));
        return playerPath(uuid) + ".fragment_vault." + encodedId;
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
            plugin.getLogger().warning("Could not save DungeonHero dungeon data: " + exception.getMessage());
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

    private record FragmentVaultHolder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record DungeonMenuHolder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
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

        private static ItemStack[] cloneItems(ItemStack[] items) {
            ItemStack[] result = new ItemStack[items.length];
            for (int slot = 0; slot < items.length; slot++) {
                result[slot] = items[slot] == null ? null : items[slot].clone();
            }
            return result;
        }
    }
}
