package com.github.agolubev.playinzoo

import com.typesafe.config.{Config, ConfigFactory}
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.Configuration

import scala.collection.JavaConverters._

/**
 * Created by alexander golubev
 */
class PlayInZooSpec extends Specification with Mockito {

  "PlayInZoo" should {
    val configMap = Map("playinzoo.hosts" -> "localhost:2181",
      "playinzoo.paths" -> "/a/b",
      "playinzoo.root" -> "/",
      "playinzoo.timeout" -> 200,
      "playinzoo.schema" -> "schema",
      "playinzoo.auth" -> "auth",
      "playinzoo.threadpool.size" -> 2)


    "Send to ZkClient all proper configuration for connection" in {

      val zkClient: ZkClient =
        new PlayInZoo(Configuration(ConfigFactory.parseMap(configMap.asJava))).newInstanceZkClient()
      zkClient.hosts === configMap.get("playinzoo.hosts").get
      zkClient.root === configMap.get("playinzoo.root").get
      zkClient.schema === configMap.get("playinzoo.schema")
      zkClient.auth === configMap.get("playinzoo.auth")
      zkClient.threadsNumber === configMap.get("playinzoo.threadpool.size").get
    }

    "Load properties from zkCLient" in {
      val playInZoo: PlayInZoo =
        spy(new PlayInZoo(Configuration(ConfigFactory.parseMap(configMap.asJava))))

      val zkClient = mock[ZkClient]
      
      zkClient.loadAttributesFromPaths("/a/b") returns Map("b" -> "b_value")

      org.mockito.Mockito.doReturn(zkClient).when(playInZoo).newInstanceZkClient()
      org.mockito.Mockito.doAnswer(new Answer[Option[Config]] {
        def answer(invocation: InvocationOnMock): Option[Config] = {
          val f = invocation.getArguments()(0).asInstanceOf[Function0[Config]]
          Some(f.apply())
        }
      }).when(zkClient).executeWithZk(any)
      
      playInZoo.loadConfiguration().getString("b") === Some("b_value")
      there were 1.times(zkClient).loadAttributesFromPaths("/a/b")
      there were 1.times(zkClient).executeWithZk(any)
      
    }

    "return empty config if not paths specified" in {
      val newMap = configMap - "playinzoo.paths"

      val playInZoo: PlayInZoo =
        spy(new PlayInZoo(Configuration(ConfigFactory.parseMap(newMap.asJava))))

      val zkClient = mock[ZkClient]

      org.mockito.Mockito.doReturn(zkClient).when(playInZoo).newInstanceZkClient()

      playInZoo.loadConfiguration() === Configuration.empty

      there was no(zkClient).loadingLoop(any)
    }
  }
}