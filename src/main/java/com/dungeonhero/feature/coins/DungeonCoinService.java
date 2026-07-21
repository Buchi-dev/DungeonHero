package com.dungeonhero.feature.coins;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/** Persistent, plugin-owned Dungeon Coin storage and atomic balance operations. */
public final class DungeonCoinService {

    private static final String BALANCE_PATH = "Balances.";

    private final File storageFile;
    private final Consumer<String> errorLogger;
    private YamlConfiguration data;

    public DungeonCoinService(JavaPlugin plugin) {
        this.storageFile = new File(plugin.getDataFolder(), "coins.yml");
        this.errorLogger = message -> plugin.getLogger().severe(message);
        ensureParentDirectory(plugin.getDataFolder());
        reload();
    }

    DungeonCoinService(File dataDirectory, Consumer<String> errorLogger) {
        this.storageFile = new File(dataDirectory, "coins.yml");
        this.errorLogger = errorLogger;
        ensureParentDirectory(dataDirectory);
        reload();
    }

    public synchronized void reload() {
        data = YamlConfiguration.loadConfiguration(storageFile);
    }

    public synchronized long getBalance(UUID playerId) {
        return Math.max(0, data.getLong(path(playerId), 0));
    }

    public synchronized boolean setBalance(UUID playerId, long amount) {
        if (amount < 0) {
            return false;
        }
        long previous = getBalance(playerId);
        data.set(path(playerId), amount);
        if (save()) {
            return true;
        }
        data.set(path(playerId), previous);
        return false;
    }

    public synchronized boolean add(UUID playerId, long amount) {
        if (amount < 0) {
            return false;
        }
        long current = getBalance(playerId);
        if (Long.MAX_VALUE - current < amount) {
            return false;
        }
        data.set(path(playerId), current + amount);
        if (save()) {
            return true;
        }
        data.set(path(playerId), current);
        return false;
    }

    public synchronized boolean withdraw(UUID playerId, long amount) {
        if (amount < 0 || getBalance(playerId) < amount) {
            return false;
        }
        long current = getBalance(playerId);
        data.set(path(playerId), current - amount);
        if (save()) {
            return true;
        }
        data.set(path(playerId), current);
        return false;
    }

    public synchronized TransferResult transfer(UUID source, UUID target, long amount) {
        if (amount <= 0) {
            return new TransferResult(TransferStatus.INVALID_AMOUNT, 0, 0);
        }
        if (source.equals(target)) {
            return new TransferResult(TransferStatus.SELF_TRANSFER, getBalance(source), getBalance(target));
        }
        long sourceBalance = getBalance(source);
        long targetBalance = getBalance(target);
        if (sourceBalance < amount) {
            return new TransferResult(TransferStatus.INSUFFICIENT_FUNDS, sourceBalance, targetBalance);
        }
        if (Long.MAX_VALUE - targetBalance < amount) {
            return new TransferResult(TransferStatus.TARGET_BALANCE_LIMIT, sourceBalance, targetBalance);
        }

        data.set(path(source), sourceBalance - amount);
        data.set(path(target), targetBalance + amount);
        if (!save()) {
            data.set(path(source), sourceBalance);
            data.set(path(target), targetBalance);
            return new TransferResult(TransferStatus.STORAGE_FAILURE, sourceBalance, targetBalance);
        }
        return new TransferResult(TransferStatus.SUCCESS, sourceBalance - amount, targetBalance + amount);
    }

    public String format(long amount) {
        return String.format(Locale.ROOT, "%,d", Math.max(0, amount));
    }

    private String path(UUID playerId) {
        return BALANCE_PATH + playerId;
    }

    private boolean save() {
        try {
            data.save(storageFile);
            return true;
        } catch (IOException exception) {
            errorLogger.accept("Unable to save Dungeon Coins: " + exception.getMessage());
            return false;
        }
    }

    private void ensureParentDirectory(File dataDirectory) {
        if (!dataDirectory.exists() && !dataDirectory.mkdirs()) {
            errorLogger.accept("Unable to create DungeonHero data directory.");
        }
    }

    public enum TransferStatus {
        SUCCESS,
        INVALID_AMOUNT,
        SELF_TRANSFER,
        INSUFFICIENT_FUNDS,
        TARGET_BALANCE_LIMIT,
        STORAGE_FAILURE
    }

    public record TransferResult(TransferStatus status, long sourceBalance, long targetBalance) {
    }
}
