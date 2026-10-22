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
persistence uses MySQL. No broker or credentials are created by this plugin.
The broker user needs permission to declare and use the required queues and
exchanges in the configured virtual host.

Fresh configs contain blank credential placeholders. Existing configs receive
missing fields without replacing operator values; when fields are added, YAML is
reserialized and comments/formatting may change. Complete configs are not rewritten.
Invalid credentials fields, virtual hosts, or ports stop startup with a diagnostic
naming the configuration file and keys. Connection or queue initialization failure
also stops startup instead of leaving messaging partially initialized.

The offline build kit in the sibling Core checkout builds PalaceBungee with its
MySQL runtime and the config template. To check configuration handling after
building, run `python3 PalaceBungee/tools/test-config.py` from the checkouts' parent
directory with `JAVA_HOME` pointing to a JDK 11 or newer. Tests run against both
the compiled classes and packaged PalaceBungee JAR, without a live broker.

## MySQL persistence

PalaceBungee and all Core servers must use the same MySQL schema:

```yaml
mysql:
  host: 'your-mysql-host'
  port: 3306
  database: palace
  username: 'your-mysql-user'
  password: 'your-mysql-password'
  ssl-mode: PREFERRED
```

Create the schema/account before enabling the plugin. The account needs SELECT,
INSERT, UPDATE, DELETE, and CREATE on the schema. MySQL Connector/J is bundled
by Core's offline build. Existing MongoDB and SQLite data are not automatically
migrated; use the atomic import tools documented in the sibling Core checkout's
`offline-build/README.md` before starting writers. SQLite paths and the old
`mongodb` settings are ignored by the running plugin.

Forum linking uses the same MySQL schema by default. To use the forum
application's separate schema, add a complete `forum-mysql` section with the same
fields. Its `core_members` and `core_pfields_content` tables must already exist;
PalaceBungee does not create or alter the forum application schema.
