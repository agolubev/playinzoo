package com.github.agolubev.playinzoo

import org.apache.zookeeper.KeeperException.NoNodeException
import org.apache.zookeeper.ZooKeeper
import org.apache.zookeeper.data.Stat
import org.specs2.mock.Mockito
import org.specs2.mutable.{After, Specification}
import scala.collection.JavaConverters._

/**
 * Created by alexandergolubev
 */
class ZkClientSpec extends Specification with Mockito {

  "Utility methods" should {

    "split path and name" in {
      ZkClient.getNodeNameAndPath("/a/b/c") ===("/a/b/", "c")
      ZkClient.getNodeNameAndPath("/a/b/c/") ===("/a/b/", "c")
    }

    "split name and path in case of root" in {
      ZkClient.parsePathForRecursiveness("/a/b/c/*") ===("/a/b/c/", false)
      ZkClient.parsePathForRecursiveness("/a/b/c/") ===("/a/b/c/", false)

      ZkClient.parsePathForRecursiveness("/a/b/c/**") ===("/a/b/c/", true)
    }

    "not split name and path if more then two stars" in {
      ZkClient.parsePathForRecursiveness( """/a/b/c/****""") ===( """/a/b/c/****""", false)
    }
  }

  //assuming we have hierarchy
  // /a/b(b_value) -> c (c_value)
  //               -> d (d_value) -> e (e_value)
  //                              -> f (f_value)


  val client = new ZkClient("", "", 3, None, None, 1)

  "ZKClient " should {

    "run simple operations successfully" in new releaseMocks {
      val zk = mock[ZooKeeper]
      client.zk = zk
      
      zk.getChildren("/a/b", false) returns List[String]("c","d").asJava
      zk.exists("/a/b/c", false) returns mock[Stat]
      zk.getData("/a/b/c", false, null) returns "c_value".getBytes

      client.getChildren("/a/b") === "c" :: "d" :: Nil
      client.getData("/a/b/c") === Some("c_value")
      client.checkIfNodeExists("/a/b/c") === true
    }

   "Return empty or false if no such node" in new releaseMocks {
      val zk2 = mock[ZooKeeper]
      client.zk = zk2
      
      val path="/a/b/a"
      
      zk2.getChildren(path, false) throws new NoNodeException("")
      zk2.getData(path, false, null) throws new NoNodeException("")
      zk2.exists(path, false) returns null

      client.getChildren(path) === Nil
      client.getData(path) === None
      client.checkIfNodeExists(path) === false

    }

    "return none if zk return exception" in new releaseMocks {
      val zk3 = mock[ZooKeeper]
      client.zk = zk3

      val path="/a/b/a"
      
      zk3.exists(path, false) throws new NoNodeException("")

      client.checkIfNodeExists(path) === false
      org.mockito.Mockito.reset()
    }
  }

  trait releaseMocks extends After{
    def after = org.mockito.Mockito.reset()
  }

}
