package io.github.colusite.straytags.client;

import io.github.colusite.straytags.client.compat.ChatCompat;
import io.github.colusite.straytags.client.config.ServerConfig;
import io.github.colusite.straytags.client.config.StrayTagsConfig;
import io.github.colusite.straytags.client.config.StrayTagsConfigManager;
import io.github.colusite.straytags.client.config.TagCategory;
import io.github.colusite.straytags.client.minimessage.MiniMessageParser;
import io.github.colusite.straytags.client.username.UsernameCache;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StrayTagsClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("StrayTags");

    public static boolean debugMode = false;
    public static boolean verboseMode = false;

    private static final Set<String> verboseLoggedNames = new HashSet<>();

    // Regex compilation is expensive. Cache compiled patterns by source string.
    private static final int MAX_PATTERN_CACHE = 64;
    private static final Map<String, Pattern> PATTERN_CACHE =
            Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Pattern> eldest) {
                    return size() > MAX_PATTERN_CACHE;
                }
            });
    // Extracted named-group names per pattern source.
    private static final Map<String, List<String>> GROUP_NAMES_CACHE =
            Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<String>> eldest) {
                    return size() > MAX_PATTERN_CACHE;
                }
            });
    private static final Pattern GROUP_NAME_PATTERN =
            Pattern.compile("\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>");

    // Rendered nametags rarely change frame-to-frame. Cache the last processed
    // result per player so we skip regex/lookup work on repeat calls.
    private static final class ProcessedResult {
        final String rawString;
        final Component processed; // null == processDisplayName returned null
        ProcessedResult(String raw, Component p) { this.rawString = raw; this.processed = p; }
    }
    private static final Map<UUID, ProcessedResult> RENDER_CACHE = new ConcurrentHashMap<>();
    private static final LinkedList<UUID> RENDER_CACHE_ORDER = new LinkedList<>();
    private static final int MAX_RENDER_CACHE = 512;
    private static volatile long renderCacheGeneration = Long.MIN_VALUE;

    private static Pattern getPattern(String source) {
        if (source == null) return null;
        Pattern p = PATTERN_CACHE.get(source);
        if (p != null) return p;
        p = Pattern.compile(source);
        PATTERN_CACHE.put(source, p);
        return p;
    }

    private static List<String> getGroupNames(String patternSource) {
        List<String> names = GROUP_NAMES_CACHE.get(patternSource);
        if (names != null) return names;
        names = new ArrayList<>();
        Matcher gm = GROUP_NAME_PATTERN.matcher(patternSource);
        while (gm.find()) names.add(gm.group(1));
        GROUP_NAMES_CACHE.put(patternSource, names);
        return names;
    }

    private static void checkRenderCacheGeneration() {
        long current = StrayTagsConfigManager.getGeneration();
        if (renderCacheGeneration != current) {
            RENDER_CACHE.clear();
            synchronized (RENDER_CACHE_ORDER) {
                RENDER_CACHE_ORDER.clear();
            }
            renderCacheGeneration = current;
        }
    }

    private static void putRenderCache(UUID uuid, ProcessedResult result) {
        RENDER_CACHE.put(uuid, result);
        synchronized (RENDER_CACHE_ORDER) {
            RENDER_CACHE_ORDER.remove(uuid);
            RENDER_CACHE_ORDER.addLast(uuid);
            while (RENDER_CACHE_ORDER.size() > MAX_RENDER_CACHE) {
                UUID evict = RENDER_CACHE_ORDER.removeFirst();
                RENDER_CACHE.remove(evict);
            }
        }
    }

    // Minecraft formatting codes and special icons
    private static final Pattern FORMATTING_CODE_PATTERN = Pattern.compile("§.|[\uE000-\uF8FF]|[\uDB80-\uDBFF][\uDC00-\uDFFF]");

    @Override
    public void onInitializeClient() {
        StrayTagsConfigManager.load();
        UsernameCache.getInstance();
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
        // Single-pass char scan: strip §-codes, private-use / supplementary
        // icons, disallowed chars, and collapse whitespace runs. Equivalent to
        // the previous regex-based pipeline but avoids all regex compilation.
        int n = raw.length();
        StringBuilder sb = new StringBuilder(n);
        boolean lastSpace = true; // treat start-of-string like whitespace, so leading spaces get skipped
        int i = 0;
        while (i < n) {
            char c = raw.charAt(i);
            if (c == '§' && i + 1 < n) { // § formatting code
                i += 2;
                continue;
            }
            // Skip private-use area characters (used for MC custom icons).
            if (c >= '' && c <= '') {
                i++;
                continue;
            }
            // Skip supplementary-plane surrogate pairs in the DB80–DBFF range.
            if (c >= '\uDB80' && c <= '\uDBFF' && i + 1 < n) {
                char c2 = raw.charAt(i + 1);
                if (c2 >= '\uDC00' && c2 <= '\uDFFF') {
                    i += 2;
                    continue;
                }
            }
            boolean allowed = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '[' || c == ']' || c == ' ';
            if (!allowed) {
                i++;
                continue;
            }
            if (c == ' ') {
                if (lastSpace) { i++; continue; }
                sb.append(' ');
                lastSpace = true;
            } else {
                sb.append(c);
                lastSpace = false;
            }
            i++;
        }
        // Trim trailing space.
        int len = sb.length();
        if (len > 0 && sb.charAt(len - 1) == ' ') sb.setLength(len - 1);
        return sb.toString();
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
            ChatCompat.sendSystem(client.player,
                    Component.literal("§7[Debug] Player: §f" + playerName
                            + " §7Raw: §f'" + lineString + "'"
                            + " §7Clean: §f'" + cleaned + "'"));
        }

        if (verboseMode) {
            ServerConfig serverConfig = getActiveServerConfig();
            if (serverConfig == null) return;

            try {
                Pattern pattern = getPattern(serverConfig.namePattern);
                Matcher matcher = pattern.matcher(cleaned);

                if (!matcher.matches()) {
                    ChatCompat.sendSystem(client.player,
                            Component.literal("§e[ST] §cNO MATCH §7'" + cleaned + "'"));
                } else {
                    String username = safeGroup(matcher, "username");
                    String clan = safeGroup(matcher, "clan");

                    String format = serverConfig.getFormat(username, clan, getCurrentServerAddress());
                    TagCategory cat = serverConfig.findCategory(username, clan, getCurrentServerAddress());
                    String catName = cat != null ? cat.name : (clan != null && !clan.isEmpty() ? "NEUTRAL" : "NO_CLAN");

                    if (format == null || format.isBlank()) {
                        ChatCompat.sendSystem(client.player,
                                Component.literal("§e[ST] §7SKIP (blank format) §f'" + cleaned + "'"));
                        return;
                    }

                    ChatCompat.sendSystem(client.player,
                            Component.literal("§e[ST] §aMATCH §7'" + cleaned + "'"));
                    ChatCompat.sendSystem(client.player,
                            Component.literal("§e[ST]   §7user=§f" + (username != null ? username : "(none)")
                                    + " §7clan=§f" + (clan != null ? clan : "(none)")));
                    ChatCompat.sendSystem(client.player,
                            Component.literal("§e[ST]   §7cat=§f" + catName
                                    + " §7fmt=§f" + format));
                }
            } catch (Exception ignored) {}
        }
    }

    public static void clearVerboseCache() {
        verboseLoggedNames.clear();
    }

    public static Component processDisplayName(Component original, UUID playerUuid, String playerName) {
        if (original == null) return null;

        ServerConfig serverConfig = getActiveServerConfig();
        if (serverConfig == null) return null;

        String rawString = original.getString();
        if (rawString.isEmpty()) return null;

        // Fast path: if we've already processed this exact rawString for this
        // player and the config hasn't changed, reuse the result.
        checkRenderCacheGeneration();
        if (playerUuid != null) {
            ProcessedResult cached = RENDER_CACHE.get(playerUuid);
            if (cached != null && cached.rawString.equals(rawString)) {
                return cached.processed;
            }
        }

        String plainName = cleanForMatching(rawString);
        if (plainName == null || plainName.isEmpty()) {
            if (playerUuid != null) putRenderCache(playerUuid, new ProcessedResult(rawString, null));
            return null;
        }

        if (playerUuid != null && playerName != null && !playerName.isEmpty()) {
            UsernameCache.getInstance().observePlayer(playerUuid, playerName);
        }

        String serverAddress = getCurrentServerAddress();
        long generation = StrayTagsConfigManager.getGeneration();

        Component result = null;
        try {
            for (TagCategory cat : serverConfig.categories) {
                if (!cat.appliesToServer(serverAddress)) continue;
                String override = cat.getEffectiveNamePattern();
                if (override == null) continue;

                Pattern catPattern = getPattern(override);
                Matcher catMatcher = catPattern.matcher(plainName);
                if (!catMatcher.matches()) continue;

                java.util.Map<String, String> matches = extractAllNamedGroups(override, catMatcher);
                if (matches.isEmpty()) continue;

                List<String> regexGroupNames = new ArrayList<>(matches.keySet());
                List<String> order = cat.effectiveMatchOrder(regexGroupNames);
                cat.ensureLookup(serverConfig, generation);
                if (categoryMatchesPlayer(cat, serverConfig, matches, order, playerUuid, playerName)) {
                    String format = cat.format;
                    if (format != null && !format.isBlank()) {
                        result = MiniMessageParser.parse(format, matches);
                    }
                    if (playerUuid != null) putRenderCache(playerUuid, new ProcessedResult(rawString, result));
                    return result;
                }
            }

            Pattern defaultPattern = getPattern(serverConfig.namePattern);
            Matcher defaultMatcher = defaultPattern.matcher(plainName);
            if (!defaultMatcher.matches()) {
                if (playerUuid != null) putRenderCache(playerUuid, new ProcessedResult(rawString, null));
                return null;
            }

            java.util.Map<String, String> matches = extractAllNamedGroups(serverConfig.namePattern, defaultMatcher);
            if (matches.isEmpty()) {
                if (playerUuid != null) putRenderCache(playerUuid, new ProcessedResult(rawString, null));
                return null;
            }

            List<String> regexGroupNames = new ArrayList<>(matches.keySet());
            TagCategory matchingCat = serverConfig.findCategory(matches, regexGroupNames, serverAddress, playerUuid, playerName);

            String format;
            if (matchingCat != null) {
                format = matchingCat.format;
            } else {
                String secondGroup = regexGroupNames.size() >= 2 ? matches.get(regexGroupNames.get(1)) : null;
                format = (secondGroup == null || secondGroup.isEmpty())
                        ? serverConfig.noClanFormat
                        : serverConfig.neutralFormat;
            }

            if (format != null && !format.isBlank()) {
                result = MiniMessageParser.parse(format, matches);
            }
            if (playerUuid != null) putRenderCache(playerUuid, new ProcessedResult(rawString, result));
            return result;
        } catch (Exception e) {
            LOGGER.debug("[StrayTags] Failed to process display name '{}': {}", plainName, e.getMessage());
            return null;
        }
    }

    static boolean categoryMatchesPlayer(TagCategory cat, ServerConfig serverConfig,
                                         java.util.Map<String, String> matches, List<String> order,
                                         UUID playerUuid, String playerName) {
        String playerUuidStr = playerUuid != null ? playerUuid.toString().toLowerCase(Locale.ROOT) : null;
        for (String groupName : order) {
            String matched = matches.get(groupName);
            if (matched == null || matched.isEmpty()) continue;
            if (serverConfig.isUuidGroup(groupName)) {
                if (cat.uuidMatches(groupName, playerUuidStr)) return true;
                if (cat.uuidNameMatches(groupName, playerName)) return true;
            } else {
                if (cat.literalMatches(groupName, matched)) return true;
            }
        }
        return false;
    }

    private static java.util.Map<String, String> extractAllNamedGroups(String patternSource, Matcher matcher) {
        java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
        try {
            for (String groupName : getGroupNames(patternSource)) {
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