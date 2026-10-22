package network.palace.bungee.utils;

import net.md_5.bungee.config.Configuration;
import java.nio.file.*;
import java.util.Comparator;

/** Run with the bundled Bungee configuration API and the built PalaceBungee classes. */
public final class ConfigUtilTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void invalid(Configuration config, String key) {
        try {
            ConfigUtil.readRabbitMQInfo(config);
            throw new AssertionError("Accepted invalid " + key);
        } catch (IllegalArgumentException e) {
            check(e.getMessage().contains(key), "Error must name invalid setting");
            check(e.getMessage().contains("plugins/PalaceBungee/config.yml"), "Error must name file");
            check(!e.getMessage().contains("secret-value"), "Error must not expose credentials");
        }
    }
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("bungee-config-test");
        Path file = directory.resolve("plugins/PalaceBungee/config.yml");
        try {
            Configuration config = ConfigUtil.loadConfig(file.toFile());
            check(Files.readString(file).contains("rabbitmq:"), "Fresh file must expose RabbitMQ fields");
            check(config.getInt("rabbitmq.port") == 5672, "Default AMQP port");
            check(config.getString("rabbitmq.virtualhost").equals("/"), "Default virtual host");
            invalid(config, "rabbitmq.host");
            Files.writeString(file, "testNetwork: false\nsqlite:\n  path: /operator/shared.sqlite\ncustom: preserved\n");
            config = ConfigUtil.loadConfig(file.toFile());
            check(!config.getBoolean("testNetwork"), "Preserve operator network mode");
            check(config.getString("sqlite.path").equals("/operator/shared.sqlite"), "Preserve SQLite path");
            check(config.getString("custom").equals("preserved"), "Preserve custom settings");
            check(Files.readString(file).contains("rabbitmq:"), "Legacy file must expose RabbitMQ fields");
            Files.writeString(file, "rabbitmq:\n  host: broker.example\n  username: operator\n  password: ' secret-value '\n  port: 5673\n  virtualhost: /palace\n");
            config = ConfigUtil.loadConfig(file.toFile());
            ConfigUtil.DatabaseConnection connection = ConfigUtil.readRabbitMQInfo(config);
            check(connection.getHost().equals("broker.example"), "Configured host");
            check(connection.getPort() == 5673, "Configured non-default port");
            check(connection.getDatabase().equals("/palace"), "Configured virtual host");
            check(connection.getPassword().equals(" secret-value "), "Preserve password whitespace");
            byte[] before = Files.readAllBytes(file);
            ConfigUtil.loadConfig(file.toFile());
            check(java.util.Arrays.equals(before, Files.readAllBytes(file)), "Complete configs must not be rewritten");
            for (Object port : new Object[]{0, 65536, "5672", 12.5}) {
                config.set("rabbitmq.port", port);
                invalid(config, "rabbitmq.port");
            }
            config.set("rabbitmq.port", 5672);
            config.set("rabbitmq.username", " ");
            invalid(config, "rabbitmq.username");
            config.set("rabbitmq.username", "operator");
            config.set("rabbitmq.virtualhost", 42);
            invalid(config, "rabbitmq.virtualhost");
            Files.writeString(file, "rabbitmq:\n  host: broker.example\n  username: operator\n  password: secret-value\n");
            connection = ConfigUtil.readRabbitMQInfo(ConfigUtil.loadConfig(file.toFile()));
            check(connection.getPort() == 5672 && connection.getDatabase().equals("/"), "Legacy defaults");
            config.set("rabbitmq", "invalid");
            invalid(config, "rabbitmq");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(path);
            }
        }
        System.out.println("PalaceBungee configuration tests passed");
    }
}
