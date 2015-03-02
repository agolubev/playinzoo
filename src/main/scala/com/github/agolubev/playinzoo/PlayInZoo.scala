package com.github.agolubev.playinzoo

import com.typesafe.config.ConfigFactory
import play.api.{Configuration, Logger}

import scala.collection.JavaConversions._
import scala.util.control.Exception._

/**
 * Created by alexandergolubev
 */
object PlayInZoo {

  val ENCODING = "UTF-8";

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
    client.loadAttributesFromPaths(paths).map { case (key, value) => (key, parseByteArray(value.array))}
  }

  private def parseByteArray(value: Array[Byte]): AnyRef = {
    val str = new String(value.toArray, PlayInZoo.ENCODING)
    catching[AnyRef](classOf[IllegalArgumentException]).opt(Boolean.box(str.toBoolean)).getOrElse(
      catching[AnyRef](classOf[NumberFormatException]).opt(Int.box(str.toInt)).getOrElse(
        catching(classOf[NumberFormatException]).opt(Long.box(str.toLong)).getOrElse(
          catching[AnyRef](classOf[NumberFormatException]).opt(Double.box(str.toDouble)).getOrElse(str)
        )))
  }
}
