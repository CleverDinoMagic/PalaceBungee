package network.palace.bungee.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.Favicon;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import network.palace.bungee.PalaceBungee;
import network.palace.bungee.handlers.Player;
import network.palace.bungee.handlers.ProtocolConstants;
import network.palace.bungee.handlers.Rank;
import network.palace.bungee.messages.packets.ProxyReloadPacket;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("MismatchedReadAndWriteOfArray")
@Getter
public class ConfigUtil {
    private Favicon favicon;
    private String motd, motdTemp;
    private ServerPing.PlayerInfo[] motdInfo;
    private String maintenanceMotd, maintenanceMotdTemp;
    private boolean maintenance;
    private int chatDelay;
    private boolean dmEnabled;
    private boolean strictChat;
    private double strictThreshold;
    private List<String> mutedChats;
    private List<String> announcements;

    public void reload() throws IOException {
        Configuration fileConfig = getConfig();
        boolean versionOverride;
        if (fileConfig.contains("versions")) {
            PalaceBungee.getInstance().getLogger().warning("Bypassing database config for MC version settings due to file override");
            Configuration versions = fileConfig.getSection("versions");
            ProtocolConstants.setHighVersion(versions.getInt("maxVersion"), versions.getString("maxVersionText"));
            ProtocolConstants.setLowVersion(versions.getInt("minVersion"), versions.getString("minVersionText"));
            versionOverride = true;
        } else {
            versionOverride = false;
        }

        BungeeConfig bungeeConfig = getBungeeConfig();
        this.favicon = bungeeConfig.favicon;
        this.motdTemp = bungeeConfig.motd;
        this.motd = this.motdTemp.replaceAll("%n%", System.getProperty("line.separator"));
        this.motdInfo = new ServerPing.PlayerInfo[bungeeConfig.motdInfo.length];
        for (int i = 0; i < bungeeConfig.motdInfo.length; i++) {
            this.motdInfo[i] = new ServerPing.PlayerInfo(ChatColor.translateAlternateColorCodes('&', bungeeConfig.motdInfo[i]), "");
        }
        this.maintenanceMotdTemp = bungeeConfig.maintenanceMotd;
        this.maintenanceMotd = this.maintenanceMotdTemp.replaceAll("%n%", System.getProperty("line.separator"));
        this.maintenance = bungeeConfig.maintenance;
        this.chatDelay = bungeeConfig.chatDelay;
        this.dmEnabled = bungeeConfig.dmEnabled;
        this.strictChat = bungeeConfig.strictChat;
        this.strictThreshold = bungeeConfig.strictThreshold;
        this.mutedChats = bungeeConfig.mutedChats;
        this.announcements = bungeeConfig.announcements;

        if (!versionOverride) {
            ProtocolConstants.setHighVersion(bungeeConfig.maxVersion, bungeeConfig.maxVersionString);
            ProtocolConstants.setLowVersion(bungeeConfig.minVersion, bungeeConfig.minVersionString);
        }

        if (this.maintenance) {
            for (Player tp : new ArrayList<>(PalaceBungee.getOnlinePlayers())) {
                try {
                    if (tp.getRank().getRankId() < Rank.DEVELOPER.getRankId()) {
                        tp.kickPlayer(ChatColor.AQUA + "Palace Network has entered a period of maintenance!\nFollow " +
                                ChatColor.BLUE + "@PalaceDev " + ChatColor.AQUA + "on Twitter for updates.", false);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public DatabaseConnection getRabbitMQInfo() throws IOException {
        return readRabbitMQInfo(getConfig());
    }

    public static DatabaseConnection readRabbitMQInfo(Configuration config) {
        if (!(config.get("rabbitmq") instanceof Configuration))
            throw new IllegalArgumentException("rabbitmq must be a configuration section in plugins/PalaceBungee/config.yml");
        List<String> errors = new ArrayList<>();
        String host = requiredRabbitMQ(config, "host", errors);
        String username = requiredRabbitMQ(config, "username", errors);
        String password = requiredRabbitMQ(config, "password", errors);
        Object virtualHost = config.get("rabbitmq.virtualhost", "/");
        if (!(virtualHost instanceof String) || ((String) virtualHost).trim().isEmpty())
            errors.add("rabbitmq.virtualhost must be a non-blank string");
        Object port = config.get("rabbitmq.port", 5672);
        if (!(port instanceof Integer) || (Integer) port < 1 || (Integer) port > 65535)
            errors.add("rabbitmq.port must be an integer between 1 and 65535");
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors)
                + ". Edit plugins/PalaceBungee/config.yml with your existing RabbitMQ broker settings. "
                + "Messaging is required; no broker credentials are supplied by PalaceBungee.");
        return new DatabaseConnection(host, username, password, (String) virtualHost, (Integer) port);
    }

    private static String requiredRabbitMQ(Configuration config, String key, List<String> errors) {
        Object value = config.get("rabbitmq." + key);
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            errors.add("rabbitmq." + key + " must be an explicitly configured, non-blank string");
            return null;
        }
        return (String) value;
    }

    /** Historical API alias; all persistence now uses MySQL. */
    @Deprecated
    public DatabaseConnection getMongoDBInfo() { return getMySQLInfo(); }

    public DatabaseConnection getMySQLInfo() { return databaseInfo("mysql"); }

    public DatabaseConnection getSQLInfo() { return databaseInfo("sql"); }

    private DatabaseConnection databaseInfo(String section) {
        try {
            Configuration config = getConfig();
            if (!config.contains(section + ".host")) section = "mysql";
            network.palace.storage.MySQLSettings.from(key -> config.get(key), section, "plugins/PalaceBungee/config.yml");
            return new DatabaseConnection(config.getString(section + ".host"), config.getString(section + ".username"),
                    config.getString(section + ".password"), config.getString(section + ".database"), config.getInt(section + ".port", 3306));
        } catch (IOException e) { throw new IllegalStateException("Cannot read plugins/PalaceBungee/config.yml", e); }
    }

    public BungeeConfig getBungeeConfig() {
        try {
            return PalaceBungee.getMongoHandler().getBungeeConfig();
        } catch (Exception e) {
            e.printStackTrace();
            return new BungeeConfig(null, "", new String[0], "", false, 2, true, false, 0.8, 0, 0, "", "", new ArrayList<>(), new ArrayList<>());
        }
    }

    public Configuration getConfig() throws IOException {
        return loadConfig(new File("plugins/PalaceBungee/config.yml"));
    }

    public File getConfigFile() throws IOException {
        File file = new File("plugins/PalaceBungee/config.yml");
        loadConfig(file);
        return file;
    }

    /** Add visible placeholders to legacy files while preserving operator values. */
    public static Configuration loadConfig(File file) throws IOException {
        java.nio.file.Files.createDirectories(file.getAbsoluteFile().getParentFile().toPath());
        ConfigurationProvider provider = ConfigurationProvider.getProvider(YamlConfiguration.class);
        Configuration defaults;
        try (java.io.InputStream input = ConfigUtil.class.getResourceAsStream("/config.yml")) {
            if (input == null) throw new IOException("Missing bundled PalaceBungee config.yml");
            if (!file.exists()) {
                java.nio.file.Files.copy(input, file.toPath());
                return provider.load(file);
            }
            defaults = provider.load(input);
        }
        Configuration config = provider.load(file);
        if (addMissing(config, defaults)) provider.save(config, file);
        return config;
    }

    private static boolean addMissing(Configuration config, Configuration defaults) {
        boolean changed = false;
        for (String key : defaults.getKeys()) {
            if (!config.getKeys().contains(key)) {
                config.set(key, defaults.get(key));
                changed = true;
            } else if (config.get(key) instanceof Configuration && defaults.get(key) instanceof Configuration) {
                changed |= addMissing((Configuration) config.get(key), (Configuration) defaults.get(key));
            }
        }
        return changed;
    }

    public void setChatDelay(int seconds) throws Exception {
        this.chatDelay = seconds;
        saveConfigChanges();
    }

    public void setMaintenanceMode(boolean maintenance) throws Exception {
        this.maintenance = maintenance;
        saveConfigChanges();
    }

    public void setDmEnabled(boolean dmEnabled) throws Exception {
        this.dmEnabled = dmEnabled;
        saveConfigChanges();
    }

    public void setMutedChats(List<String> mutedChats, boolean dbUpdate) throws Exception {
        this.mutedChats = mutedChats;
        if (dbUpdate) saveConfigChanges();
    }

    public void setAnnouncements(List<String> announcements, boolean dbUpdate) throws Exception {
        this.announcements = announcements;
        if (dbUpdate) saveConfigChanges();
    }

    public void setStrictChat(boolean strictChat) throws Exception {
        this.strictChat = strictChat;
        saveConfigChanges();
    }

    public void setStrictThreshold(double strictThreshold) throws Exception {
        this.strictThreshold = strictThreshold;
        saveConfigChanges();
    }

    private void saveConfigChanges() throws Exception {
        BungeeConfig config = new BungeeConfig(null, null, null, null,
                maintenance, chatDelay, dmEnabled, strictChat, strictThreshold,
                0, 0, null, null, mutedChats, announcements);
        PalaceBungee.getMongoHandler().setBungeeConfig(config);
        PalaceBungee.getMessageHandler().sendMessage(new ProxyReloadPacket(), PalaceBungee.getMessageHandler().ALL_PROXIES);
    }

    public boolean isTestNetwork() throws IOException {
        Configuration config = getConfig();
        if (config.contains("testNetwork")) return config.getBoolean("testNetwork", true);
        return true;
    }

    @Getter
    @AllArgsConstructor
    public static class DatabaseConnection {
        private final String host, username, password, database;
        private final int port;
    }

    @Getter
    @AllArgsConstructor
    public static class BungeeConfig {
        private final Favicon favicon;
        private final String motd;
        private final String[] motdInfo;
        private final String maintenanceMotd;
        private final boolean maintenance;
        private final int chatDelay;
        private final boolean dmEnabled;
        private final boolean strictChat;
        private final double strictThreshold;
        private final int maxVersion;
        private final int minVersion;
        private final String maxVersionString;
        private final String minVersionString;
        private final List<String> mutedChats;
        private final List<String> announcements;
    }
}
