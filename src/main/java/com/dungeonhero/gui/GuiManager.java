package com.dungeonhero.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Owns custom inventory sessions and guarantees one close callback per session. */
public final class GuiManager implements Listener {

    private final Map<Inventory, Session> sessionsByInventory = new IdentityHashMap<>();
    private final Map<UUID, Session> sessionsByPlayer = new HashMap<>();

    public Inventory open(Player player, int rows, Component title, Screen screen) {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("GUI rows must be between 1 and 6.");
        }

        close(player);
        Inventory inventory = Bukkit.createInventory(null, rows * 9, title);
        Session session = new Session(player, inventory, screen);
        sessionsByInventory.put(inventory, session);
        sessionsByPlayer.put(player.getUniqueId(), session);
        screen.onOpen(player, inventory);
        player.openInventory(inventory);
        return inventory;
    }

    public void close(Player player) {
        Session session = sessionsByPlayer.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        finish(session, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Session session = sessionsByInventory.get(event.getView().getTopInventory());
        if (session != null) {
            session.screen().onClick(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        Session session = sessionsByInventory.get(event.getView().getTopInventory());
        if (session != null) {
            session.screen().onDrag(event);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Session session = sessionsByInventory.get(event.getInventory());
        if (session != null) {
            finish(session, false);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Session session = sessionsByPlayer.get(event.getPlayer().getUniqueId());
        if (session != null) {
            finish(session, false);
        }
    }

    /** Closes all active screens before the plugin is disabled. */
    public void closeAll() {
        for (Session session : new ArrayList<>(sessionsByInventory.values())) {
            finish(session, true);
        }
    }

    private void finish(Session session, boolean closeInventory) {
        if (!sessionsByInventory.containsKey(session.inventory())) {
            return;
        }

        sessionsByInventory.remove(session.inventory());
        sessionsByPlayer.remove(session.player().getUniqueId(), session);
        session.screen().onClose(session.player(), session.inventory());

        if (closeInventory && session.player().isOnline()
                && session.player().getOpenInventory().getTopInventory() == session.inventory()) {
            session.player().closeInventory();
        }
    }

    public interface Screen {

        void onOpen(Player player, Inventory inventory);

        void onClick(InventoryClickEvent event);

        void onDrag(InventoryDragEvent event);

        void onClose(Player player, Inventory inventory);
    }

    private record Session(Player player, Inventory inventory, Screen screen) {
    }
}
