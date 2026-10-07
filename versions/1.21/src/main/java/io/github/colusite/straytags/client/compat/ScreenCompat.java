package io.github.colusite.straytags.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class ScreenCompat {
    private ScreenCompat() {}

    public static void setScreen(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }
}
