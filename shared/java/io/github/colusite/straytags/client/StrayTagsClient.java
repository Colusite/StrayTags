package io.github.colusite.straytags.client;

import io.github.colusite.straytags.client.config.ServerConfig;
import io.github.colusite.straytags.client.config.StrayTagsConfig;
import io.github.colusite.straytags.client.config.StrayTagsConfigManager;
import io.github.colusite.straytags.client.config.TagCategory;
import io.github.colusite.straytags.client.minimessage.MiniMessageParser;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StrayTagsClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("StrayTags");

    public static boolean debugMode = false;
    public static boolean verboseMode = false;

    private static final Set<String> verboseLoggedNames = new HashSet<>();

    // Minecraft formatting codes and special icons
    private static final Pattern FORMATTING_CODE_PATTERN = Pattern.compile("§.|[\uE000-\uF8FF]|[\uDB80-\uDBFF][\uDC00-\uDFFF]");

    @Override
    public void onInitializeClient() {
        StrayTagsConfigManager.load();
        TestCommand.register();
        LOGGER.info("[StrayTags] Initialized! Mod is {}.",
                StrayTagsConfigManager.getConfig().enabled ? "enabled" : "disabled");
    }

    public static String getCurrentServerAddress() {
        Minecraft client = Minecraft.getInstance();
        ServerData serverData = client.getCurrentServer();
        if (serverData != null) {
            return serverData.ip;
        }
        return null;
    }

    public static boolean isActiveOnCurrentServer() {
        StrayTagsConfig config = StrayTagsConfigManager.getConfig();
        if (!config.enabled) return false;
        String serverAddress = getCurrentServerAddress();
        return config.isServerWhitelisted(serverAddress);
    }

    public static ServerConfig getActiveServerConfig() {
        StrayTagsConfig config = StrayTagsConfigManager.getConfig();
        if (!config.enabled) return null;
        String serverAddress = getCurrentServerAddress();
        if (!config.isServerWhitelisted(serverAddress)) return null;
        return config.getServerConfig(serverAddress);
    }

    public static String stripFormattingCodes(String input) {
        if (input == null) return null;
        return FORMATTING_CODE_PATTERN.matcher(input).replaceAll("");
    }

    public static String cleanForMatching(String raw) {
        if (raw == null) return null;
        String cleaned = stripFormattingCodes(raw);
        cleaned = cleaned.replaceAll("[^a-zA-Z0-9_ \\[\\]]", "");
        cleaned = cleaned.strip();
        cleaned = cleaned.replaceAll("\\s{2,}", " ");
        return cleaned;
    }

    public static Component rebuildWithPrefixSuffix(String rawString, String cleanedString, Component modified) {
        if (cleanedString == null || cleanedString.isEmpty()) {
            return modified;
        }

        int rawStart = -1;
        int rawEnd = -1;

        int cleanIdx = 0;
        while (cleanIdx < cleanedString.length() && cleanedString.charAt(cleanIdx) == ' ') {
            cleanIdx++;
        }
        if (cleanIdx >= cleanedString.length()) return modified;

        for (int rawIdx = 0; rawIdx < rawString.length(); rawIdx++) {
            char rawChar = rawString.charAt(rawIdx);

            if (rawChar == '§' && rawIdx + 1 < rawString.length()) {
                rawIdx++;
                continue;
            }

            if (!String.valueOf(rawChar).matches("[a-zA-Z0-9_ \\[\\]]")) {
                continue;
            }

            if (cleanIdx < cleanedString.length() && rawChar == cleanedString.charAt(cleanIdx)) {
                if (rawStart == -1) {
                    rawStart = rawIdx;
                }
                rawEnd = rawIdx + 1;
                cleanIdx++;
            }
        }

        if (rawStart == -1) {
            return modified;
        }

        String rawPrefix = rawString.substring(0, rawStart);
        String rawSuffix = rawString.substring(rawEnd);

        MutableComponent result = Component.empty();
        if (!rawPrefix.isEmpty()) {
            result.append(Component.literal(rawPrefix));
        }
        result.append(modified);
        if (!rawSuffix.isEmpty()) {
            result.append(Component.literal(rawSuffix));
        }

        return result;
    }

    public static void logVerbose(String lineString, String playerName) {
        if (!verboseMode && !debugMode) return;

        String cleaned = cleanForMatching(lineString);
        String key = playerName + ":" + cleaned;
        if (verboseLoggedNames.contains(key)) return;
        verboseLoggedNames.add(key);

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        if (debugMode) {
            client.player.displayClientMessage(
                    Component.literal("§7[Debug] Player: §f" + playerName
                            + " §7Raw: §f'" + lineString + "'"
                            + " §7Clean: §f'" + cleaned + "'"), false);
        }

        if (verboseMode) {
            ServerConfig serverConfig = getActiveServerConfig();
            if (serverConfig == null) return;

            try {
                Pattern pattern = Pattern.compile(serverConfig.namePattern);
                Matcher matcher = pattern.matcher(cleaned);

                if (!matcher.matches()) {
                    client.player.displayClientMessage(
                            Component.literal("§e[ST] §cNO MATCH §7'" + cleaned + "'"), false);
                } else {
                    String username = safeGroup(matcher, "username");
                    String clan = safeGroup(matcher, "clan");

                    String format = serverConfig.getFormat(username, clan, getCurrentServerAddress());
                    TagCategory cat = serverConfig.findCategory(username, clan, getCurrentServerAddress());
                    String catName = cat != null ? cat.name : (clan != null && !clan.isEmpty() ? "NEUTRAL" : "NO_CLAN");

                    if (format == null || format.isBlank()) {
                        client.player.displayClientMessage(
                                Component.literal("§e[ST] §7SKIP (blank format) §f'" + cleaned + "'"), false);
                        return;
                    }

                    client.player.displayClientMessage(
                            Component.literal("§e[ST] §aMATCH §7'" + cleaned + "'"), false);
                    client.player.displayClientMessage(
                            Component.literal("§e[ST]   §7user=§f" + (username != null ? username : "(none)")
                                    + " §7clan=§f" + (clan != null ? clan : "(none)")), false);
                    client.player.displayClientMessage(
                            Component.literal("§e[ST]   §7cat=§f" + catName
                                    + " §7fmt=§f" + format), false);
                }
            } catch (Exception ignored) {}
        }
    }

    public static void clearVerboseCache() {
        verboseLoggedNames.clear();
    }

    public static Component processDisplayName(Component original) {
        if (original == null) return null;

        ServerConfig serverConfig = getActiveServerConfig();
        if (serverConfig == null) return null;

        String rawString = original.getString();
        if (rawString.isEmpty()) return null;

        String plainName = cleanForMatching(rawString);
        if (plainName == null || plainName.isEmpty()) return null;

        String serverAddress = getCurrentServerAddress();

        try {
            for (TagCategory cat : serverConfig.categories) {
                if (!cat.appliesToServer(serverAddress)) continue;
                String override = cat.getEffectiveNamePattern();
                if (override == null) continue;

                Pattern catPattern = Pattern.compile(override);
                Matcher catMatcher = catPattern.matcher(plainName);
                if (!catMatcher.matches()) continue;

                java.util.Map<String, String> matches = extractAllNamedGroups(catPattern, catMatcher);
                if (matches.isEmpty()) continue;

                List<String> regexGroupNames = new java.util.ArrayList<>(matches.keySet());
                List<String> order = cat.effectiveMatchOrder(regexGroupNames);
                boolean hit = false;
                for (String groupName : order) {
                    String matched = matches.get(groupName);
                    if (matched == null || matched.isEmpty()) continue;
                    for (String entry : cat.getGroup(groupName)) {
                        if (entry.equalsIgnoreCase(matched)) { hit = true; break; }
                    }
                    if (hit) break;
                }

                if (hit) {
                    String format = cat.format;
                    if (format == null || format.isBlank()) return null;
                    return MiniMessageParser.parse(format, matches);
                }
            }

            Pattern defaultPattern = Pattern.compile(serverConfig.namePattern);
            Matcher defaultMatcher = defaultPattern.matcher(plainName);
            if (!defaultMatcher.matches()) return null;

            java.util.Map<String, String> matches = extractAllNamedGroups(defaultPattern, defaultMatcher);
            if (matches.isEmpty()) return null;

            List<String> regexGroupNames = new java.util.ArrayList<>(matches.keySet());
            TagCategory matchingCat = serverConfig.findCategory(matches, regexGroupNames, serverAddress);

            String format;
            if (matchingCat != null) {
                format = matchingCat.format;
            } else {
                String secondGroup = regexGroupNames.size() >= 2 ? matches.get(regexGroupNames.get(1)) : null;
                format = (secondGroup == null || secondGroup.isEmpty())
                        ? serverConfig.noClanFormat
                        : serverConfig.neutralFormat;
            }

            if (format == null || format.isBlank()) return null;
            return MiniMessageParser.parse(format, matches);
        } catch (Exception e) {
            LOGGER.debug("[StrayTags] Failed to process display name '{}': {}", plainName, e.getMessage());
            return null;
        }
    }

    private static java.util.Map<String, String> extractAllNamedGroups(Pattern pattern, Matcher matcher) {
        java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
        try {
            Pattern groupNamePattern = Pattern.compile("\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>");
            Matcher gm = groupNamePattern.matcher(pattern.pattern());
            while (gm.find()) {
                String groupName = gm.group(1);
                try {
                    String val = matcher.group(groupName);
                    if (val != null) result.put(groupName, val);
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (Exception ignored) {}
        return result;
    }

    private static String safeGroup(Matcher matcher, String groupName) {
        try {
            return matcher.group(groupName);
        } catch (IllegalArgumentException e) {
            try {
                String pattern = matcher.pattern().pattern();
                java.util.regex.Pattern groupNameP = java.util.regex.Pattern.compile("\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>");
                Matcher gm = groupNameP.matcher(pattern);
                java.util.List<String> names = new java.util.ArrayList<>();
                while (gm.find()) names.add(gm.group(1));
                int idx = "username".equals(groupName) ? 0 : ("clan".equals(groupName) ? 1 : -1);
                if (idx >= 0 && idx < names.size()) {
                    try {
                        return matcher.group(names.get(idx));
                    } catch (IllegalArgumentException ignored) {}
                }
            } catch (Exception ignored) {}
            return null;
        }
    }
}