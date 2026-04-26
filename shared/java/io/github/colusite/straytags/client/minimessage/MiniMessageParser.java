package io.github.colusite.straytags.client.minimessage;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MiniMessageParser {

    private static final Logger LOGGER = LoggerFactory.getLogger("StrayTags");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final GsonComponentSerializer ADVENTURE_GSON = GsonComponentSerializer.gson();

    // Generic parse
    public static Component parse(String format, java.util.Map<String, String> placeholders) {
        if (format == null) format = "";
        try {
            String resolved = format;
            if (placeholders != null) {
                for (java.util.Map.Entry<String, String> e : placeholders.entrySet()) {
                    String key = e.getKey();
                    String val = e.getValue() != null ? e.getValue() : "";
                    resolved = resolved.replace("%" + key + "%", val);
                }
                // Legacy aliases
                if (resolved.contains("%username%") || resolved.contains("%clan%")) {
                    java.util.Iterator<java.util.Map.Entry<String, String>> it = placeholders.entrySet().iterator();
                    if (resolved.contains("%username%") && it.hasNext()) {
                        resolved = resolved.replace("%username%", it.next().getValue());
                    }
                    if (resolved.contains("%clan%") && it.hasNext()) {
                        resolved = resolved.replace("%clan%", it.next().getValue());
                    }
                }
            }
            // Strip any unresolved placeholders
            resolved = resolved.replaceAll("%[A-Za-z][A-Za-z0-9_]*%", "");
            resolved = resolved.replaceAll("\\s{2,}", " ").trim();

            net.kyori.adventure.text.Component adventureComponent = MINI_MESSAGE.deserialize(resolved);
            String json = ADVENTURE_GSON.serialize(adventureComponent);
            JsonElement jsonElement = JsonParser.parseString(json);
            return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, jsonElement)
                    .getOrThrow(errorMsg -> new RuntimeException("Failed to parse text: " + errorMsg));
        } catch (Exception e) {
            LOGGER.warn("[StrayTags] Failed to parse MiniMessage format: {}", format, e);
            String fallback = format.replaceAll("<[^>]+>", "")
                    .replaceAll("%[A-Za-z][A-Za-z0-9_]*%", "")
                    .replaceAll("\\s{2,}", " ")
                    .trim();
            return Component.literal(fallback);
        }
    }

    public static Component parse(String format, String username, String clan) {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        if (username != null) map.put("username", username);
        if (clan != null) map.put("clan", clan);
        return parse(format, map);
    }
}