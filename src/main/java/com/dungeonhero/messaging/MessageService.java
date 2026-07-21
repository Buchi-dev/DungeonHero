package com.dungeonhero.messaging;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Loads locale resources while keeping an English fallback for missing keys. */
public final class MessageService {

    private static final String DEFAULT_LOCALE = "en_us";

    private final JavaPlugin plugin;
    private YamlConfiguration messages = new YamlConfiguration();

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        String locale = plugin.getConfig().getString("DungeonHero.Locale", DEFAULT_LOCALE)
                .toLowerCase(java.util.Locale.ROOT);
        messages = load(locale);
    }

    public Component text(String key, String fallback) {
        return Component.text(messages.getString(key, fallback));
    }

    private YamlConfiguration load(String locale) {
        YamlConfiguration loaded = loadResource(locale);
        return loaded.getKeys(true).isEmpty() && !DEFAULT_LOCALE.equals(locale)
                ? loadResource(DEFAULT_LOCALE) : loaded;
    }

    private YamlConfiguration loadResource(String locale) {
        String path = "messages/" + locale + ".yml";
        InputStream resource = plugin.getResource(path);
        if (resource == null) {
            return new YamlConfiguration();
        }
        try (InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (java.io.IOException exception) {
            plugin.getLogger().warning("Unable to load message resource " + path + ": " + exception.getMessage());
            return new YamlConfiguration();
        }
    }
}
