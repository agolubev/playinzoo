# PlayInZoo configuration management 

[![Build Status](https://travis-ci.org/agolubev/playinzoo.svg?branch=master)](https://travis-ci.org/agolubev/playinzoo) [![Coverage Status](https://coveralls.io/repos/agolubev/playinzoo/badge.svg?branch=master)](https://coveralls.io/r/agolubev/playinzoo?branch=master)

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
 - zookeeper node names convert to path expressions (per TypeSafe Config) and node data can be 
 boolean, int, long, double, string
 
## Configure your application to use PlayInZoo
You need to define Zookeeper hosts and paths you need to load configuration from

```
playinzoo.hosts=127.0.0.1:3000,127.0.0.1:3001,127.0.0.1:3002
playinzoo.paths=/domain/data_center/client
```
You can set these properties in application.conf or system properties.

If you defined path as `/domain/data_center/client` than all nodes that are direct children of `client` node
will be loaded along with their values and shared as play configuration

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
resolvers += "Sonatype Releases" at "http://oss.sonatype.org/content/repositories/releases"
libraryDependencies += "com.github.agolubev" %% "play-in-zoo" % "0.3"
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

## Attributes overriding - sequential and parallel loading
Obviously attributes with the same name will override each other and it depends on loading order who wins.
You can ask to load configuration from zk folders in parallel by dividing paths with coma, or specify 
to load sequentially by using "->".

In following example `default_log` and `default_int` will be loaded in parallel. Then configuration for 
http_service will be loaded and override attributes from default settings.
```
playinzoo.paths="/org/cluster/default_log,/org/cluster/default_int->/org/cluster/http_service"
```

## String Encoding
As zookeeper node data is byte[] there is configuration attribute `playinzoo.encoding` that defines 
charset for string values. Default encoding is UTF-8.

## Example
Having following structure in Zookeeper
```
org - cluster + default + region (value: US)
              |         | language (value: en)
              |
              | service + name (value: Mobile REST)
                        | timeout(value: 3000)
``` 
you can load it into play config with paths setting:
```
playinzoo.paths=/org/cluster/**
```
In play config following attributes will appear:
```
region="US"
language="en"
name="Mobile REST"
value=3000 //integer
```