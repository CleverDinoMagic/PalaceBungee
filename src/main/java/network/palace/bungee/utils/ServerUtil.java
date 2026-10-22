package network.palace.bungee.utils;

import lombok.Getter;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ReconnectHandler;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import network.palace.bungee.PalaceBungee;
import network.palace.bungee.handlers.Player;
import network.palace.bungee.handlers.Server;
import network.palace.bungee.messages.packets.DisablePlayerPacket;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.logging.Level;
import java.util.concurrent.ConcurrentHashMap;

public class ServerUtil {
    private final Timer updateTimer = new Timer();
    private final Map<String, Server> servers = new ConcurrentHashMap<>();
    private volatile ServerInfo currentHub;
    private boolean warnedNoHub;
    @Getter private int onlineCount = 0;
    @Getter private final List<String> onlinePlayerNames = new ArrayList<>();

    public ServerUtil() {
        loadServers();

        updateCurrentHub();

        PalaceBungee.getProxyServer().setReconnectHandler(new ReconnectHandler() {
            @Override
            public ServerInfo getServer(ProxiedPlayer proxiedPlayer) {
                ServerInfo hub = currentHub;
                Map<String, ServerInfo> registered = ProxyServer.getInstance().getServers();
                if (hub != null && registered.containsKey(hub.getName())) return registered.get(hub.getName());
                return HubSelector.fallback(servers, registered,
                        proxiedPlayer.getPendingConnection().getListener().getServerPriority());
            }

            @Override
            public void setServer(ProxiedPlayer proxiedPlayer) {
            }

            @Override
            public void save() {
            }

            @Override
            public void close() {
            }
        });

        updateTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    onlineCount = PalaceBungee.getMongoHandler().getOnlineCount();
                } catch (Exception e) {
                    PalaceBungee.getProxyServer().getLogger().log(Level.SEVERE, "Error retrieving global player count", e);
                }
                try {
                    List<String> names = PalaceBungee.getMongoHandler().getOnlinePlayerNames();
                    onlinePlayerNames.clear();
                    onlinePlayerNames.addAll(names);
                } catch (Exception e) {
                    PalaceBungee.getProxyServer().getLogger().log(Level.SEVERE, "Error updating online player name list", e);
                }
                try {
                    updateCurrentHub();
                } catch (Exception e) {
                    PalaceBungee.getProxyServer().getLogger().log(Level.SEVERE, "Error determining currentHub", e);
                }
                try {
                    for (Player tp : PalaceBungee.getOnlinePlayers()) {
                        if (tp.getProxiedPlayer().isEmpty() && (System.currentTimeMillis() - tp.getLoginTime()) > 5000) {
                            PalaceBungee.logout(tp.getUniqueId(), tp);
                        }
                    }
                } catch (Exception e) {
                    PalaceBungee.getProxyServer().getLogger().log(Level.SEVERE, "Error maintaining online player list", e);
                }
            }
        }, 2000L, 5000L);
    }

    private void updateCurrentHub() {
        currentHub = HubSelector.select(servers, ProxyServer.getInstance().getServers(), currentHub);
        if (currentHub == null) {
            if (!warnedNoHub) PalaceBungee.getProxyServer().getLogger().warning(
                    "No registered hub is available. Reconnects will use each proxy listener's configured priorities. "
                    + "Check proxy config.yml servers/listeners and MySQL server records (including testNetwork/playground settings).");
            warnedNoHub = true;
        } else if (warnedNoHub) {
            PalaceBungee.getProxyServer().getLogger().info("Hub routing restored: " + currentHub.getName());
            warnedNoHub = false;
        }
    }

    public void shutdown() {
        updateTimer.cancel();
    }

    private void loadServers() {
        servers.clear();
//        ProxyServer.getInstance().getServers().clear();
        try {
            List<Server> servers = PalaceBungee.getMongoHandler().getServers(PalaceBungee.isTestNetwork());
            for (Server s : servers) {
                this.servers.put(s.getName(), s);
                try {
                    String[] addressList = s.getAddress().split(":");
                    ServerInfo info = ProxyServer.getInstance().constructServerInfo(s.getName(),
                            new InetSocketAddress(addressList[0], Integer.parseInt(addressList[1])), "", false);
                    ProxyServer.getInstance().getServers().put(info.getName(), info);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            PalaceBungee.getProxyServer().getLogger().info("Successfully loaded " + servers.size() + " servers!");
        } catch (Exception e) {
            PalaceBungee.getProxyServer().getLogger().log(Level.SEVERE, "Error loading servers, stopping bungee server!", e);
            System.exit(1);
        }
    }

    public ServerInfo getServerInfo(String name, boolean exact) {
        for (ServerInfo info : ProxyServer.getInstance().getServers().values()) {
            if ((exact && info.getName().equals(name)) || (!exact && info.getName().equalsIgnoreCase(name)))
                return info;
        }
        return null;
    }

    public Server getServer(String name, boolean exact) {
        if (exact) return servers.get(name);
        for (Server s : servers.values()) {
            if (s.getName().equalsIgnoreCase(name))
                return s;
        }
        return null;
    }

    public int getServerCount(net.md_5.bungee.api.connection.Server server) {
        return getServerCount(server.getInfo().getName());
    }

    public int getServerCount(String name) {
        return PalaceBungee.getMongoHandler().getServerCount(name);
    }

    public void createServer(Server server) {
        servers.put(server.getName(), server);
    }

    public void deleteServer(String name) {
        servers.remove(name);
    }

    public List<Server> getServers() {
        return new ArrayList<>(servers.values());
    }

    public void sendPlayer(Player player, String serverName) {
        Server server = getServer(serverName, true);
        if (server == null) return;
        server.join(player);
    }

    public void sendPlayer(Player player, Server server) {
        if (server == null) return;
        server.join(player);
    }

    public void handleServerSwitch(UUID uuid, ServerInfo fromInfo, ServerInfo toInfo) {
        Player tp = PalaceBungee.getPlayer(uuid);
        Server from = fromInfo == null ? null : getServer(fromInfo.getName(), true);
        Server to = toInfo == null ? null : getServer(toInfo.getName(), true);
        if (from == null) {
            // new connection
            if (tp.isDisabled()) {
                try {
                    tp.sendPacket(new DisablePlayerPacket(tp.getUniqueId(), true), true);
                } catch (Exception e) {
                    e.printStackTrace();
                    tp.kickPlayer("Internal Exception: java.io.IOException: An existing connection was forcibly closed by the remote host", false);
                }
            }
        }
        if (to == null) {
            // unknown error
        } else {
            PalaceBungee.getMongoHandler().setPlayerServer(uuid, to.getName());
        }
    }

    public Server getServerByType(String serverType) {
        return getServerByType(serverType, null);
    }

    public Server getServerByType(String serverType, UUID exclude) {
        Server server = null;
        for (Server s : servers.values()) {
            if ((s.getUniqueId().equals(exclude)) || !s.isOnline()) continue;
            if (s.getServerType().equals(serverType)) {
                if (server == null) {
                    server = s;
                    continue;
                }
                if (s.getCount() < server.getCount()) server = s;
            }
        }
        return server;
    }

    public void sendPlayerByType(Player player, String serverType) {
        Server server = getServerByType(serverType);
        if (server != null) server.join(player);
    }


    public Server getEmptyParkServer(UUID exclude) {
        Server s = null;
        List<Server> servers = new ArrayList<>(this.servers.values());
        for (Server server : servers) {
            if ((server.getUniqueId().equals(exclude)) || !server.isOnline() || !server.isPark()) continue;
            if (s == null) {
                s = server;
                continue;
            }
            if (server.getCount() < s.getCount()) {
                s = server;
            }
        }
        return s;
    }

    public String getChannel(Player player) {
        String serverName = player.getServerName();
        Server server = getServer(serverName, true);
        if (server == null) {
            return "";
        }
        return server.isPark() ? "ParkChat" : server.getName();
    }

    public boolean isOnPark(Player tp) {
        Server server = getServer(tp.getServerName(), true);
        return server != null && server.isPark();
    }
}
