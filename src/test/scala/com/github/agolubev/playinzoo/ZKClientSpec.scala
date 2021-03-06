package com.github.agolubev.playinzoo

import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue, TimeUnit}

import com.github.agolubev.playinzoo.NodeTask._
import org.apache.zookeeper.KeeperException.NoNodeException
import org.apache.zookeeper.Watcher.Event.{EventType, KeeperState}
import org.apache.zookeeper.{WatchedEvent, Watcher, ZooKeeper}
import org.apache.zookeeper.data.Stat
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.specs2.mock.Mockito
import org.specs2.mutable.{After, Specification}

import scala.collection.JavaConverters._
import scala.collection.mutable

/**
 * Created by alexander golubev
 */
class ZkClientSpec extends Specification with Mockito {

  val ENC = "UTF-8"

  "Utility methods" should {

    "Split path and name" in {
      ZkClient.getNodeNameAndPath("/a/b/c") ===("/a/b/", "c")
      ZkClient.getNodeNameAndPath("/a/b/c/") ===("/a/b/", "c")
    }

    "Parse path for recursive loading" in {
      ZkClient.parsePathForRecursiveness("/a/b/c/*") ===("/a/b/c/", false)
      ZkClient.parsePathForRecursiveness("/a/b/c/") ===("/a/b/c/", false)

      ZkClient.parsePathForRecursiveness("/a/b/c/**") ===("/a/b/c/", true)
    }

    "Do not split name and path if more then two stars" in {
      ZkClient.parsePathForRecursiveness( """/a/b/c/****""") ===( """/a/b/c/****""", false)
    }
  }

  // assuming we have hierarchy
  // /a/b(b_value) -> c (c_value)
  //               -> d (d_value) -> e (e_value)
  //                              -> f (f_value)

  val b_path = "/a/b"
  val c_path = "/a/b/c"
  val d_path = "/a/b/d"
  val e_path = "/a/b/d/e"
  val f_path = "/a/b/d/f"

  val b_value = "b_value"
  val c_value = "c_value"
  val d_value = "d_value"
  val e_value = "e_value"
  val f_value = "f_value"

