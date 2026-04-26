package io.github.colusite.straytags.client.modmenu;

import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.ListOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.isxander.yacl3.gui.YACLScreen;
import io.github.colusite.straytags.client.compat.IconFont;
import io.github.colusite.straytags.client.config.ConfigShareUtil;
import io.github.colusite.straytags.client.config.ServerConfig;
import io.github.colusite.straytags.client.config.StrayTagsConfig;
import io.github.colusite.straytags.client.config.StrayTagsConfigManager;
import io.github.colusite.straytags.client.config.TagCategory;
import io.github.colusite.straytags.client.minimessage.MiniMessageParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StrayTagsConfigScreenBuilder {

    public static Screen create(Screen parent) {
        return create(parent, -1);
    }

    private static Screen create(Screen parent, int targetTabIndex) {
        StrayTagsConfig config = StrayTagsConfigManager.getConfig();
        boolean advanced = config.advancedMode;

        Minecraft client = Minecraft.getInstance();
        final String playerName = client.getUser().getName();

        YetAnotherConfigLib.Builder yaclBuilder = YetAnotherConfigLib.createBuilder()
                .title(Component.literal("StrayTags"));

        if (targetTabIndex >= 0) {
            yaclBuilder.screenInit(screen -> {
                if (screen.tabNavigationBar != null
                        && targetTabIndex < screen.tabNavigationBar.getTabs().size()) {
                    screen.tabNavigationBar.selectTab(targetTabIndex, false);
                }
            });
        }

        // General
        ConfigCategory.Builder generalCat = ConfigCategory.createBuilder()
                .name(Component.literal("General"));

        generalCat.option(Option.<Boolean>createBuilder()
                .name(Component.literal("Enabled"))
                .description(OptionDescription.of(Component.literal("Enable or disable StrayTags")))
                .binding(true, () -> config.enabled, v -> config.enabled = v)
                .controller(TickBoxControllerBuilder::create).build());

        generalCat.option(Option.<Boolean>createBuilder()
                .name(Component.literal("Enable Debug Commands"))
                .description(OptionDescription.of(Component.literal(
                        "Enable /straytags test, testuser, verbose, and debug commands.")))
                .binding(false, () -> config.debugCommandsEnabled, v -> config.debugCommandsEnabled = v)
                .controller(TickBoxControllerBuilder::create).build());

        generalCat.option(Option.<Boolean>createBuilder()
                .name(Component.literal("Enable Advanced Options"))
                .description(OptionDescription.of(Component.literal(
                        "Show advanced options. Save and reopen to apply.")))
                .binding(false, () -> config.advancedMode, v -> config.advancedMode = v)
                .controller(TickBoxControllerBuilder::create).build());

        ListOption<String> serverList = ListOption.<String>createBuilder()
                .name(Component.literal("Server Whitelist"))
                .description(OptionDescription.of(Component.literal("Servers where StrayTags is active")))
                .binding(new ArrayList<>(), () -> new ArrayList<>(config.serverWhitelist), v -> {
                    config.serverWhitelist.clear();
                    config.serverWhitelist.addAll(v);
                })
                .controller(StringControllerBuilder::create)
                .initial("").build();
        generalCat.group(serverList);

        yaclBuilder.category(generalCat.build());

        // Per Server
        for (Map.Entry<String, ServerConfig> entry : config.serverConfigs.entrySet()) {
            String serverKey = entry.getKey();
            final ServerConfig sc = entry.getValue();
            String displayName = serverKey.equals("__default__") ? "Default (Fallback)" : serverKey;

            ServerConfig defaults = serverKey.equals("stray.gg")
                    ? ServerConfig.createDefaultStrayConfig()
                    : new ServerConfig();

            ConfigCategory.Builder serverCat = ConfigCategory.createBuilder()
                    .name(Component.literal(displayName));

            serverCat.option(ButtonOption.createBuilder()
                    .name(Component.literal("§a✚ Add New Category"))
                    .description(OptionDescription.of(Component.literal(
                            "Click to add a new empty category. Edit it after creating.")))
                    .action((screen, opt) -> {
                        sc.categories.add(new TagCategory("New Category", ""));
                        StrayTagsConfigManager.save();
                        rebuild(screen, parent);
                    })
                    .build());

            // Determine active group names from this server's regex
            List<String> regexGroupNames = extractGroupNames(sc.namePattern);

            for (int i = 0; i < sc.categories.size(); i++) {
                final TagCategory cat = sc.categories.get(i);
                final String catId = cat.id;

                MutableComponent groupTitle = renderName(cat.name ).copy();
                if (cat.pendingDelete) {
                    groupTitle.append(Component.literal(" §c§l[PENDING DELETE]"));
                }

                OptionGroup.Builder group = OptionGroup.createBuilder()
                        .name(groupTitle)
                        .description(OptionDescription.of(Component.literal(
                                "Category " + (i + 1) + " of " + sc.categories.size())))
                        .collapsed(false);

                // Name
                group.option(Option.<String>createBuilder()
                        .name(Component.literal("Name"))
                        .description(OptionDescription.of(Component.literal(
                                "Category name. Supports MiniMessage. Example: <gold>Own</gold>.")))
                        .binding(cat.name,
                                () -> {
                                    TagCategory c = sc.findCategoryById(catId);
                                    return c != null ? c.name : "";
                                },
                                v -> {
                                    TagCategory c = sc.findCategoryById(catId);
                                    if (c != null) c.name = v.trim();
                                })
                        .controller(StringControllerBuilder::create).build());

                // Format
                group.option(Option.<String>createBuilder()
                        .name(Component.literal("Format"))
                        .description(buildFormatDescription(cat.format, playerName, regexGroupNames))
                        .binding("",
                                () -> {
                                    TagCategory c = sc.findCategoryById(catId);
                                    return c != null ? c.format : "";
                                },
                                v -> {
                                    TagCategory c = sc.findCategoryById(catId);
                                    if (c != null) c.format = v;
                                })
                        .controller(StringControllerBuilder::create).build());

                group.option(LabelOption.create(buildFormatPreview(cat.format, cat.name, playerName, regexGroupNames)));

                // Match Priority
                group.option(Option.<String>createBuilder()
                        .name(Component.literal("Match Priority"))
                        .description(OptionDescription.of(Component.literal(
                                "Comma-separated list of regex group names in match order. Groups not listed are checked after, in declaration order. Available: " +
                                        String.join(", ", regexGroupNames))))
                        .binding("",
                                () -> {
                                    TagCategory c = sc.findCategoryById(catId);
                                    return c != null ? String.join(", ", c.matchPriority) : "";
                                },
                                v -> {
                                    TagCategory c = sc.findCategoryById(catId);
                                    if (c != null) {
                                        c.matchPriority.clear();
                                        if (!v.isBlank()) {
                                            for (String s : v.split(",")) {
                                                String trimmed = s.trim();
                                                if (!trimmed.isEmpty()) c.matchPriority.add(trimmed);
                                            }
                                        }
                                    }
                                })
                        .controller(StringControllerBuilder::create).build());

                if (advanced) {
                    group.option(Option.<String>createBuilder()
                            .name(Component.literal("§7§o[Adv] §rRegex Matching Override"))
                            .description(OptionDescription.of(Component.literal(
                                    "Custom regex for this category only. Must have named groups. Blank = use server default.")))
                            .binding("",
                                    () -> {
                                        TagCategory c = sc.findCategoryById(catId);
                                        return (c != null && c.namePatternOverride != null) ? c.namePatternOverride : "";
                                    },
                                    v -> {
                                        TagCategory c = sc.findCategoryById(catId);
                                        if (c != null) {
                                            c.namePatternOverride = v.isBlank() ? null : v;
                                        }
                                    })
                            .controller(StringControllerBuilder::create).build());

                    group.option(Option.<String>createBuilder()
                            .name(Component.literal("§7§o[Adv] §rServer Filters"))
                            .description(OptionDescription.of(Component.literal(
                                    "Comma-separated server filters. Dot = exact, no dot = substring. Empty = all servers.")))
                            .binding("",
                                    () -> {
                                        TagCategory c = sc.findCategoryById(catId);
                                        return c != null ? String.join(", ", c.serverFilters) : "";
                                    },
                                    v -> {
                                        TagCategory c = sc.findCategoryById(catId);
                                        if (c != null) {
                                            c.serverFilters.clear();
                                            if (!v.isBlank()) {
                                                for (String s : v.split(",")) {
                                                    String trimmed = s.trim();
                                                    if (!trimmed.isEmpty()) c.serverFilters.add(trimmed);
                                                }
                                            }
                                        }
                                    })
                            .controller(StringControllerBuilder::create).build());
                }

                if (i > 0) {
                    group.option(ButtonOption.createBuilder()
                            .name(Component.literal("§7▲ Move Up"))
                            .description(OptionDescription.of(Component.literal("Move this category up.")))
                            .action((screen, opt) -> {
                                int curIdx = sc.indexOfCategory(catId);
                                if (curIdx > 0) {
                                    TagCategory moving = sc.categories.remove(curIdx);
                                    sc.categories.add(curIdx - 1, moving);
                                    StrayTagsConfigManager.save();
                                    rebuild(screen, parent);
                                }
                            }).build());
                }
                if (i < sc.categories.size() - 1) {
                    group.option(ButtonOption.createBuilder()
                            .name(Component.literal("§7▼ Move Down"))
                            .description(OptionDescription.of(Component.literal("Move this category down.")))
                            .action((screen, opt) -> {
                                int curIdx = sc.indexOfCategory(catId);
                                if (curIdx >= 0 && curIdx < sc.categories.size() - 1) {
                                    TagCategory moving = sc.categories.remove(curIdx);
                                    sc.categories.add(curIdx + 1, moving);
                                    StrayTagsConfigManager.save();
                                    rebuild(screen, parent);
                                }
                            }).build());
                }

                if (!cat.pendingDelete) {
                    group.option(ButtonOption.createBuilder()
                            .name(Component.literal("§c✖ Mark for Deletion"))
                            .description(OptionDescription.of(Component.literal("Click 'Confirm Delete' next to actually delete it.")))
                            .action((screen, opt) -> {
                                TagCategory c = sc.findCategoryById(catId);
                                if (c != null) {
                                    c.pendingDelete = true;
                                    StrayTagsConfigManager.save();
                                    rebuild(screen, parent);
                                }
                            }).build());
                } else {
                    group.option(ButtonOption.createBuilder()
                            .name(Component.literal("§c§l✖ Confirm Delete"))
                            .description(OptionDescription.of(Component.literal("Permanently delete this category.")))
                            .action((screen, opt) -> {
                                int curIdx = sc.indexOfCategory(catId);
                                if (curIdx >= 0) {
                                    sc.categories.remove(curIdx);
                                    StrayTagsConfigManager.save();
                                    rebuild(screen, parent);
                                }
                            }).build());

                    group.option(ButtonOption.createBuilder()
                            .name(Component.literal("§7Cancel Deletion"))
                            .description(OptionDescription.of(Component.literal("Cancel the pending deletion.")))
                            .action((screen, opt) -> {
                                TagCategory c = sc.findCategoryById(catId);
                                if (c != null) {
                                    c.pendingDelete = false;
                                    StrayTagsConfigManager.save();
                                    rebuild(screen, parent);
                                }
                            }).build());
                }

                serverCat.group(group.build());

                for (String groupName : regexGroupNames) {
                    ListOption<String> list = ListOption.<String>createBuilder()
                            .name(Component.literal(groupName))
                            .description(OptionDescription.of(Component.literal(
                                    "Values matching the '" + groupName + "' regex group for this category")))
                            .binding(new ArrayList<>(),
                                    () -> {
                                        TagCategory c = sc.findCategoryById(catId);
                                        return c != null ? new ArrayList<>(c.getGroup(groupName)) : new ArrayList<>();
                                    },
                                    v -> {
                                        TagCategory c = sc.findCategoryById(catId);
                                        if (c != null) {
                                            List<String> target = c.getOrCreateGroup(groupName);
                                            target.clear();
                                            target.addAll(v);
                                        }
                                    })
                            .controller(StringControllerBuilder::create)
                            .initial("")
                            .collapsed(true)
                            .build();
                    serverCat.group(list);
                }
            }

            // Default Formats group
            OptionGroup.Builder formatsGroup = OptionGroup.createBuilder()
                    .name(Component.literal("§6§lDefault Formats"))
                    .description(OptionDescription.of(Component.literal(
                            "Formats applied when no category matches, plus the default name regex.")))
                    .collapsed(true);

            formatsGroup.option(Option.<String>createBuilder()
                    .name(Component.literal("Default Matching Regex"))
                    .description(OptionDescription.of(Component.literal(
                            "Regex to parse nametags. Must have named groups. Their names become the labels for the per-category lists.")))
                    .binding(defaults.namePattern,
                            () -> sc.namePattern,
                            v -> sc.namePattern = !v.isBlank() ? v : defaults.namePattern)
                    .controller(StringControllerBuilder::create).build());

            formatsGroup.option(Option.<String>createBuilder()
                    .name(Component.literal("Neutral Format"))
                    .description(buildFormatDescription(sc.neutralFormat, playerName, regexGroupNames))
                    .binding(defaults.neutralFormat,
                            () -> sc.neutralFormat,
                            v -> sc.neutralFormat = v)
                    .controller(StringControllerBuilder::create).build());
            formatsGroup.option(LabelOption.create(buildFormatPreview(sc.neutralFormat, "Neutral", playerName, regexGroupNames)));

            formatsGroup.option(Option.<String>createBuilder()
                    .name(Component.literal("No Clan Format"))
                    .description(buildFormatDescription(sc.noClanFormat, playerName, regexGroupNames))
                    .binding(defaults.noClanFormat,
                            () -> sc.noClanFormat,
                            v -> sc.noClanFormat = v)
                    .controller(StringControllerBuilder::create).build());
            formatsGroup.option(LabelOption.create(buildFormatPreview(sc.noClanFormat, "No Clan", playerName, regexGroupNames)));

            serverCat.group(formatsGroup.build());

            OptionGroup.Builder ioGroup = OptionGroup.createBuilder()
                    .name(Component.literal("§e§lImport / Export"))
                    .description(OptionDescription.of(Component.literal("Share your config with other players.")))
                    .collapsed(true);

            ioGroup.option(Option.<String>createBuilder()
                    .name(Component.literal("Export Code (copy this)"))
                    .description(OptionDescription.of(Component.literal("Copy this code and send it to another player.")))
                    .binding("", () -> ConfigShareUtil.exportConfig(sc), v -> {})
                    .controller(StringControllerBuilder::create).build());

            ioGroup.option(Option.<String>createBuilder()
                    .name(Component.literal("Import Code (paste here)"))
                    .description(OptionDescription.of(Component.literal("Paste a config code here and apply to import.")))
                    .binding("", () -> "", v -> {
                        if (!v.isBlank()) {
                            String error = ConfigShareUtil.importConfig(v, sc);
                            if (error != null && client.player != null) {
                                client.player.displayClientMessage(
                                        Component.literal("§c[StrayTags] Import failed: " + error), false);
                            }
                            for (TagCategory c : sc.categories) {
                                c.ensureId();
                                c.migrateLegacyLists();
                            }
                        }
                    })
                    .controller(StringControllerBuilder::create).build());

            serverCat.group(ioGroup.build());

            yaclBuilder.category(serverCat.build());
        }

        yaclBuilder.save(() -> {
            for (ServerConfig sc : config.serverConfigs.values()) {
                sc.categories.removeIf(cat -> cat.name == null || cat.name.isBlank());
                for (TagCategory cat : sc.categories) cat.ensureId();
            }
            StrayTagsConfigManager.save();
        });

        return yaclBuilder.build().generateScreen(parent);
    }

    private static void rebuild(YACLScreen screen, Screen parent) {
        int idx = -1;
        try {
            if (screen.tabNavigationBar != null) {
                Tab currentTab = screen.tabManager.getCurrentTab();
                if (currentTab != null) {
                    idx = screen.tabNavigationBar.getTabs().indexOf(currentTab);
                }
            }
        } catch (Exception ignored) {}
        Minecraft.getInstance().setScreen(create(parent, idx));
    }

    private static List<String> extractGroupNames(String pattern) {
        List<String> names = new ArrayList<>();
        if (pattern == null) return names;
        try {
            Pattern groupNameP = Pattern.compile("\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>");
            Matcher m = groupNameP.matcher(pattern);
            while (m.find()) names.add(m.group(1));
        } catch (Exception ignored) {}
        return names;
    }

    private static Component renderName(String name) {
        if (name == null || name.isEmpty()) return Component.literal("§6§l" + "(unnamed)");
        try {
            if (name.contains("<") && name.contains(">")) {
                return MiniMessageParser.parse(name, Map.of());
            }
        } catch (Exception ignored) {}
        return Component.literal("§6§l" + name);
    }

    private static Map<String, String> buildPreviewPlaceholders(String playerName, List<String> regexGroupNames) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < regexGroupNames.size(); i++) {
            String g = regexGroupNames.get(i);
            map.put(g, i == 0 ? playerName : g.toUpperCase());
        }
        // Legacy aliases
        if (!map.containsKey("username")) map.put("username", playerName);
        if (!map.containsKey("clan")) map.put("clan", "CLAN");
        return map;
    }

    private static Component buildFormatPreview(String format, String catName, String playerName, List<String> regexGroupNames) {
        if (format == null || format.isBlank()) {
            return Component.literal("§7Preview: ")
                    .append(Component.literal("§8(unchanged - blank format)"));
        }
        try {
            Component rendered = MiniMessageParser.parse(format, buildPreviewPlaceholders(playerName, regexGroupNames));
            MutableComponent line = Component.empty();
            line.append(Component.literal("§7Preview: "));
            line.append(Component.literal(IconFont.GLYPH).withStyle(IconFont.iconStyle()));
            line.append(Component.literal(" "));
            line.append(rendered);
            return line;
        } catch (Exception e) {
            return Component.literal("§7Preview: §c(error parsing format)");
        }
    }

    private static OptionDescription buildFormatDescription(String currentFormat, String playerName, List<String> regexGroupNames) {
        Component preview;
        if (currentFormat == null || currentFormat.isBlank()) {
            preview = Component.literal("§8(blank - keeps original)");
        } else {
            try {
                preview = MiniMessageParser.parse(currentFormat, buildPreviewPlaceholders(playerName, regexGroupNames));
            } catch (Exception e) {
                preview = Component.literal("§c(error)");
            }
        }
        StringBuilder placeholders = new StringBuilder();
        for (String g : regexGroupNames) {
            if (!placeholders.isEmpty()) placeholders.append(' ');
            placeholders.append("%").append(g).append("%");
        }
        if (placeholders.isEmpty()) placeholders.append("%username% %clan%");

        MutableComponent desc = Component.literal("MiniMessage format. Placeholders: " + placeholders + "\n");
        desc.append(Component.literal("Tags: <color:#hex>...</color> <bold> <italic>\n\n"));
        desc.append(Component.literal("Preview: "));
        desc.append(preview);
        return OptionDescription.of(desc);
    }

    private static String stripMiniTags(String s) {
        if (s == null) return "";
        return s.replaceAll("<[^>]+>", "").trim();
    }
}