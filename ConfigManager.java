package com.example.donutauction.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads/saves config JSON. Disk writes happen on a background thread so the client never stalls. */
public final class ConfigManager {
    private static final Logger LOG = LoggerFactory.getLogger("donutauction");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "dsmpa-io");
        t.setDaemon(true);
        return t;
    });

    private static ModConfig config = new ModConfig();

    private ConfigManager() {}

    public static ModConfig get() { return config; }

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("donutauction-assistant.json");
    }

    public static void load() {
        Path p = file();
        if (!Files.exists(p)) {
            config = new ModConfig();
            config.sanitize();
            save();
            return;
        }
        try {
            ModConfig loaded = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), ModConfig.class);
            if (loaded == null) throw new JsonParseException("empty file");
            loaded.sanitize();
            config = loaded;
        } catch (IOException | JsonParseException | IllegalStateException e) {
            LOG.error("Config unreadable, using defaults. Broken file is kept as a backup.", e);
            try {
                Files.move(p, p.resolveSibling("donutauction-assistant.broken-" + System.currentTimeMillis() + ".json"),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) { }
            config = new ModConfig();
            config.sanitize();
            save();
        }
    }

    public static void save() {
        final String json = GSON.toJson(config); // snapshot on the calling thread
        final Path p = file();
        IO.execute(() -> {
            try {
                Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
                Files.writeString(tmp, json, StandardCharsets.UTF_8);
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                LOG.error("Could not save config", e);
            }
        });
    }

    /** Appends one line to the local chat-capture file (used to collect REAL server message formats). */
    public static void appendChatLog(String line) {
        final Path p = FabricLoader.getInstance().getConfigDir().resolve("donutauction-chatlog.txt");
        final String entry = LocalDateTime.now() + "  " + line + System.lineSeparator();
        IO.execute(() -> {
            try {
                Files.writeString(p, entry, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                LOG.warn("Could not write chat log", e);
            }
        });
    }
}
