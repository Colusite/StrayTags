package io.github.colusite.straytags.client.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ServerConfig {

    // User defined categories
    public List<TagCategory> categories = new ArrayList<>();

    // Players in no matching category that matches the namePattern regex
    public String neutralFormat = "";
    // Players in no matching category that doesn't match the regex
    public String noClanFormat = "";

    public String namePattern = "^(?<Usernames>\\S+)(?:\\s+\\[(?<Clans>[^\\]]+)\\])?$";

    // Legacy fields
    // Gson will populate these from old configs; migration happens in ConfigManager
    public List<String> ownClans;
    public List<String> ownPlayers;
    public List<String> alliedClans;
    public List<String> alliedPlayers;
    public List<String> enemyClans;
    public List<String> enemyPlayers;
    public String ownFormat;
    public String alliedFormat;
    public String enemyFormat;

    public ServerConfig() {
    }

    public boolean hasLegacyData() {
        return (ownClans != null && !ownClans.isEmpty())
                || (ownPlayers != null && !ownPlayers.isEmpty())
                || (alliedClans != null && !alliedClans.isEmpty())
                || (alliedPlayers != null && !alliedPlayers.isEmpty())
                || (enemyClans != null && !enemyClans.isEmpty())
                || (enemyPlayers != null && !enemyPlayers.isEmpty());
    }

    // Migrate legacy fields into the categories list, then null out
    public void migrateLegacy() {
        if (!hasLegacyData()) return;
        if (!categories.isEmpty()) return;

        // Determine group names from current namePattern (typically Usernames/Clans or username/clan)
        String userGroup = "username";
        String clanGroup = "clan";
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>");
            java.util.regex.Matcher m = p.matcher(namePattern != null ? namePattern : "");
            if (m.find()) userGroup = m.group(1);
            if (m.find()) clanGroup = m.group(1);
        } catch (Exception ignored) {}

        if (ownClans != null || ownPlayers != null) {
            TagCategory own = new TagCategory("Own",
                    ownFormat != null ? ownFormat : "<color:#00ff00><bold>OWN</bold></color:#00ff00> <gray>%" + userGroup + "% [<color:#00ff00>%" + clanGroup + "%</color:#00ff00>]");
            if (ownClans != null) own.getOrCreateGroup(clanGroup).addAll(ownClans);
            if (ownPlayers != null) own.getOrCreateGroup(userGroup).addAll(ownPlayers);
            categories.add(own);
        }
        if (alliedClans != null || alliedPlayers != null) {
            TagCategory allied = new TagCategory("Allied",
                    alliedFormat != null ? alliedFormat : "<color:#00ff00><bold>ALLY</bold></color:#00ff00> <gray>%" + userGroup + "% [<color:#00ff00>%" + clanGroup + "%</color:#00ff00>]");
            if (alliedClans != null) allied.getOrCreateGroup(clanGroup).addAll(alliedClans);
            if (alliedPlayers != null) allied.getOrCreateGroup(userGroup).addAll(alliedPlayers);
            categories.add(allied);
        }
        if (enemyClans != null || enemyPlayers != null) {
            TagCategory enemy = new TagCategory("Enemy",
                    enemyFormat != null ? enemyFormat : "<color:#ff0000><bold>ENEMY</bold></color:#ff0000> <gray>%" + userGroup + "% [<color:#ff0000>%" + clanGroup + "%</color:#ff0000>]");
            if (enemyClans != null) enemy.getOrCreateGroup(clanGroup).addAll(enemyClans);
            if (enemyPlayers != null) enemy.getOrCreateGroup(userGroup).addAll(enemyPlayers);
            categories.add(enemy);
        }

        ownClans = null;
        ownPlayers = null;
        alliedClans = null;
        alliedPlayers = null;
        enemyClans = null;
        enemyPlayers = null;
        ownFormat = null;
        alliedFormat = null;
        enemyFormat = null;
    }

    // Generic category matcher
    public TagCategory findCategory(Map<String, String> groupMatches,
                                    List<String> regexGroupNames,
                                    String serverAddress) {
        if (groupMatches == null || groupMatches.isEmpty()) return null;
        for (TagCategory cat : categories) {
            if (!cat.appliesToServer(serverAddress)) continue;
            List<String> order = cat.effectiveMatchOrder(regexGroupNames);
            for (String groupName : order) {
                String matched = groupMatches.get(groupName);
                if (matched == null || matched.isEmpty()) continue;
                List<String> entries = cat.getGroup(groupName);
                for (String entry : entries) {
                    if (entry.equalsIgnoreCase(matched)) return cat;
                }
            }
        }
        return null;
    }

    // Legacy wrappers
    public TagCategory findCategory(String username, String clan, String serverAddress) {
        Map<String, String> matches = new java.util.LinkedHashMap<>();
        if (username != null && !username.isEmpty()) matches.put("username", username);
        if (clan != null && !clan.isEmpty()) matches.put("clan", clan);
        List<String> groupOrder = List.of("username", "clan");
        return findCategory(matches, groupOrder, serverAddress);
    }

    public TagCategory findCategory(String username, String clan) {
        return findCategory(username, clan, null);
    }

    public TagCategory findCategoryById(String id) {
        if (id == null || id.isBlank()) return null;
        for (TagCategory cat : categories) {
            if (id.equals(cat.id)) return cat;
        }
        return null;
    }

    public int indexOfCategory(String id) {
        if (id == null || id.isBlank()) return -1;
        for (int i = 0; i < categories.size(); i++) {
            if (id.equals(categories.get(i).id)) return i;
        }
        return -1;
    }

    public String getFormat(String username, String clan, String serverAddress) {
        TagCategory cat = findCategory(username, clan, serverAddress);
        if (cat != null) {
            return cat.format;
        }
        if (clan == null || clan.isEmpty()) {
            return noClanFormat;
        }
        return neutralFormat;
    }

    public String getFormat(String username, String clan) {
        return getFormat(username, clan, null);
    }

    public static ServerConfig createDefaultStrayConfig() {
        ServerConfig config = new ServerConfig();

        config.namePattern = "^(?<Usernames>\\S+)(?:\\s+\\[(?<Clans>[^\\]]+)\\])?$";

        TagCategory own = new TagCategory("<gradient:#6b4226:#d4a373:#f6e7d7:#d4a373:#6b4226>⋄༺⥼—–---–—⥽༻⋄ <b>Latte</b> ⋄༺⥼—–---–—⥽༻⋄</gradient>",
                "<color:#00ff00><bold>LATTE</bold></color:#00ff00> <gray>%Usernames% [<color:#00ff00>%Clans%</color:#00ff00>]");
        own.getOrCreateGroup("Clans").add("Latte");
        own.getOrCreateGroup("Clans").add("Latte2");
        own.getOrCreateGroup("Usernames").add("Colusite");
        own.getOrCreateGroup("Usernames").add("Kylaz");
        own.getOrCreateGroup("Usernames").add("Yoe");
        own.getOrCreateGroup("Usernames").add("Knucior");
        config.categories.add(own);

        TagCategory allied = new TagCategory("<gradient:#0f8f2f:#1ed760:#b6ffcc:#1ed760:#0f8f2f>⋄༺⥼—–---–—⥽༻⋄ <b>Allied</b> ⋄༺⥼—–---–—⥽༻⋄</gradient>",
                "<color:#00ff00><bold>ALLY</bold></color:#00ff00> <gray>%Usernames% [<color:#00ff00>%Clans%</color:#00ff00>]");
        List<String> alliedClans = allied.getOrCreateGroup("Clans");
        alliedClans.add("REVOL");
        alliedClans.add("REVOLUTION");
        alliedClans.add("Solace");
        alliedClans.add("Serenity");
        alliedClans.add("Knight");
        alliedClans.add("Knight2");
        alliedClans.add("Knights");
        alliedClans.add("Knights2");
        alliedClans.add("Valor");
        alliedClans.add("Valor2");
        alliedClans.add("ASPCT");
        alliedClans.add("Aspect");
        alliedClans.add("Compilance");
        alliedClans.add("Sakura");
        alliedClans.add("better");
        alliedClans.add("VLRSE");
        alliedClans.add("Valerse");
        alliedClans.add("FUN");
        List<String> alliedPlayers = allied.getOrCreateGroup("Usernames");
        alliedPlayers.add("Washup");
        alliedPlayers.add("SnowyAvi");
        alliedPlayers.add("vhro");
        alliedPlayers.add("SimplyAPancake");
        alliedPlayers.add("_Valzz");
        config.categories.add(allied);

        TagCategory elysiumEu = new TagCategory("<gradient:#001a99:#003cff:#99bbff:#003cff:#001a99>⋄༺⥼—–---–—⥽༻⋄ <b>Elysium</b> ⋄༺⥼—–---–—⥽༻⋄</gradient>",
                "<color:#ff0000><bold>ENEMY</bold></color:#ff0000> <gray>%Usernames% [<color:#ff0000>%Clans%</color:#ff0000>]");
        List<String> elyClans = elysiumEu.getOrCreateGroup("Clans");
        elyClans.add("Elysium");
        elyClans.add("ElysiumNA");
        elyClans.add("ElysiumAS");
        elyClans.add("Elysium2");
        elysiumEu.serverFilters.add("eu");
        config.categories.add(elysiumEu);

        TagCategory enemy = new TagCategory("<gradient:#660000:#ff1a1a:#ff8080:#ff1a1a:#660000>⋄༺⥼—–---–—⥽༻⋄ <b>Enemy</b> ⋄༺⥼—–---–—⥽༻⋄</gradient>",
                "<color:#ff0000><bold>ENEMY</bold></color:#ff0000> <gray>%Usernames% [<color:#ff0000>%Clans%</color:#ff0000>]");
        List<String> enemyClans = enemy.getOrCreateGroup("Clans");
        enemyClans.add("Resistance");
        enemyClans.add("Dense");
        enemyClans.add("Batman");
        enemyClans.add("Vaping");
        enemyClans.add("Forsaken");
        enemyClans.add("Trace");
        enemyClans.add("SYNDICATE");
        enemyClans.add("Luminous");
        enemyClans.add("ETRNL");
        enemyClans.add("ETERNAL");
        enemyClans.add("Vanity");
        enemyClans.add("VanityAS");
        enemyClans.add("Fate");
        enemyClans.add("Rage");
        enemyClans.add("Legion");
        enemyClans.add("Mythic");
        enemyClans.add("UBC");
        enemyClans.add("kindacold");
        enemy.getOrCreateGroup("Usernames").add("ndla");
        config.categories.add(enemy);

        return config;
    }
}