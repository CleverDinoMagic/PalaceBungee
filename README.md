# PalaceBungee
Palace's new and improved BungeeCord plugin, relying on nothing to run smoothly!

## RabbitMQ configuration

PalaceBungee creates `plugins/PalaceBungee/config.yml` on the **proxy**, including
these fields. Set them to the same broker and virtual host used by Core:

```yaml
rabbitmq:
  host: 'your-broker-host'
  port: 5672
  username: 'your-broker-user'
  password: 'your-broker-password'
  virtualhost: '/'
```

Restart the proxy after editing. RabbitMQ is required for messaging even when
persistence uses SQLite. No broker or credentials are created by this plugin.
The broker user needs permission to declare and use the required queues and
exchanges in the configured virtual host.

Fresh configs contain blank credential placeholders. Existing configs receive
missing fields without replacing operator values; when fields are added, YAML is
reserialized and comments/formatting may change. Complete configs are not rewritten.
Invalid credentials fields, virtual hosts, or ports stop startup with a diagnostic
naming the configuration file and keys. Connection or queue initialization failure
also stops startup instead of leaving messaging partially initialized.

The offline build kit in the sibling Core checkout builds PalaceBungee with its
SQLite runtime and the config template. To check configuration handling after
building, run `python3 PalaceBungee/tools/test-config.py` from the checkouts' parent
directory with `JAVA_HOME` pointing to a JDK 11 or newer. Tests run against both
the compiled classes and packaged PalaceBungee JAR, without a live broker.
