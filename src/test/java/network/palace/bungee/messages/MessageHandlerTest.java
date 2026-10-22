package network.palace.bungee.messages;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import network.palace.bungee.utils.ConfigUtil;
import java.io.IOException;
import java.lang.reflect.Proxy;

public final class MessageHandlerTest {
    public static void main(String[] args) throws Exception {
        boolean[] closed = {false};
        Connection publishing = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, values) -> {
                    if (method.getName().equals("close")) { closed[0] = true; return null; }
                    throw new AssertionError("Unexpected connection call " + method.getName());
                });
        ConnectionFactory factory = new ConnectionFactory() {
            int calls;
            @Override public Connection newConnection() throws IOException {
                if (++calls == 1) return publishing;
                throw new IOException("Simulated consuming connection failure");
            }
        };
        ConfigUtil.DatabaseConnection settings = new ConfigUtil.DatabaseConnection("broker.example", "operator", "password", "/palace", 5673);
        try {
            new MessageHandler(settings, factory);
            throw new AssertionError("Connection failure must propagate");
        } catch (IOException expected) {
            if (!closed[0]) throw new AssertionError("Publishing connection must close on partial failure");
        }
        if (!factory.getHost().equals("broker.example") || factory.getPort() != 5673
                || !factory.getVirtualHost().equals("/palace") || !factory.getUsername().equals("operator")
                || !factory.getPassword().equals("password")) throw new AssertionError("All broker settings must reach the factory");
        System.out.println("PalaceBungee RabbitMQ factory and cleanup tests passed");
    }
}
