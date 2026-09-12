package io.github.colusite.straytags.client.config;

import io.github.colusite.straytags.client.username.UsernameCache;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;


public class TagCategory {

    public String id;
    public String name;
    public String format;

    // Map of regex-group-name -> list of values to match against that group.
    // e.g {"username": ["Colusite", "Kylaz"], "clan": ["Latte"]}.
    public Map<String, List<String>> groupValues = new LinkedHashMap<>();

    // Ordered list of group names, first match wins
    public List<String> matchPriority = new ArrayList<>();

    public List<String> serverFilters = new ArrayList<>();

    public String namePatternOverride;

    // Legacy fields
    public List<String> clans;
    public List<String> players;

    public transient boolean pendingDelete = false;

    // O(1) lookup caches, rebuilt when the config generation changes.
    // Non-UUID groups: lowercased entries.
    private transient Map<String, Set<String>> literalLookup;
    // UUID groups: normalized-UUID entries (both direct UUIDs and name→UUID resolutions).
    private transient Map<String, Set<String>> uuidLookup;
    // UUID groups: lowercased name entries (for playerName direct match).
    private transient Map<String, Set<String>> uuidNameLookup;
    private transient long lookupStamp = Long.MIN_VALUE;

    public TagCategory() {
        this.id = UUID.randomUUID().toString();
        this.name = "Unnamed";
        this.format = "";
    }

    public TagCategory(String name, String format) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.format = format;
    }

    public boolean ensureId() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
            return true;
        }
        return false;
    }

    public boolean migrateLegacyLists() {
        boolean changed = false;
        if (groupValues == null) {
            groupValues = new LinkedHashMap<>();
            changed = true;
        }
        if (players != null) {
            if (!groupValues.containsKey("username") && !players.isEmpty()) {
                groupValues.put("username", new ArrayList<>(players));
                changed = true;
            }
            players = null;
        }
        if (clans != null) {
            if (!groupValues.containsKey("clan") && !clans.isEmpty()) {
                groupValues.put("clan", new ArrayList<>(clans));
                changed = true;
            }
            clans = null;
        }
        return changed;
    }

    public List<String> getOrCreateGroup(String groupName) {
        if (groupValues == null) groupValues = new LinkedHashMap<>();
        return groupValues.computeIfAbsent(groupName, k -> new ArrayList<>());
    }

    public List<String> getGroup(String groupName) {
        if (groupValues == null) return List.of();
        List<String> list = groupValues.get(groupName);
        return list != null ? list : List.of();
    }

    public boolean appliesToServer(String serverAddress) {
        if (serverFilters == null || serverFilters.isEmpty()) return true;
        if (serverAddress == null) return true;
        String lower = serverAddress.toLowerCase();
        for (String filter : serverFilters) {
            if (filter == null || filter.isBlank()) continue;
            String f = filter.toLowerCase().trim();
            if (f.contains(".")) {
                if (lower.equals(f)) return true;
            } else {
                if (lower.contains(f)) return true;
            }
        }
        return false;
    }

    public String getEffectiveNamePattern() {
        if (namePatternOverride != null && !namePatternOverride.isBlank()) {
            return namePatternOverride;
        }
        return null;
    }

    public List<String> effectiveMatchOrder(List<String> regexGroupNames) {
        List<String> result = new ArrayList<>();
        if (matchPriority != null) {
            for (String g : matchPriority) {
                if (regexGroupNames.contains(g) && !result.contains(g)) {
                    result.add(g);
                }
            }
        }
        for (String g : regexGroupNames) {
            if (!result.contains(g)) result.add(g);
        }
        return result;
    }

    // Rebuild the O(1) lookup structures if the config generation has changed.
    // Called from the render hot path — must be cheap when already up to date.
    public void ensureLookup(ServerConfig serverConfig, long generation) {
        if (lookupStamp == generation && literalLookup != null) return;
        Map<String, Set<String>> literal = new LinkedHashMap<>();
        Map<String, Set<String>> uuid = new LinkedHashMap<>();
        Map<String, Set<String>> uuidNames = new LinkedHashMap<>();
        if (groupValues != null) {
            UsernameCache cache = UsernameCache.getInstance();
            for (Map.Entry<String, List<String>> e : groupValues.entrySet()) {
                String groupName = e.getKey();
                List<String> entries = e.getValue();
                if (entries == null || entries.isEmpty()) continue;
                boolean isUuid = serverConfig != null && serverConfig.isUuidGroup(groupName);
                if (isUuid) {
                    Set<String> uuidSet = new HashSet<>();
                    Set<String> nameSet = new HashSet<>();
                    for (String entry : entries) {
                        if (entry == null || entry.isEmpty()) continue;
                        String norm = UsernameCache.normalizeUuid(entry);
                        if (norm != null) {
                            uuidSet.add(norm);
                        } else {
                            String lower = entry.toLowerCase(Locale.ROOT);
                            nameSet.add(lower);
                            Optional<String> resolved = cache.getUuid(entry);
                            resolved.ifPresent(uuidSet::add);
                        }
                    }
                    uuid.put(groupName, uuidSet);
                    uuidNames.put(groupName, nameSet);
                } else {
                    Set<String> set = new HashSet<>();
                    for (String entry : entries) {
                        if (entry == null || entry.isEmpty()) continue;
                        set.add(entry.toLowerCase(Locale.ROOT));
                    }
                    literal.put(groupName, set);
                }
            }
        }
        this.literalLookup = literal;
        this.uuidLookup = uuid;
        this.uuidNameLookup = uuidNames;
        this.lookupStamp = generation;
    }

    // O(1) membership for non-UUID group values. `matched` is compared case-insensitively.
    public boolean literalMatches(String groupName, String matched) {
        if (literalLookup == null || matched == null) return false;
        Set<String> set = literalLookup.get(groupName);
        if (set == null || set.isEmpty()) return false;
        return set.contains(matched.toLowerCase(Locale.ROOT));
    }

    // O(1) membership for UUID groups. `normalizedPlayerUuid` is the dashed-lowercase form.
    public boolean uuidMatches(String groupName, String normalizedPlayerUuid) {
        if (uuidLookup == null || normalizedPlayerUuid == null) return false;
        Set<String> set = uuidLookup.get(groupName);
        return set != null && set.contains(normalizedPlayerUuid);
    }

    // O(1) name-fallback match for UUID groups (config lists a plain name; server sends that name).
    public boolean uuidNameMatches(String groupName, String playerName) {
        if (uuidNameLookup == null || playerName == null) return false;
        Set<String> set = uuidNameLookup.get(groupName);
        if (set == null || set.isEmpty()) return false;
        return set.contains(playerName.toLowerCase(Locale.ROOT));
    }

    public TagCategory copy() {
        TagCategory copy = new TagCategory(this.name, this.format);
        copy.id = this.id;
        copy.serverFilters.addAll(this.serverFilters);
        copy.namePatternOverride = this.namePatternOverride;
        copy.matchPriority.addAll(this.matchPriority);
        if (this.groupValues != null) {
            for (Map.Entry<String, List<String>> e : this.groupValues.entrySet()) {
                copy.groupValues.put(e.getKey(), new ArrayList<>(e.getValue()));
            }
        }
        return copy;
    }
}