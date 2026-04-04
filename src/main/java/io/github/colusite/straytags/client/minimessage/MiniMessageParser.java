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

    public static Component parse(String format, String rank, String username, String clan) {
        try {
            String resolved = format;
            resolved = resolved.replace("%rank%", rank != null ? rank : "");
            resolved = resolved.replace("%username%", username != null ? username : "");
            resolved = resolved.replace("%clan%", clan != null ? clan : "");
            resolved = resolved.replaceAll("\\s{2,}", " ").trim();

            net.kyori.adventure.text.Component adventureComponent = MINI_MESSAGE.deserialize(resolved);

            String json = ADVENTURE_GSON.serialize(adventureComponent);

            JsonElement jsonElement = JsonParser.parseString(json);
            return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, jsonElement)
                    .getOrThrow(errorMsg -> new RuntimeException("Failed to parse text: " + errorMsg));
        } catch (Exception e) {
            LOGGER.warn("[StrayTags] Failed to parse MiniMessage format: {}", format, e);
            String fallback = format
                    .replace("%rank%", rank != null ? rank : "")
                    .replace("%username%", username != null ? username : "")
                    .replace("%clan%", clan != null ? clan : "")
                    .replaceAll("<[^>]+>", "")
                    .replaceAll("\\s{2,}", " ")
                    .trim();
            return Component.literal(fallback);
        }
    }
}