package io.github.colusite.straytags.client.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StrayTagsConfig {

    public boolean enabled = true;
    public List<String> serverWhitelist = new ArrayList<>();
    public Map<String, ServerConfig> serverConfigs = new HashMap<>();

    public StrayTagsConfig() {
    }

    public static StrayTagsConfig createDefault() {
        StrayTagsConfig config = new StrayTagsConfig();
        config.enabled = true;
        config.serverWhitelist.add("stray.gg");
        config.serverConfigs.put("stray.gg", ServerConfig.createDefaultStrayConfig());
        config.serverConfigs.put("__default__", new ServerConfig());
        return config;
    }

    public ServerConfig getServerConfig(String serverAddress) {
        if (serverAddress == null) return null;
        String lower = serverAddress.toLowerCase();

        ServerConfig exact = serverConfigs.get(lower);
        if (exact != null) return exact;

        // Partial match
        for (Map.Entry<String, ServerConfig> entry : serverConfigs.entrySet()) {
            String key = entry.getKey().toLowerCase();
            if (key.equals("__default__")) continue;
            if (lower.contains(key) || key.contains(lower)) {
                return entry.getValue();
            }
        }

        return serverConfigs.get("__default__");
    }

    public boolean isServerWhitelisted(String serverAddress) {
        if (serverAddress == null) return false;
        String lower = serverAddress.toLowerCase();
        for (String s : serverWhitelist) {
            if (s.toLowerCase().equals(lower)) return true;
            if (lower.contains(s.toLowerCase())) return true;
        }
        return false;
    }
}