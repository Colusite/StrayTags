package io.github.colusite.straytags.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class ConfigShareUtil {

    private static final Gson GSON = new GsonBuilder().create();
    private static final String PREFIX = "ST1:";

    public static String exportConfig(ServerConfig config) {
        if (config == null) return null;
        String json = GSON.toJson(config);
        String encoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        return PREFIX + encoded;
    }

    public static String exportClansOnly(ServerConfig config) {
        if (config == null) return null;
        JsonObject obj = new JsonObject();
        obj.add("ownClans", GSON.toJsonTree(config.ownClans));
        obj.add("ownPlayers", GSON.toJsonTree(config.ownPlayers));
        obj.add("alliedClans", GSON.toJsonTree(config.alliedClans));
        obj.add("alliedPlayers", GSON.toJsonTree(config.alliedPlayers));
        obj.add("enemyClans", GSON.toJsonTree(config.enemyClans));
        obj.add("enemyPlayers", GSON.toJsonTree(config.enemyPlayers));
        String json = GSON.toJson(obj);
        String encoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        return "ST1C:" + encoded;
    }

    public static String exportFormatsOnly(ServerConfig config) {
        if (config == null) return null;
        JsonObject obj = new JsonObject();
        obj.addProperty("ownFormat", config.ownFormat);
        obj.addProperty("alliedFormat", config.alliedFormat);
        obj.addProperty("enemyFormat", config.enemyFormat);
        obj.addProperty("neutralFormat", config.neutralFormat);
        obj.addProperty("noClanFormat", config.noClanFormat);
        obj.addProperty("namePattern", config.namePattern);
        String json = GSON.toJson(obj);
        String encoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        return "ST1F:" + encoded;
    }

    public static String importConfig(String code, ServerConfig target) {
        if (code == null || code.isBlank()) return "Empty code";
        code = code.strip();

        String type;
        String encoded;
        if (code.startsWith("ST1C:")) {
            type = "clans";
            encoded = code.substring(5);
        } else if (code.startsWith("ST1F:")) {
            type = "formats";
            encoded = code.substring(5);
        } else if (code.startsWith("ST1:")) {
            type = "full";
            encoded = code.substring(4);
        } else {
            return "Invalid code - must start with ST1:, ST1C:, or ST1F:";
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            String json = new String(decoded, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            if (type.equals("clans") || type.equals("full")) {
                applyClans(obj, target);
            }
            if (type.equals("formats") || type.equals("full")) {
                applyFormats(obj, target);
            }

            return null; // success
        } catch (IllegalArgumentException e) {
            return "Invalid Base64 encoding";
        } catch (Exception e) {
            return "Failed to parse config: " + e.getMessage();
        }
    }

    private static void applyClans(JsonObject obj, ServerConfig target) {
        if (obj.has("ownClans")) {
            target.ownClans.clear();
            obj.getAsJsonArray("ownClans").forEach(e -> target.ownClans.add(e.getAsString()));
        }
        if (obj.has("ownPlayers")) {
            target.ownPlayers.clear();
            obj.getAsJsonArray("ownPlayers").forEach(e -> target.ownPlayers.add(e.getAsString()));
        }
        if (obj.has("alliedClans")) {
            target.alliedClans.clear();
            obj.getAsJsonArray("alliedClans").forEach(e -> target.alliedClans.add(e.getAsString()));
        }
        if (obj.has("alliedPlayers")) {
            target.alliedPlayers.clear();
            obj.getAsJsonArray("alliedPlayers").forEach(e -> target.alliedPlayers.add(e.getAsString()));
        }
        if (obj.has("enemyClans")) {
            target.enemyClans.clear();
            obj.getAsJsonArray("enemyClans").forEach(e -> target.enemyClans.add(e.getAsString()));
        }
        if (obj.has("enemyPlayers")) {
            target.enemyPlayers.clear();
            obj.getAsJsonArray("enemyPlayers").forEach(e -> target.enemyPlayers.add(e.getAsString()));
        }
    }

    private static void applyFormats(JsonObject obj, ServerConfig target) {
        if (obj.has("ownFormat")) target.ownFormat = obj.get("ownFormat").getAsString();
        if (obj.has("alliedFormat")) target.alliedFormat = obj.get("alliedFormat").getAsString();
        if (obj.has("enemyFormat")) target.enemyFormat = obj.get("enemyFormat").getAsString();
        if (obj.has("neutralFormat")) target.neutralFormat = obj.get("neutralFormat").getAsString();
        if (obj.has("noClanFormat")) target.noClanFormat = obj.get("noClanFormat").getAsString();
        if (obj.has("namePattern")) target.namePattern = obj.get("namePattern").getAsString();
    }
}