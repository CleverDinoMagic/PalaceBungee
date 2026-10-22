package network.palace.bungee.utils;

import net.md_5.bungee.api.config.ServerInfo;
import network.palace.bungee.handlers.Server;
import java.util.*;

/** Hub selection also works before database server records have been populated. */
final class HubSelector {
    private HubSelector() { }

    static ServerInfo select(Map<String, Server> known, Map<String, ServerInfo> registered, ServerInfo current) {
        ServerInfo selected = null;
        int count = Integer.MAX_VALUE;
        List<Server> candidates = new ArrayList<>(known.values());
        candidates.sort(Comparator.comparing(Server::getName));
        for (Server server : candidates) {
            if (!isHub(server.getName()) && !"Hub".equalsIgnoreCase(server.getServerType())) continue;
            ServerInfo info = registered.get(server.getName());
            if (info == null || !server.isOnline()) continue;
            int players = server.getCount();
            if (selected == null || players < count || (players == count && same(info, current))) {
                selected = info;
                count = players;
            }
        }
        if (selected != null) return selected;

        // Proxy-only Hub entries have no database liveness/count data yet.
        List<String> names = new ArrayList<>(registered.keySet());
        Collections.sort(names);
        for (String name : names) {
            if (!isHub(name) || known.containsKey(name)) continue;
            ServerInfo info = registered.get(name);
            if (info != null && (selected == null || same(info, current))) selected = info;
        }
        return selected;
    }

    static ServerInfo fallback(Map<String, Server> known, Map<String, ServerInfo> registered, List<String> priorities) {
        for (String name : priorities) {
            ServerInfo info = registered.get(name);
            Server server = known.get(name);
            if (info != null && (server == null || server.isOnline())) return info;
        }
        return null;
    }

    private static boolean isHub(String name) { return name.regionMatches(true, 0, "Hub", 0, 3); }
    private static boolean same(ServerInfo a, ServerInfo b) { return b != null && a.getName().equals(b.getName()); }
}
