package io.github.colusite.straytags.client.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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