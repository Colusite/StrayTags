package io.github.colusite.straytags.client.mixin;

import io.github.colusite.straytags.client.StrayTagsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(DisplayRenderer.TextDisplayRenderer.class)
public abstract class EntityRendererMixin {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Display$TextDisplay;Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;F)V",
            at = @At("RETURN")
    )
    private void straytags$modifyTextDisplay(
            Display.TextDisplay entity,
            TextDisplayEntityRenderState renderState,
            float tickProgress,
            CallbackInfo ci
    ) {
        if (!StrayTagsClient.isActiveOnCurrentServer()) return;
        if (renderState.cachedInfo == null) return;

        Player player = findRidingPlayer(entity);
        if (player == null) return;

        List<Display.TextDisplay.CachedLine> lines = renderState.cachedInfo.lines();
        String playerName = player.getGameProfile().getName();

        for (int i = 0; i < lines.size(); i++) {
            Display.TextDisplay.CachedLine line = lines.get(i);

            // Extract the raw string (with § codes and icons)
            StringBuilder sb = new StringBuilder();
            line.contents().accept((index, style, codePoint) -> {
                sb.appendCodePoint(codePoint);
                return true;
            });
            String rawLineString = sb.toString();

            if (rawLineString.isBlank()) continue;

            // Clean for matching
            String cleaned = StrayTagsClient.cleanForMatching(rawLineString);
            if (cleaned == null || cleaned.isBlank()) continue;
            if (!cleaned.contains(playerName)) continue;

            // Verbose/debug logging
            StrayTagsClient.logVerbose(rawLineString, playerName);

            // Try to process
            Component modified = StrayTagsClient.processDisplayName(Component.literal(rawLineString));
            if (modified == null) continue;

            // Rebuild with original prefix/suffix preserved
            Component finalComponent = StrayTagsClient.rebuildWithPrefixSuffix(rawLineString, cleaned, modified);

            FormattedCharSequence modifiedSeq = finalComponent.getVisualOrderText();
            int newWidth = Minecraft.getInstance().font.width(finalComponent);

            List<Display.TextDisplay.CachedLine> newLines = new ArrayList<>(lines);
            newLines.set(i, new Display.TextDisplay.CachedLine(modifiedSeq, newWidth));

            int newMaxWidth = newLines.stream()
                    .mapToInt(Display.TextDisplay.CachedLine::width)
                    .max()
                    .orElse(renderState.cachedInfo.width());

            renderState.cachedInfo = new Display.TextDisplay.CachedInfo(newLines, newMaxWidth);
            return;
        }
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