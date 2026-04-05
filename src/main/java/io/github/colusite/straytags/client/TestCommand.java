package io.github.colusite.straytags.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.colusite.straytags.client.config.ClanCategory;
import io.github.colusite.straytags.client.config.ServerConfig;
import io.github.colusite.straytags.client.config.StrayTagsConfigManager;
import io.github.colusite.straytags.client.minimessage.MiniMessageParser;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestCommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(TestCommand::registerCommands);
    }

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher,
                                         CommandBuildContext registryAccess) {
        var root = ClientCommandManager.literal("straytags");

        root.then(ClientCommandManager.literal("reload")
                .executes(ctx -> {
                    StrayTagsConfigManager.load();
                    ctx.getSource().sendFeedback(Component.literal("§a[StrayTags] Config reloaded!"));
                    return 1;
                })
        );

        if (StrayTagsConfigManager.getConfig().debugCommandsEnabled) {
            root.then(ClientCommandManager.literal("test")
                    .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                String input = StringArgumentType.getString(ctx, "name");
                                return runTest(ctx.getSource(), input);
                            })
                    )
            );

            root.then(ClientCommandManager.literal("testuser")
                    .executes(ctx -> runTestUser(ctx.getSource(), null))
                    .then(ClientCommandManager.argument("player", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                String target = StringArgumentType.getString(ctx, "player");
                                return runTestUser(ctx.getSource(), target);
                            })
                    )
            );

            root.then(ClientCommandManager.literal("verbose")
                    .executes(ctx -> {
                        StrayTagsClient.verboseMode = !StrayTagsClient.verboseMode;
                        if (StrayTagsClient.verboseMode) StrayTagsClient.clearVerboseCache();
                        ctx.getSource().sendFeedback(Component.literal(
                                "§e[StrayTags] Verbose mode: " +
                                        (StrayTagsClient.verboseMode ? "§aON" : "§cOFF") +
                                        (StrayTagsClient.verboseMode ? " §7- all nametags will be logged to chat" : "")));
                        return 1;
                    })
            );

            root.then(ClientCommandManager.literal("debug")
                    .executes(ctx -> {
                        StrayTagsClient.debugMode = !StrayTagsClient.debugMode;
                        if (StrayTagsClient.debugMode) StrayTagsClient.clearVerboseCache();
                        ctx.getSource().sendFeedback(Component.literal(
                                "§e[StrayTags] Debug mode: " +
                                        (StrayTagsClient.debugMode ? "§aON" : "§cOFF")));
                        return 1;
                    })
            );
        }

        dispatcher.register(root);
    }

    private static int runTestUser(FabricClientCommandSource source, String targetName) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            source.sendFeedback(Component.literal("§c[StrayTags] Not in a world!"));
            return 0;
        }

        Player target = null;
        ClientLevel level = client.level;

        if (targetName != null && !targetName.isEmpty()) {
            for (AbstractClientPlayer player : level.players()) {
                if (player.getGameProfile().name().equalsIgnoreCase(targetName)) {
                    target = player;
                    break;
                }
            }
            if (target == null) {
                source.sendFeedback(Component.literal("§c[StrayTags] Player '" + targetName + "' not found nearby!"));
                return 0;
            }
        } else {
            double closestDist = Double.MAX_VALUE;
            for (AbstractClientPlayer player : level.players()) {
                if (player == client.player) continue;
                double dist = client.player.distanceToSqr(player);
                if (dist < closestDist) {
                    closestDist = dist;
                    target = player;
                }
            }
            if (target == null) {
                source.sendFeedback(Component.literal("§c[StrayTags] No other players nearby!"));
                return 0;
            }
        }

        String playerName = target.getGameProfile().name();
        source.sendFeedback(Component.literal("§e[StrayTags] Inspecting: §f" + playerName));
        source.sendFeedback(Component.literal("§7  Vanilla displayName: §f'" + target.getDisplayName().getString() + "'"));

        List<Entity> passengers = target.getPassengers();
        if (passengers.isEmpty()) {
            source.sendFeedback(Component.literal("§7  No passengers (no TextDisplay riding this player)"));
            source.sendFeedback(Component.literal("§7  This server might use vanilla nametags."));
            return 1;
        }

        int textDisplayCount = 0;
        for (Entity passenger : passengers) {
            if (passenger instanceof Display.TextDisplay textDisplay) {
                textDisplayCount++;
                Component textData = textDisplay.getText();
                String raw = textData.getString();

                source.sendFeedback(Component.literal("§e  TextDisplay #" + textDisplayCount + ":"));
                source.sendFeedback(Component.literal("§7    Raw text: §f'" + raw + "'"));

                if (!raw.isBlank()) {
                    String cleaned = StrayTagsClient.cleanForMatching(raw);

                    source.sendFeedback(Component.literal("§7    Cleaned: §f'" + cleaned + "'"));

                    ServerConfig serverConfig = StrayTagsClient.getActiveServerConfig();
                    if (serverConfig == null) {
                        source.sendFeedback(Component.literal("§c    No active server config!"));
                        continue;
                    }

                    try {
                        Pattern pattern = Pattern.compile(serverConfig.namePattern);
                        Matcher matcher = pattern.matcher(cleaned);
                        source.sendFeedback(Component.literal("§7    Pattern: §f" + serverConfig.namePattern));

                        if (!matcher.matches()) {
                            source.sendFeedback(Component.literal("§c    NO MATCH"));
                        } else {
                            String rank = safeGroup(matcher, "rank");
                            String username = safeGroup(matcher, "username");
                            String clan = safeGroup(matcher, "clan");
                            ClanCategory cat = StrayTagsClient.categorize(serverConfig, username, clan);

                            source.sendFeedback(Component.literal("§a    MATCH!"));
                            source.sendFeedback(Component.literal("§7    rank=§f" + (rank != null ? rank : "(none)")
                                    + " §7user=§f" + (username != null ? username : "(none)")
                                    + " §7clan=§f" + (clan != null ? clan : "(none)")));
                            source.sendFeedback(Component.literal("§7    category=§f" + cat.name()));

                            String format = StrayTagsClient.getFormatForCategory(serverConfig, cat, clan);

                            if (format == null || format.isBlank()) {
                                source.sendFeedback(Component.literal("§7    format=§f(blank - won't modify)"));
                            } else {
                                source.sendFeedback(Component.literal("§7    format=§f" + format));

                                Component result = MiniMessageParser.parse(format,
                                        rank != null ? rank : "", username, clan != null ? clan : "");
                                source.sendFeedback(Component.literal("§7    result: ").copy().append(result));
                            }
                        }
                    } catch (Exception e) {
                        source.sendFeedback(Component.literal("§c    Error: " + e.getMessage()));
                    }
                }
            } else {
                source.sendFeedback(Component.literal("§7  Passenger: " + passenger.getType().getDescription().getString()
                        + " (not a TextDisplay)"));
            }
        }

        if (textDisplayCount == 0) {
            source.sendFeedback(Component.literal("§7  No TextDisplay passengers found (" + passengers.size() + " other passengers)"));
        }

        return 1;
    }

    private static int runTest(FabricClientCommandSource source, String input) {
        ServerConfig serverConfig = StrayTagsConfigManager.getConfig().getServerConfig("stray.gg");
        if (serverConfig == null) {
            serverConfig = StrayTagsConfigManager.getConfig().getServerConfig("__default__");
        }
        if (serverConfig == null) {
            source.sendFeedback(Component.literal("§c[StrayTags] No config found!"));
            return 0;
        }

        try {
            String cleaned = StrayTagsClient.cleanForMatching(input);

            Pattern pattern = Pattern.compile(serverConfig.namePattern);
            Matcher matcher = pattern.matcher(cleaned);

            if (!matcher.matches()) {
                source.sendFeedback(Component.literal("§c[StrayTags] No match for: " + cleaned));
                source.sendFeedback(Component.literal("§7Pattern: " + serverConfig.namePattern));
                return 0;
            }

            String rank = safeGroup(matcher, "rank");
            String username = safeGroup(matcher, "username");
            String clan = safeGroup(matcher, "clan");
            ClanCategory category = StrayTagsClient.categorize(serverConfig, username, clan);

            source.sendFeedback(Component.literal("§e[StrayTags] §7Parse results:"));
            source.sendFeedback(Component.literal("  §7Input: §f" + cleaned));
            source.sendFeedback(Component.literal("  §7Rank: §f" + (rank != null ? rank : "(none)")));
            source.sendFeedback(Component.literal("  §7Username: §f" + (username != null ? username : "(none)")));
            source.sendFeedback(Component.literal("  §7Clan: §f" + (clan != null ? clan : "(none)")));
            source.sendFeedback(Component.literal("  §7Category: §f" + category.name()));

            String format = StrayTagsClient.getFormatForCategory(serverConfig, category, clan);

            if (format == null || format.isBlank()) {
                source.sendFeedback(Component.literal("  §7Format: §f(blank - won't modify)"));
            } else {
                source.sendFeedback(Component.literal("  §7Format: §f" + format));
                Component result = MiniMessageParser.parse(format, rank != null ? rank : "", username, clan != null ? clan : "");
                source.sendFeedback(Component.literal("  §7Result: ").copy().append(result));
            }

        } catch (Exception e) {
            source.sendFeedback(Component.literal("§c[StrayTags] Error: " + e.getMessage()));
        }

        return 1;
    }

    private static String safeGroup(Matcher matcher, String groupName) {
        try {
            return matcher.group(groupName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}