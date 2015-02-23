package com.github.agolubev.playinzoo

import com.typesafe.config.{Config, ConfigFactory}
import play.api.Configuration
import scala.collection.JavaConverters._
import play.api.Logger

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
    configuration.getString("playinzoo.paths").foldLeft(Configuration.empty)((emptyConfig, paths) => {
      val client = newInstanceZkClient()

      client.executeWithZk(() => ConfigFactory.parseMap(client.loadAttributesFromPaths(paths).asJava)) match {
        case Some(config) => Configuration(config)
        case None => emptyConfig
      }
    })
  }
}
