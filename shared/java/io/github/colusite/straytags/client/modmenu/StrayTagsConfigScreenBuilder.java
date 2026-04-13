package io.github.colusite.straytags.client.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import io.github.colusite.straytags.client.config.ConfigShareUtil;
import io.github.colusite.straytags.client.config.ServerConfig;
import io.github.colusite.straytags.client.config.StrayTagsConfig;
import io.github.colusite.straytags.client.config.StrayTagsConfigManager;
import io.github.colusite.straytags.client.minimessage.MiniMessageParser;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Consumer;

public class StrayTagsConfigScreenBuilder {

    public static ConfigScreenFactory<?> create() {
        return parent -> {
            StrayTagsConfig config = StrayTagsConfigManager.getConfig();

            StrayTagsConfig defaultConfig = StrayTagsConfig.createDefault();
            ServerConfig defaultServerConfig = new ServerConfig();

            String previewUsername;
            Minecraft client = Minecraft.getInstance();
            previewUsername = client.getUser().getName();
            final String playerName = previewUsername;

            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Component.translatable("straytags.config.title"))
                    .setSavingRunnable(StrayTagsConfigManager::save);

            ConfigEntryBuilder entryBuilder = builder.entryBuilder();

            ConfigCategory general = builder.getOrCreateCategory(
                    Component.translatable("straytags.config.category.general"));

            general.addEntry(entryBuilder.startBooleanToggle(
                            Component.translatable("straytags.config.enabled"), config.enabled)
                    .setDefaultValue(defaultConfig.enabled)
                    .setTooltip(Component.translatable("straytags.config.enabled.tooltip"))
                    .setSaveConsumer(val -> config.enabled = val)
                    .build());

            general.addEntry(entryBuilder.startBooleanToggle(
                            Component.translatable("straytags.config.debug_commands"), config.debugCommandsEnabled)
                    .setDefaultValue(defaultConfig.debugCommandsEnabled)
                    .setTooltip(Component.translatable("straytags.config.debug_commands.tooltip"))
                    .setSaveConsumer(val -> config.debugCommandsEnabled = val)
                    .build());

            for (String whitelisted : config.serverWhitelist) {
                if (whitelisted == null || whitelisted.isBlank()) continue;
                if (!config.serverConfigs.containsKey(whitelisted)) {
                    config.serverConfigs.put(whitelisted, new ServerConfig());
                }
            }

            general.addEntry(entryBuilder.startStrList(
                            Component.translatable("straytags.config.servers"), config.serverWhitelist)
                    .setDefaultValue(defaultConfig.serverWhitelist)
                    .setTooltip(Component.translatable("straytags.config.servers.tooltip"))
                    .setSaveConsumer(val -> {
                        config.serverWhitelist.clear();
                        config.serverWhitelist.addAll(val);
                    })
                    .build());

            for (Map.Entry<String, ServerConfig> entry : config.serverConfigs.entrySet()) {
                String serverKey = entry.getKey();
                ServerConfig sc = entry.getValue();

                ServerConfig defaults;
                if (serverKey.equals("stray.gg")) {
                    defaults = ServerConfig.createDefaultStrayConfig();
                } else {
                    defaults = defaultServerConfig;
                }

                String displayName = serverKey.equals("__default__") ? "Default (Fallback)" : serverKey;

                ConfigCategory clansCategory = builder.getOrCreateCategory(
                        Component.literal("§b" + displayName + " §7- Clans"));

                clansCategory.addEntry(entryBuilder.startStrList(
                                Component.translatable("straytags.config.own_clans"), sc.ownClans)
                        .setDefaultValue(new ArrayList<>(defaults.ownClans))
                        .setTooltip(Component.translatable("straytags.config.own_clans.tooltip"))
                        .setSaveConsumer(val -> {
                            sc.ownClans.clear();
                            sc.ownClans.addAll(val);
                        })
                        .build());

                clansCategory.addEntry(entryBuilder.startStrList(
                                Component.translatable("straytags.config.own_players"), sc.ownPlayers)
                        .setDefaultValue(new ArrayList<>(defaults.ownPlayers))
                        .setTooltip(Component.translatable("straytags.config.own_players.tooltip"))
                        .setSaveConsumer(val -> {
                            sc.ownPlayers.clear();
                            sc.ownPlayers.addAll(val);
                        })
                        .build());

                clansCategory.addEntry(entryBuilder.startStrList(
                                Component.translatable("straytags.config.allied_clans"), sc.alliedClans)
                        .setDefaultValue(new ArrayList<>(defaults.alliedClans))
                        .setTooltip(Component.translatable("straytags.config.allied_clans.tooltip"))
                        .setSaveConsumer(val -> {
                            sc.alliedClans.clear();
                            sc.alliedClans.addAll(val);
                        })
                        .build());

                clansCategory.addEntry(entryBuilder.startStrList(
                                Component.translatable("straytags.config.allied_players"), sc.alliedPlayers)
                        .setDefaultValue(new ArrayList<>(defaults.alliedPlayers))
                        .setTooltip(Component.translatable("straytags.config.allied_players.tooltip"))
                        .setSaveConsumer(val -> {
                            sc.alliedPlayers.clear();
                            sc.alliedPlayers.addAll(val);
                        })
                        .build());

                clansCategory.addEntry(entryBuilder.startStrList(
                                Component.translatable("straytags.config.enemy_clans"), sc.enemyClans)
                        .setDefaultValue(new ArrayList<>(defaults.enemyClans))
                        .setTooltip(Component.translatable("straytags.config.enemy_clans.tooltip"))
                        .setSaveConsumer(val -> {
                            sc.enemyClans.clear();
                            sc.enemyClans.addAll(val);
                        })
                        .build());

                clansCategory.addEntry(entryBuilder.startStrList(
                                Component.translatable("straytags.config.enemy_players"), sc.enemyPlayers)
                        .setDefaultValue(new ArrayList<>(defaults.enemyPlayers))
                        .setTooltip(Component.translatable("straytags.config.enemy_players.tooltip"))
                        .setSaveConsumer(val -> {
                            sc.enemyPlayers.clear();
                            sc.enemyPlayers.addAll(val);
                        })
                        .build());

                clansCategory.addEntry(entryBuilder.startTextDescription(
                        Component.literal("")
                ).build());

                clansCategory.addEntry(entryBuilder.startTextDescription(
                        Component.literal("§e§lImport / Export Clans")
                ).build());

                clansCategory.addEntry(entryBuilder.startTextField(
                                Component.translatable("straytags.config.export_clans"),
                                ConfigShareUtil.exportClansOnly(sc))
                        .setDefaultValue("")
                        .setTooltip(
                                Component.translatable("straytags.config.export_clans.tooltip")
                        )
                        .setSaveConsumer(val -> {})
                        .build());

                final ServerConfig scRef = sc;
                clansCategory.addEntry(entryBuilder.startTextField(
                                Component.translatable("straytags.config.import_clans"), "")
                        .setDefaultValue("")
                        .setTooltip(
                                Component.translatable("straytags.config.import_clans.tooltip")
                        )
                        .setSaveConsumer(val -> {
                            if (val != null && !val.isBlank()) {
                                String error = ConfigShareUtil.importConfig(val, scRef);
                                if (error != null) {
                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.player != null) {
                                        mc.player.displayClientMessage(
                                                Component.literal("§c[StrayTags] Import failed: " + error), false);
                                    }
                                }
                            }
                        })
                        .build());

                ConfigCategory formatsCategory = builder.getOrCreateCategory(
                        Component.literal("§d" + displayName + " §7- Formats"));

                addFormatWithPreview(formatsCategory, entryBuilder, playerName,
                        "straytags.config.own_format", sc.ownFormat, defaults.ownFormat,
                        "straytags.config.own_format.tooltip", "OWN",
                        val -> sc.ownFormat = val);

                addFormatWithPreview(formatsCategory, entryBuilder, playerName,
                        "straytags.config.allied_format", sc.alliedFormat, defaults.alliedFormat,
                        "straytags.config.allied_format.tooltip", "ALLIED",
                        val -> sc.alliedFormat = val);

                addFormatWithPreview(formatsCategory, entryBuilder, playerName,
                        "straytags.config.enemy_format", sc.enemyFormat, defaults.enemyFormat,
                        "straytags.config.enemy_format.tooltip", "ENEMY",
                        val -> sc.enemyFormat = val);

                addFormatWithPreview(formatsCategory, entryBuilder, playerName,
                        "straytags.config.neutral_format", sc.neutralFormat, defaults.neutralFormat,
                        "straytags.config.neutral_format.tooltip", "NEUTRAL",
                        val -> sc.neutralFormat = val);

                addFormatWithPreview(formatsCategory, entryBuilder, playerName,
                        "straytags.config.no_clan_format", sc.noClanFormat, defaults.noClanFormat,
                        "straytags.config.no_clan_format.tooltip", "NO CLAN",
                        val -> sc.noClanFormat = val);

                formatsCategory.addEntry(buildExpandedTextField(entryBuilder,
                        sc.namePattern,
                        defaults.namePattern,
                        val -> sc.namePattern = val));

                formatsCategory.addEntry(entryBuilder.startTextDescription(
                        Component.literal("§7§oLeave a format blank to keep the original nametag for that category.")
                ).build());

                formatsCategory.addEntry(entryBuilder.startTextDescription(
                        Component.literal("")
                ).build());

                formatsCategory.addEntry(entryBuilder.startTextDescription(
                        Component.literal("§e§lImport / Export Formats")
                ).build());

                formatsCategory.addEntry(entryBuilder.startTextField(
                                Component.translatable("straytags.config.export_formats"),
                                ConfigShareUtil.exportFormatsOnly(sc))
                        .setDefaultValue("")
                        .setTooltip(
                                Component.translatable("straytags.config.export_formats.tooltip")
                        )
                        .setSaveConsumer(val -> {})
                        .build());

                formatsCategory.addEntry(entryBuilder.startTextField(
                                Component.translatable("straytags.config.import_formats"), "")
                        .setDefaultValue("")
                        .setTooltip(
                                Component.translatable("straytags.config.import_formats.tooltip")
                        )
                        .setSaveConsumer(val -> {
                            if (val != null && !val.isBlank()) {
                                String error = ConfigShareUtil.importConfig(val, scRef);
                                if (error != null) {
                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.player != null) {
                                        mc.player.displayClientMessage(
                                                Component.literal("§c[StrayTags] Import failed: " + error), false);
                                    }
                                }
                            }
                        })
                        .build());

