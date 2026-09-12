package io.github.colusite.straytags.client.compat;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

public final class ChatCompat {
    private ChatCompat() {}

    public static void sendSystem(LocalPlayer player, Component message) {
        player.displayClientMessage(message, false);
    }

    public static void sendOverlay(LocalPlayer player, Component message) {
        player.displayClientMessage(message, true);
    }
}
