package com.github.agolubev.playinzoo

import com.typesafe.config.ConfigFactory
import play.api.{Configuration, Logger}
import collection.JavaConversions._

/**
 * Created by alexandergolubev
 */
object PlayInZoo {

  def loadConfiguration(configuration: Configuration): Configuration = {
    Logger.debug("Loading configuration from zookeeper")
    val result = new PlayInZoo(configuration).loadConfiguration()
    Logger.debug("Loading configuration is finished")
    result
  }
}

protected[playinzoo] class PlayInZoo(configuration: Configuration) {

  def newInstanceZkClient(): ZkClient = new ZkClient(
    configuration.getString("playinzoo.hosts").getOrElse({
      Logger.warn("playinzoo.hosts is not set uses default value: localhost")
      "localhost:2181"
    }),
    configuration.getString("playinzoo.root").getOrElse("/"),
    configuration.getInt("playinzoo.timeout").getOrElse(3000),
    configuration.getString("playinzoo.schema"),
    configuration.getString("playinzoo.auth"),
    configuration.getInt("playinzoo.threadpool.size").getOrElse(1)
  )

  def loadConfiguration(): Configuration = {
    configuration.getString("playinzoo.paths").foldLeft(Configuration.empty)(
      (emptyConfig, paths) => loadConfigurationAux(emptyConfig, paths)
    )
  }

  private def loadConfigurationAux(emptyConfig: Configuration, paths: String): Configuration = {
    val client = newInstanceZkClient()
    client.executeWithZk(() => ConfigFactory.parseMap(
      loadConfigIntoMap(client, paths))) match {
      case Some(config) => Configuration(config)
      case None => emptyConfig
    }
  }

  private def loadConfigIntoMap(client: ZkClient, paths: String): java.util.Map[String, AnyRef] = {
    client.loadAttributesFromPaths(paths).map { case (key, value) => (key, new String(value.toArray, "UTF-8"))}
  }
}
