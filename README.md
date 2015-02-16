# PlayInZoo configuration management
This is a helper for play application to load configuration from zookeeper on server startup.
It's extremely useful for cloud solutions to store configuration in one place to be available from anywhere.

Play 2.2 compliant.

##Feature
 - load nodes and their data as play configuration 
 - configuration loaded from zookeeper can be used by plugins, application or play itself
 - you can ask to load from multiple paths
 - support Zookeeper authentication
 - can load nodes recursively (TBD)
 - supports several threads to load configuration - single thread loading by default (TBD)
 
## Configure your application to use PlayInZoo
You need to define Zookeeper hosts and paths you need to load configuration from

```
playinzoo.hosts=127.0.0.1:3000,127.0.0.1:3001,127.0.0.1:3002
playinzoo.paths=/domain/data_center/client,/domain/data_center/org
```
You can set these properties in application.conf or system properties.

If you defined path as `/domain/data_center/client` than all nodes that are direct children of `client` node
will be loaded with their values

To make it work you also need to add to Global object following

```scala
 override def onLoadConfig(config: Configuration, path: File, classloader: ClassLoader, mode: Mode): Configuration = {
    config ++ PlayInZoo.loadConfiguration(config)
  }
```
## Authentication
You can add settings with authentication information if required:
```
playinzoo.schema=digest
playinzoo.auth="me:pass"
```


