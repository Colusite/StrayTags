package io.github.colusite.straytags.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class StrayTagsConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("StrayTags");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "straytags.json";

    private static StrayTagsConfig config;

    public static StrayTagsConfig getConfig() {
        if (config == null) {
            load();
        }
        return config;
    }

    public static void load() {
        Path configPath = getConfigPath();
        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                config = GSON.fromJson(json, StrayTagsConfig.class);
                if (config == null) {
                    config = StrayTagsConfig.createDefault();
                }
                if (!config.serverConfigs.containsKey("__default__")) {
                    config.serverConfigs.put("__default__", new ServerConfig());
                }
                LOGGER.info("[StrayTags] Configuration loaded from {}", configPath);
            } catch (Exception e) {
                LOGGER.error("[StrayTags] Failed to load config, using defaults", e);
                config = StrayTagsConfig.createDefault();
            }
        } else {
            config = StrayTagsConfig.createDefault();
            save();
            LOGGER.info("[StrayTags] Default configuration created at {}", configPath);
        }
    }

    public static void save() {
        if (config == null) return;
        Path configPath = getConfigPath();
        try {
            Files.createDirectories(configPath.getParent());
            String json = GSON.toJson(config);
            Files.writeString(configPath, json);
            LOGGER.info("[StrayTags] Configuration saved to {}", configPath);
        } catch (IOException e) {
            LOGGER.error("[StrayTags] Failed to save config", e);
        }
    }

    private static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);
    }
}