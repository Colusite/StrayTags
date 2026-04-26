package io.github.colusite.straytags.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class ConfigShareUtil {

    private static final Gson GSON = new GsonBuilder().create();
    private static final String PREFIX = "ST2:";

    // Export full server config
    public static String exportConfig(ServerConfig config) {
        if (config == null) return null;
        String json = GSON.toJson(config);
        String encoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        return PREFIX + encoded;
    }

    // Import config
    public static String importConfig(String code, ServerConfig target) {
        if (code == null || code.isBlank()) return "Empty code";
        code = code.strip();

        String encoded;
        boolean isLegacy;

        if (code.startsWith("ST2:")) {
            encoded = code.substring(4);
            isLegacy = false;
        } else if (code.startsWith("ST1:") || code.startsWith("ST1C:") || code.startsWith("ST1F:")) {
            // Legacy formats
            if (code.startsWith("ST1C:")) {
                encoded = code.substring(5);
            } else if (code.startsWith("ST1F:")) {
                encoded = code.substring(5);
            } else {
                encoded = code.substring(4);
            }
            isLegacy = true;
        } else {
            return "Invalid code - must start with ST2: (or legacy ST1:)";
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            String json = new String(decoded, StandardCharsets.UTF_8);

            if (isLegacy) {
                // Deserialize as old format ServerConfig, then migrate
                ServerConfig imported = GSON.fromJson(json, ServerConfig.class);
                if (imported.hasLegacyData()) {
                    imported.migrateLegacy();
                }
                target.categories.clear();
                target.categories.addAll(imported.categories);
                if (imported.neutralFormat != null) target.neutralFormat = imported.neutralFormat;
                if (imported.noClanFormat != null) target.noClanFormat = imported.noClanFormat;
                if (imported.namePattern != null) target.namePattern = imported.namePattern;
            } else {
                ServerConfig imported = GSON.fromJson(json, ServerConfig.class);
                target.categories.clear();
                target.categories.addAll(imported.categories);
                target.neutralFormat = imported.neutralFormat;
                target.noClanFormat = imported.noClanFormat;
                target.namePattern = imported.namePattern;
            }

            return null;
        } catch (IllegalArgumentException e) {
            return "Invalid Base64 encoding";
        } catch (Exception e) {
            return "Failed to parse config: " + e.getMessage();
        }
    }
}
