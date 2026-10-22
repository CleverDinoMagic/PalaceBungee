package network.palace.bungee.utils;

import net.md_5.bungee.api.config.ServerInfo;
import network.palace.bungee.handlers.Server;
import java.lang.reflect.Proxy;
import java.util.*;

public final class HubSelectorTest {
    private static ServerInfo info(String name) {
        return (ServerInfo) Proxy.newProxyInstance(ServerInfo.class.getClassLoader(), new Class<?>[]{ServerInfo.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return name;
                    throw new AssertionError("Unexpected ServerInfo call: " + method.getName());
                });
    }
    private static Server server(String name, String type, int players, boolean online) {
        return new Server(name, "localhost:25565", false, type, online) {
            @Override public boolean isOnline() { return online; }
            @Override public int getCount() { return players; }
        };
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        Map<String,Server> known = new HashMap<>();
        Map<String,ServerInfo> registered = new HashMap<>();
        check(HubSelector.select(known, registered, null) == null, "Empty startup must not throw");
        ServerInfo hub1 = info("Hub1"), hub2 = info("Hub2"), lobby = info("Lobby"), stale = info("DeletedHub");
        registered.put("Hub2", hub2);
        check(HubSelector.select(known, registered, null) == hub2, "Proxy-only hub must work without database records or Hub1");
        known.put("Hub2", server("Hub2", "Hub", 2, false));
        check(HubSelector.select(known, registered, hub2) == null, "Known offline hub must not be reused");
        known.put("Hub1", server("Hub1", "Hub", 20, true));
        known.put("Hub2", server("Hub2", "Hub", 2, true));
        registered.put("Hub1", hub1);
        check(HubSelector.select(known, registered, null) == hub2, "Select lowest population with no current hub");
        check(HubSelector.select(known, registered, stale) == hub2, "Replace stale current hub");
        known.put("Hub1", server("Hub1", "Hub", 2, true));
        check(HubSelector.select(known, registered, hub2) == hub2, "Equal counts must retain eligible current hub");
        registered.remove("Hub2");
        check(HubSelector.select(known, registered, hub2) == hub1, "Removed proxy registration must not remain selected");
        registered.remove("Hub1");
        check(HubSelector.select(known, registered, hub1) == null, "Missing proxy metadata must not be selected");
        registered.put("Lobby", lobby);
        known.put("Lobby", server("Lobby", "Hub", 0, true));
        check(HubSelector.select(known, registered, null) == lobby, "Recognize Hub type without a Hub name prefix");
        known.remove("Lobby");
        check(HubSelector.select(known, registered, null) == null, "Do not select arbitrary proxy servers as hubs");
        check(HubSelector.fallback(known, registered, Arrays.asList("Missing", "Lobby")) == lobby,
                "Empty database must respect listener priorities and skip missing registrations");
        check(HubSelector.fallback(known, registered, Collections.singletonList("OtherListenerHub")) == null,
                "Do not borrow another listener's fallback");
        known.put("Lobby", server("Lobby", "Lobby", 0, false));
        check(HubSelector.fallback(known, registered, Collections.singletonList("Lobby")) == null,
                "Listener fallback must skip servers known to be offline");
        known.put("Lobby", server("Lobby", "Hub", 1, true));
        check(HubSelector.select(known, registered, null) == lobby, "Recover when a hub becomes available");
        System.out.println("PalaceBungee hub selection tests passed");
    }
}
