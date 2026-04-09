package io.github.colusite.straytags.client.mixin;

import io.github.colusite.straytags.client.StrayTagsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;

@Mixin(DisplayRenderer.TextDisplayRenderer.class)
public abstract class EntityRendererMixin {

    @Redirect(
            method = "renderInner(Lnet/minecraft/world/entity/Display$TextDisplay;Lnet/minecraft/world/entity/Display$TextDisplay$TextRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Display$TextDisplay;cacheDisplay(Lnet/minecraft/world/entity/Display$TextDisplay$LineSplitter;)Lnet/minecraft/world/entity/Display$TextDisplay$CachedInfo;"
            )
    )
    private Display.TextDisplay.CachedInfo straytags$modifyCachedDisplay(
            Display.TextDisplay textDisplay,
            Display.TextDisplay.LineSplitter lineSplitter
    ) {
        Display.TextDisplay.CachedInfo original = textDisplay.cacheDisplay(lineSplitter);

        if (!StrayTagsClient.isActiveOnCurrentServer()) return original;

        Player player = findRidingPlayer(textDisplay);
        if (player == null) return original;

        List<Display.TextDisplay.CachedLine> lines = original.lines();
        if (lines.isEmpty()) return original;

        String playerName = player.getGameProfile().getName();

        for (int i = 0; i < lines.size(); i++) {
            Display.TextDisplay.CachedLine line = lines.get(i);

            StringBuilder sb = new StringBuilder();
            line.contents().accept((index, style, codePoint) -> {
                sb.appendCodePoint(codePoint);
                return true;
            });
            String rawLineString = sb.toString();

            if (rawLineString.isBlank()) continue;

            String cleaned = StrayTagsClient.cleanForMatching(rawLineString);
            if (cleaned == null || cleaned.isBlank()) continue;
            if (!cleaned.contains(playerName)) continue;

            StrayTagsClient.logVerbose(rawLineString, playerName);

            Component modified = StrayTagsClient.processDisplayName(Component.literal(rawLineString));
            if (modified == null) continue;

            Component finalComponent = StrayTagsClient.rebuildWithPrefixSuffix(rawLineString, cleaned, modified);

            FormattedCharSequence modifiedSeq = finalComponent.getVisualOrderText();
            int newWidth = Minecraft.getInstance().font.width(finalComponent);

            List<Display.TextDisplay.CachedLine> newLines = new ArrayList<>(lines);
            newLines.set(i, new Display.TextDisplay.CachedLine(modifiedSeq, newWidth));

            int newMaxWidth = newLines.stream()
                    .mapToInt(Display.TextDisplay.CachedLine::width)
                    .max()
                    .orElse(original.width());

            return new Display.TextDisplay.CachedInfo(newLines, newMaxWidth);
        }

        return original;
    }

    @Unique
    private static Player findRidingPlayer(Entity entity) {
        Entity current = entity.getVehicle();
        int depth = 0;
        while (current != null && depth < 5) {
            if (current instanceof Player player) {
                return player;
            }
            current = current.getVehicle();
            depth++;
        }
        return null;
    }
}