                formatsCategory.addEntry(entryBuilder.startTextDescription(
                        Component.literal("")
                ).build());

                formatsCategory.addEntry(entryBuilder.startTextDescription(
                        Component.literal("§e§lImport / Export Full Server Config")
                ).build());

                formatsCategory.addEntry(entryBuilder.startTextField(
                                Component.translatable("straytags.config.export_full"),
                                ConfigShareUtil.exportConfig(sc))
                        .setDefaultValue("")
                        .setTooltip(
                                Component.translatable("straytags.config.export_full.tooltip")
                        )
                        .setSaveConsumer(val -> {})
                        .build());

                formatsCategory.addEntry(entryBuilder.startTextField(
                                Component.translatable("straytags.config.import_full"), "")
                        .setDefaultValue("")
                        .setTooltip(
                                Component.translatable("straytags.config.import_full.tooltip")
                        )
                        .setSaveConsumer(val -> {
                            if (val != null && !val.isBlank()) {
                                String error = ConfigShareUtil.importConfig(val, scRef);
                                if (error != null) {
                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.player != null) {
                                        mc.player.displayClientMessage(
                                                Component.literal("§c[StrayTags] Import failed: " + error), false);
                                    }
                                }
                            }
                        })
                        .build());
            }

            return builder.build();
        };
    }

    private static void addFormatWithPreview(
            ConfigCategory category,
            ConfigEntryBuilder entryBuilder,
            String playerName,
            String translationKey,
            String currentValue,
            String defaultValue,
            String tooltipKey,
            String categoryLabel,
            Consumer<String> saveConsumer
    ) {
        category.addEntry(entryBuilder.startTextField(
                        Component.translatable(translationKey), currentValue)
                .setDefaultValue(defaultValue)
                .setTooltip(
                        Component.translatable(tooltipKey),
                        Component.literal(""),
                        Component.literal("§7Placeholders: §f%username% %clan%"),
                        Component.literal("§7MiniMessage: §f<color:#hex>text</color> <bold> <italic>"),
                        Component.literal("§7Leave blank to keep original nametag.")
                )
                .setSaveConsumer(saveConsumer)
                .build());

        Component preview;
        if (currentValue == null || currentValue.isBlank()) {
            preview = Component.literal("§7  Preview (" + categoryLabel + "): ")
                    .append(Component.literal("§8(unchanged - blank format)"));
        } else {
            try {
                Component rendered = MiniMessageParser.parse(currentValue, playerName, "CLAN");
                MutableComponent line = Component.empty();
                line.append(Component.literal("§7  Preview (" + categoryLabel + "): §f㭗 "));
                line.append(rendered);
                preview = line;
            } catch (Exception e) {
                preview = Component.literal("§7  Preview (" + categoryLabel + "): §c(error parsing format)");
            }
        }

        category.addEntry(entryBuilder.startTextDescription(preview).build());
    }

    private static me.shedaniel.clothconfig2.api.AbstractConfigListEntry<?> buildExpandedTextField(
            ConfigEntryBuilder entryBuilder,
            String currentValue,
            String defaultValue,
            Consumer<String> saveConsumer
    ) {
        return entryBuilder.startTextField(
                        Component.translatable("straytags.config.name_pattern"), currentValue)
                .setDefaultValue(defaultValue)
                .setTooltip(
                        Component.translatable("straytags.config.name_pattern.tooltip"),
                        Component.literal(""),
                        Component.literal("§7Placeholders: §f%username% %clan%"),
                        Component.literal("§7MiniMessage: §f<color:#hex>text</color> <bold> <italic>"),
                        Component.literal("§7Leave blank to keep original nametag.")
                )
                .setSaveConsumer(saveConsumer)
                .build();
    }
}