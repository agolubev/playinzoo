# PlayInZoo configuration management [![Build Status](https://travis-ci.org/agolubev/playinzoo.svg?branch=master)
This is a helper for play application to load configuration from zookeeper on server startup.
It's extremely useful for cloud solutions to store configuration in one place to be available from anywhere.

Play 2.2 compliant.

**Any suggestions about additional features are welcome.**

## Feature
 - load nodes and their data as play configuration 
 - configuration loaded from zookeeper can be used by plugins, application or play itself
 - you can ask to load from multiple paths
 - support Zookeeper authentication
 - can load nodes recursively
 - supports several threads to load configuration - single thread loads by default
 
## Configure your application to use PlayInZoo
You need to define Zookeeper hosts and paths you need to load configuration from

```
playinzoo.hosts=127.0.0.1:3000,127.0.0.1:3001,127.0.0.1:3002
playinzoo.paths=/domain/data_center/client,/domain/data_center/org
```
You can set these properties in application.conf or system properties.

If you defined path as `/domain/data_center/client` than all nodes that are direct children of `client` node
will be loaded with their values and shared as play configuration

To make it work you also need to add to Global object following

```scala
 override def onLoadConfig(config: Configuration, path: File, 
        classloader: ClassLoader, mode: Mode): Configuration = {
    config ++ PlayInZoo.loadConfiguration(config)
  }
```

## Add PlayInZoo to your dependencies

In your project/Build.scala:
```scala
libraryDependencies ++= Seq(
  "com.github.agolubev" %% "play-in-zoo" % "0.1"
)
```

## Authentication
You can add settings with authentication information if required:
```
playinzoo.schema=digest
playinzoo.auth="me:pass"
```

## Loading with multiple threads
To load configuration by multiple threads you can specify number of threads in pool
```
playinzoo.threadpool.size=3
```
By default it's one thread doing loading

## Recursive loading
You can load configuration from the whole subtree. To do this you need to add `**` to the 
end of a path, like `/domain/data_center/org/**`. If zookeeper node has children it consider as folder -
only leafs are loading as name-value configuration properties.