package com.github.agolubev.playinzoo

import com.typesafe.config.ConfigFactory
import play.api.Configuration
import scala.collection.JavaConverters._
import play.api.Logger

/**
 * Created by alexandergolubev
 */
object PlayInZoo {

  def loadConfiguration(configuration: Configuration): Configuration = {
    Logger.debug("Loading configuration from zookeeper")

    val client = new ZkClient(
      configuration.getString("playinzoo.hosts").getOrElse({
        Logger.warn("playinzoo.hosts is not set uses default value: localhost")
        "localhost:2181"
      }),
      configuration.getString("playinzoo.root").getOrElse("/"),
      configuration.getInt("playinzoo.timeout").getOrElse(3000)
    )

    client.connect()

    def checkPathsParam(attr: Option[String]): Map[String, Any] = attr match {
      case None => Logger.error("playinzoo.paths configuration attribute is not set"); Map.empty[String, Any]
      case Some(k) => client.loadAttributesFromPaths(k)
    }

    val conf = Configuration(ConfigFactory.parseMap(checkPathsParam(configuration.getString("playinzoo.paths")).asJava))

    Logger.debug("Loaded config attributes "+conf.toString)

    client.close()

    Logger.debug("Loading configuration done")

    conf
  }

}
