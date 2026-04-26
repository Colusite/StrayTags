package io.github.colusite.straytags.client.compat;

import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

public final class IconFont {

    public static final String GLYPH = "㭗";

    private static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath("straytags", "icons");

    public static Style iconStyle() {
        return Style.EMPTY.withFont(FONT);
    }

    private IconFont() {}
}