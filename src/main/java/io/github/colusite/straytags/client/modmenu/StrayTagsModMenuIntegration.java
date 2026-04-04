package io.github.colusite.straytags.client.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.colusite.straytags.client.config.ServerConfig;
import io.github.colusite.straytags.client.config.StrayTagsConfig;
import io.github.colusite.straytags.client.config.StrayTagsConfigManager;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Map;

public class StrayTagsModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            StrayTagsConfig config = StrayTagsConfigManager.getConfig();

            StrayTagsConfig defaultConfig = StrayTagsConfig.createDefault();
            ServerConfig defaultServerConfig = new ServerConfig();

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
                        Component.literal("§b" + displayName + " §7— Clans"));

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

                ConfigCategory formatsCategory = builder.getOrCreateCategory(
                        Component.literal("§d" + displayName + " §7- Formats"));

                formatsCategory.addEntry(buildExpandedTextField(entryBuilder,
                        "straytags.config.own_format", sc.ownFormat,
                        defaults.ownFormat,
                        "straytags.config.own_format.tooltip",
                        val -> sc.ownFormat = val));

                formatsCategory.addEntry(buildExpandedTextField(entryBuilder,
                        "straytags.config.allied_format", sc.alliedFormat,
                        defaults.alliedFormat,
                        "straytags.config.allied_format.tooltip",
                        val -> sc.alliedFormat = val));

                formatsCategory.addEntry(buildExpandedTextField(entryBuilder,
                        "straytags.config.enemy_format", sc.enemyFormat,
                        defaults.enemyFormat,
                        "straytags.config.enemy_format.tooltip",
                        val -> sc.enemyFormat = val));

                formatsCategory.addEntry(buildExpandedTextField(entryBuilder,
                        "straytags.config.neutral_format", sc.neutralFormat,
                        defaults.neutralFormat,
                        "straytags.config.neutral_format.tooltip",
                        val -> sc.neutralFormat = val));

                formatsCategory.addEntry(buildExpandedTextField(entryBuilder,
                        "straytags.config.no_clan_format", sc.noClanFormat,
                        defaults.noClanFormat,
                        "straytags.config.no_clan_format.tooltip",
                        val -> sc.noClanFormat = val));

                formatsCategory.addEntry(buildExpandedTextField(entryBuilder,
                        "straytags.config.name_pattern", sc.namePattern,
                        defaults.namePattern,
                        "straytags.config.name_pattern.tooltip",
                        val -> sc.namePattern = val));

                formatsCategory.addEntry(entryBuilder.startTextDescription(
                        Component.literal("§7§oLeave a format blank to keep the original nametag for that category.")
                ).build());
            }

            return builder.build();
        };
    }

    private static me.shedaniel.clothconfig2.api.AbstractConfigListEntry<?> buildExpandedTextField(
            ConfigEntryBuilder entryBuilder,
            String translationKey,
            String currentValue,
            String defaultValue,
            String tooltipKey,
            java.util.function.Consumer<String> saveConsumer
    ) {
        return entryBuilder.startTextField(
                        Component.translatable(translationKey), currentValue)
                .setDefaultValue(defaultValue)
                .setTooltip(
                        Component.translatable(tooltipKey),
                        Component.literal(""),
                        Component.literal("§7Placeholders: §f%username% %clan% %rank%"),
                        Component.literal("§7MiniMessage: §f<color:#hex>text</color> <bold> <italic>"),
                        Component.literal("§7Leave blank to keep original nametag.")
                )
                .setSaveConsumer(saveConsumer)
                .build();
    }
}