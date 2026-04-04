package io.github.colusite.straytags.client.config;

import java.util.ArrayList;
import java.util.List;

public class ServerConfig {

    public List<String> ownClans = new ArrayList<>();
    public List<String> ownPlayers = new ArrayList<>();
    public List<String> alliedClans = new ArrayList<>();
    public List<String> alliedPlayers = new ArrayList<>();
    public List<String> enemyClans = new ArrayList<>();
    public List<String> enemyPlayers = new ArrayList<>();

    public String ownFormat = "%rank% <color:#00ff00><bold>LATTE</bold></color:#00ff00> <gray>%username% [<color:#00ff00>%clan%</color:#00ff00>]";
    public String alliedFormat = "%rank% <color:#00ff00><bold>ALLY</bold></color:#00ff00> <gray>%username% [<color:#00ff00>%clan%</color:#00ff00>]";
    public String enemyFormat = "%rank% <color:#ff0000><bold>ENEMY</bold></color:#ff0000> <gray>%username% [<color:#ff0000>%clan%</color:#ff0000>]";
    public String neutralFormat = "";
    public String noClanFormat = "";

    public String namePattern = "^(?<username>\\S+)(?:\\s+\\[(?<clan>[^\\]]+)\\])?$";

    public ServerConfig() {
    }

    public static ServerConfig createDefaultStrayConfig() {
        ServerConfig config = new ServerConfig();

        config.namePattern = "^(?<username>\\S+)(?:\\s+\\[(?<clan>[^\\]]+)\\])?$";

        config.ownClans.add("Latte");
        config.ownClans.add("Latte2");
        config.ownPlayers.add("Colusite");
        config.ownPlayers.add("Kylaz");
        config.ownPlayers.add("Yoe");
        config.ownPlayers.add("Knucior");

        config.alliedClans.add("Revol");
        config.alliedClans.add("Revol2");
        config.alliedClans.add("Revolution");
        config.alliedClans.add("Solace");
        config.alliedClans.add("Serenity");
        config.alliedClans.add("Knights");
        config.alliedClans.add("Knights2");
        config.alliedClans.add("Aspct");
        config.alliedClans.add("Valor");
        config.alliedClans.add("Compilance");
        config.alliedClans.add("Sakura");
        config.alliedClans.add("Better");
        config.alliedClans.add("Valerse");

        config.enemyClans.add("Resistance");
        config.enemyClans.add("Vaping");
        config.enemyClans.add("Hyperr");
        config.enemyClans.add("Luminous");
        config.enemyClans.add("Elysium");
        config.enemyClans.add("ElysiumNA");
        config.enemyClans.add("ElysiumAS");
        config.enemyClans.add("Eternal");
        config.enemyClans.add("Vanity");
        config.enemyClans.add("Fate");
        config.enemyClans.add("Rage");
        config.enemyClans.add("Legion");
        config.enemyClans.add("Mythic");
        config.enemyClans.add("Forsaken");
        config.enemyClans.add("UBC");
        config.enemyClans.add("Trace");
        config.enemyPlayers.add("ndla");

        return config;
    }
}