  "ZKClient (basic cases) " should {

    "Run requeststo zookeeper successfully" in new releaseMocks {
      val zk = mock[ZooKeeper]
      val client = createZKClient(zk)


      zk.getChildren(b_path, false) returns List[String]("c", "d").asJava
      zk.exists(c_path, false) returns mock[Stat]
      zk.getData(c_path, false, null) returns c_value.getBytes

      client.getChildren(b_path) === "c" :: "d" :: Nil
      client.getData(c_path) === Some(wrap(c_value))
      client.checkIfNodeExists(c_path) === true
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

    "Return None if zk returns exception" in new releaseMocks {
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

    "Load leaf in simple mode" in new releaseMocks {
      val zkLoadingResult = new LinkedBlockingQueue[Node]()
      val zk = mock[ZooKeeper]
      val client = createZKClient(zk)

      zk.exists(b_path, false) returns mock[Stat]
      zk.getData(b_path, false, null) returns b_value.getBytes(ENC)

      client.loadAttributesFromPath(new Node("/a/", "b", SimpleLeaf, false, None), zkLoadingResult)

      verifyNode(zkLoadingResult, SimpleLeaf, Some(NodeContent(List(), Some(wrap(b_value)))))

      there was no(zk).getChildren(b_path, false)
    }

    "Load leaf in recursive mode" in new releaseMocks {
      val zkLoadingResult = new LinkedBlockingQueue[Node]()
      val zk = mock[ZooKeeper]
      val client = createZKClient(zk)

      zk.exists(c_path, false) returns mock[Stat]
      zk.getChildren(c_path, false) returns List[String]().asJava
      zk.getData(c_path, false, null) returns wrap(c_value).toArray

      client.loadAttributesFromPath(new Node(b_path, "c", Recursive, false, None), zkLoadingResult)

      verifyNode(zkLoadingResult, Recursive, Some(NodeContent(List(), Some(wrap(c_value)))))
    }

    "Load children of folder node in recursive mode" in new releaseMocks {
      val zkLoadingResult = new LinkedBlockingQueue[Node]()
      val zk = mock[ZooKeeper]
      val client = createZKClient(zk)

      zk.exists(d_path, false) returns mock[Stat]
      zk.getChildren(d_path, false) returns List[String]("e", "f").asJava

      client.loadAttributesFromPath(new Node(b_path, "d", Recursive, false, None), zkLoadingResult)

      verifyNode(zkLoadingResult, Recursive, Some(NodeContent(List("e", "f"), None)))

      there was no(zk).getData(d_path, false, null)
    }

    "Load via loop recursively" in new releaseMocks {
      val client = spy(new ZkClient("", "", 3, None, None, 1))

      org.mockito.Mockito.doAnswer(new Answer[Unit] {
        def answer(invocation: InvocationOnMock): Unit = {
          val node = invocation.getArguments()(0).asInstanceOf[Node]
          val queue = invocation.getArguments()(1).asInstanceOf[BlockingQueue[Node]]
          node.path + node.name match {
            case `b_path` => queue.put(node.loadingDone(NodeContent(List[String]("c", "d"), None)))
            case `c_path` => queue.put(node.loadingDone(NodeContent(Nil, Some(wrap(c_value)))))
            case `d_path` => queue.add(node.loadingDone(NodeContent(List[String]("e", "f"), None)))
            case `e_path` => queue.add(node.loadingDone(NodeContent(Nil, Some(wrap(e_value)))))
            case `f_path` => queue.add(node.loadingDone(NodeContent(Nil, Some(wrap(f_value)))))
            case path: String => failure("Node " + path + " must not be requested")
          }

        }
      }).when(client).loadAttributesFromPath(any[Node], any[BlockingQueue[Node]])

      val map = client.loadingLoop(List[String]("/a/b/**"))
      map === Map("c" -> wrap(c_value), "e" -> wrap(e_value), "f" -> wrap(f_value))
    }

    "Load via loop all children of given node in simple mode" in new releaseMocks {
      val client = spy(new ZkClient("", "", 3, None, None, 1))

      org.mockito.Mockito.doAnswer(new Answer[Unit] {
        def answer(invocation: InvocationOnMock): Unit = {
          val node = invocation.getArguments()(0).asInstanceOf[Node]
          val queue = invocation.getArguments()(1).asInstanceOf[BlockingQueue[Node]]
          node.path + node.name match {
            case `b_path` => queue.put(node.loadingDone(NodeContent(List[String]("c", "d"), None)))
            case `c_path` => queue.put(node.loadingDone(NodeContent(Nil, Some(wrap(c_value)))))
            case `d_path` => queue.add(node.loadingDone(NodeContent(Nil, Some(wrap(d_value)))))
            case path: String => failure("Node " + path + " must not be requested")
          }

        }
      }).when(client).loadAttributesFromPath(any[Node], any[BlockingQueue[Node]])

      val map = client.loadingLoop(List[String](b_path))
      map === Map("c" -> wrap(c_value), "d" -> wrap(d_value))
    }


    "Execute in context of Zookeeper connection" in new releaseMocks {
      val zkClient = spy(new ZkClient("", "", 3, Some("schema"), Some("auth"), 1))

      org.mockito.Mockito.doReturn(true).when(zkClient).connect()
      org.mockito.Mockito.doReturn(true).when(zkClient).close()
      org.mockito.Mockito.doReturn(Map(b_path -> b_value)).when(zkClient).loadingLoop(any[List[String]])

      zkClient.executeWithZk(() => zkClient.loadAttributesFromPaths(b_path)) === Some(Map(b_path -> b_value))
      there were 1.times(zkClient).loadingLoop(any[List[String]])
      there were 1.times(zkClient).connect()
      there were 1.times(zkClient).close()
    }

    "Connect to zookeeper synchronously" in new releaseMocks {
      val zkClient = spy(new ZkClient("", "", 3, Some("schema"), Some("auth"), 1))

      val zk = mock[ZooKeeper]

      org.mockito.Mockito.doAnswer(new Answer[ZooKeeper] {
        def answer(invocation: InvocationOnMock): ZooKeeper = {
          val watcher = invocation.getArguments()(2).asInstanceOf[Watcher]
          watcher.process(new WatchedEvent(EventType.None, KeeperState.SyncConnected, ""))
          zk
        }
      }).when(zkClient).newZooKeeperClient(any[String], any[Int], any[Watcher])

      zkClient.connect() === true
      there were 1.times(zk).addAuthInfo(any[String], any[Array[Byte]])
    }

    "Return None when problem with connection" in new releaseMocks {
      val zkClient = spy(new ZkClient("", "", 3, Some("schema"), Some("auth"), 1))
      org.mockito.Mockito.doReturn(false).when(zkClient).connect()

      zkClient.executeWithZk(() => zkClient.loadAttributesFromPaths(b_path)) === None
      there was no(zkClient).loadAttributesFromPaths(any[String])
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

  def wrap(str: String): mutable.WrappedArray[Byte] =
    mutable.WrappedArray.make[Byte](str.getBytes(ENC))


  trait releaseMocks extends After {
    def after = org.mockito.Mockito.reset()

  }

}
