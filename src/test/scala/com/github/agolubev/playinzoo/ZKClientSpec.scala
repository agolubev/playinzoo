package com.github.agolubev.playinzoo

import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue, TimeUnit}

import com.github.agolubev.playinzoo.NodeTask._
import org.apache.zookeeper.KeeperException.NoNodeException
import org.apache.zookeeper.ZooKeeper
import org.apache.zookeeper.data.Stat
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.specs2.mock.Mockito
import org.specs2.mutable.{After, Specification}

import scala.collection.JavaConverters._

/**
 * Created by alexander golubev
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


  "ZKClient simple zk methods" should {

    "Run operations successfully" in new releaseMocks {
      val zk = mock[ZooKeeper]
      val client = createZKClient(zk)


      zk.getChildren("/a/b", false) returns List[String]("c", "d").asJava
      zk.exists("/a/b/c", false) returns mock[Stat]
      zk.getData("/a/b/c", false, null) returns "c_value".getBytes

      client.getChildren("/a/b") === "c" :: "d" :: Nil
      client.getData("/a/b/c") === Some("c_value")
      client.checkIfNodeExists("/a/b/c") === true
    }

    "Return empty or false if no such node" in new releaseMocks {
      val zk = mock[ZooKeeper]
      val client = createZKClient(zk)

      val path = "/a/b/a"

      zk.getChildren(path, false) throws new NoNodeException("")
      zk.getData(path, false, null) throws new NoNodeException("")
      zk.exists(path, false) returns null

      client.getChildren(path) === Nil
      client.getData(path) === None
      client.checkIfNodeExists(path) === false
    }

    "Return none if zk return exception" in new releaseMocks {
      val zk = mock[ZooKeeper]
      val client = createZKClient(zk)

      val path = "/a/b/a"

      zk.exists(path, false) throws new NoNodeException("")

      client.checkIfNodeExists(path) === false
    }
  }

  "ZKClient " should {
    "Load root in simple mode" in new releaseMocks {
      val zkLoadingResult = new LinkedBlockingQueue[Node]()
      val path = "/a/b"
      val zk = mock[ZooKeeper]
      val client = createZKClient(zk)

      zk.getChildren(path, false) returns List[String]("c", "d").asJava
      zk.exists(path, false) returns mock[Stat]

      client.loadAttributesFromPath(new Node("/a/", "b", SimpleRoot, false, None), zkLoadingResult)

      verifyNode(zkLoadingResult, SimpleRoot, Some(NodeContent(List("c", "d"), None)))

      there was no(zk).getData(path, false, null)
    }

    "Load leaf in recursive mode" in new releaseMocks {
      val zkLoadingResult = new LinkedBlockingQueue[Node]()
      val path = "/a/b"
      val value = "b_value"
      val zk = mock[ZooKeeper]
      val client = createZKClient(zk)

      zk.exists(path, false) returns mock[Stat]
      zk.getData(path, false, null) returns value.getBytes

      client.loadAttributesFromPath(new Node("/a/", "b", SimpleLeaf, false, None), zkLoadingResult)

      verifyNode(zkLoadingResult, SimpleLeaf, Some(NodeContent(List(), Some(value))))

      there was no(zk).getChildren(path, false)
    }

    "Load leaf in recursive mode" in new releaseMocks {
      val zkLoadingResult = new LinkedBlockingQueue[Node]()
      val path = "/a/b/c"
      val value = "c_value"
      val zk = mock[ZooKeeper]
      val client = createZKClient(zk)

      zk.exists(path, false) returns mock[Stat]
      zk.getChildren(path, false) returns List[String]().asJava
      zk.getData(path, false, null) returns value.getBytes

      client.loadAttributesFromPath(new Node("/a/b/", "c", Recursive, false, None), zkLoadingResult)

      verifyNode(zkLoadingResult, Recursive, Some(NodeContent(List(), Some(value))))

    }

    "Load non leaf node in recursive mode" in new releaseMocks {
      val zkLoadingResult = new LinkedBlockingQueue[Node]()
      val path = "/a/b/d"
      val zk = mock[ZooKeeper]
      val client = createZKClient(zk)

      zk.exists(path, false) returns mock[Stat]
      zk.getChildren(path, false) returns List[String]("e", "f").asJava

      client.loadAttributesFromPath(new Node("/a/b/", "d", Recursive, false, None), zkLoadingResult)

      verifyNode(zkLoadingResult, Recursive, Some(NodeContent(List("e", "f"), None)))

      there was no(zk).getData(path, false, null)
    }

    "Load via loop recursively" in new releaseMocks {
      val path = "/a/b/**"
      val client = spy(new ZkClient("", "", 3, None, None, 1))

      org.mockito.Mockito.doAnswer(new Answer[Unit] {
        def answer(invocation: InvocationOnMock): Unit = {
          val node = invocation.getArguments()(0).asInstanceOf[Node]
          val queue = invocation.getArguments()(1).asInstanceOf[BlockingQueue[Node]]
          node.path + node.name match {
            case "/a/b" => queue.put(node.loadingDone(NodeContent(List[String]("c", "d"), None)))
            case "/a/b/c" => queue.put(node.loadingDone(NodeContent(Nil, Some("c_value"))))
            case "/a/b/d" => queue.add(node.loadingDone(NodeContent(List[String]("e", "f"), None)))
            case "/a/b/d/e" => queue.add(node.loadingDone(NodeContent(Nil, Some("e_value"))))
            case "/a/b/d/f" => queue.add(node.loadingDone(NodeContent(Nil, Some("f_value"))))
            case path: String => failure("Node " + path + " must not be requested")
          }

        }
      }).when(client).loadAttributesFromPath(any[Node], any[BlockingQueue[Node]])

      val map = client.loadingLoop(List[String](path))
      map === Map("c" -> "c_value", "e" -> "e_value", "f" -> "f_value")
    }

    "Load via loop all children of given node" in new releaseMocks {
      val path = "/a/b"
      val client = spy(new ZkClient("", "", 3, None, None, 1))

      org.mockito.Mockito.doAnswer(new Answer[Unit] {
        def answer(invocation: InvocationOnMock): Unit = {
          val node = invocation.getArguments()(0).asInstanceOf[Node]
          val queue = invocation.getArguments()(1).asInstanceOf[BlockingQueue[Node]]
          node.path + node.name match {
            case "/a/b" => queue.put(node.loadingDone(NodeContent(List[String]("c", "d"), None)))
            case "/a/b/c" => queue.put(node.loadingDone(NodeContent(Nil, Some("c_value"))))
            case "/a/b/d" => queue.add(node.loadingDone(NodeContent(Nil, Some("d_value"))))
            case path: String => failure("Node " + path + " must not be requested")
          }

        }
      }).when(client).loadAttributesFromPath(any[Node], any[BlockingQueue[Node]])

      val map = client.loadingLoop(List[String](path))
      map === Map("c" -> "c_value", "d" -> "d_value")
    }
  }

  def createZKClient(zk: ZooKeeper) = {
    val client = new ZkClient("", "", 3, None, None, 1)
    client.zk = zk
    client
  }

  def verifyNode(queue: BlockingQueue[Node], task: NodeTask, content: Option[NodeContent]) = {
    val node = queue.poll(2, TimeUnit.SECONDS)
    node.loaded === true
    node.task === task
    node.content === content
  }

  trait releaseMocks extends After {
    def after = {
      org.mockito.Mockito.reset()
    }
  }

}
