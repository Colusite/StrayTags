package io.github.colusite.straytags.client.compat;

import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

public final class IconFont {

    public static final String GLYPH = "㭗";

    private static final Identifier FONT = Identifier.fromNamespaceAndPath("straytags", "icons");
    private static final FontDescription FONT_DESC = new FontDescription.Resource(FONT);

    public static Style iconStyle() {
        return Style.EMPTY.withFont(FONT_DESC);
    }

    private IconFont() {}
}