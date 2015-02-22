package com.github.agolubev.playinzoo

import java.io.IOException
import java.util.concurrent._

import com.github.agolubev.playinzoo.ZkClient._
import org.apache.zookeeper.Watcher.Event.KeeperState
import org.apache.zookeeper.{KeeperException, WatchedEvent, Watcher, ZooKeeper}
import org.slf4j.LoggerFactory
import play.api.Logger

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent._
import scala.util.{Failure, Success}

/**
 * Created by alexander golubev.
 */
class ZkClient(hosts: String, root: String, timeout: Int = 3000, schema: Option[String], auth: Option[String], threadsNumber: Int) {
  def logger = LoggerFactory.getLogger(this.getClass)

  var zk: ZooKeeper = null
  val CONNECTION_TIMEOUT_SEC: Long = 3

  // set thread number for futures pool
  implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(threadsNumber))

  def connect(): Boolean = {
    val connectedSignal = new CountDownLatch(1)

    try {
      zk = new ZooKeeper(hosts, timeout, new Watcher() {
        override def process(event: WatchedEvent): Unit =
          if (event.getState() == KeeperState.SyncConnected) {
            connectedSignal.countDown()
          }
      })

      //set auth info
      schema.foreach(schemaName => zk.addAuthInfo(schemaName, auth.getOrElse("").getBytes))

      connectedSignal.await(CONNECTION_TIMEOUT_SEC, TimeUnit.SECONDS)
    } catch {
      case e: IOException => logger.error(e.getMessage); false
    }
  }

  def loadAttributesFromPaths(paths: String): Map[String, Any] =
    loadingLoop(paths.split(",").map(_ trim).toList)

  /**
   * Scenario 1 - simple 
   * get listing for root
   * get data for each
   * simple root -> simple leaf
   *
   * Scenario 2 - recursive
   * get listing
   * for each check if has child - if yes get listing
   * if no children - get data
   * recursive root (has children - list / no children get value)
   *
   * @param paths
   * @return
   */
  protected[playinzoo] def loadingLoop(paths: List[String]): Map[String, Any] = {
    import NodeTask._

    var readProperties = new mutable.HashMap[String, Any]()
    val zkLoadingResult = new LinkedBlockingQueue[Node]()
    val runningFutures = new mutable.HashSet[String]()


    for (path <- paths) {
      val (plainPath, recursive) = parsePathForRecursiveness(path)
      val task = if (recursive) Recursive else SimpleRoot
      val (p, name) = getNodeNameAndPath(plainPath)
      zkLoadingResult.add(Node(p, name, task, false, None))
    }

    var node: Node = null

    while ( {
      node = zkLoadingResult.take()
      node
    } != null) {
      if (node.loaded) runningFutures.remove(node.getFullPath())

      //TODO consider adding here a timeout
      node.task match {
        case SimpleRoot =>
          if (node.loaded) loadChildren(node, SimpleLeaf) else loadAttributesHelper2(node)
        case Recursive =>
          if (node.loaded) {
            node.content.foreach(content =>
              if (content.children.isEmpty) // if it's leaf
                addNodeContentAsProperty(node)
              else //it's node
                loadChildren(node, Recursive))
          }
          else loadAttributesHelper2(node)
        case SimpleLeaf => addNodeContentAsProperty(node)
      }

      def addNodeContentAsProperty(node: Node) =
        node.content.foreach(c => c.getAsProperty(node.name).foreach(readProperties += _))

      def loadChildren(node: Node, task: NodeTask): Unit =
        node.content.foreach(content =>
          for (childName <- content.children) {
            runningFutures.add(node.getFullPath() + "/" + childName)
            loadAttributesHelper(node.getFullPath() + "/", childName, task) //TODO check if ZK required train slash
          }
        )

      def loadAttributesHelper(path: String, name: String, task: NodeTask): Unit = {
        runningFutures.add(generateFullPath(path, name))
        loadAttributesFromPath(Node(path, name, task, false, None), zkLoadingResult)
      }

      def loadAttributesHelper2(node: Node): Unit = {
        runningFutures.add(node.getFullPath())
        loadAttributesFromPath(node, zkLoadingResult)
      }

      if (runningFutures.size == 0) return readProperties.toMap
    }
    readProperties.toMap
  }

  private def requestZookeeper[A](f: () => A): Option[A] = {
    try {
      val result = f()
      if (result == null) None else Some(result)
    } catch {
      case e@(_: KeeperException | _: InterruptedException) => logger.error(e.getMessage); None
    }
  }

  def loadAttributesFromPath(node: Node, responses: BlockingQueue[Node]) =
    future {
      import NodeTask._
      
      logger.debug("Requesting info for node " + node.getFullPath() + " from ZK in thread " + Thread.currentThread().getName())
      
      if (checkIfNodeExists(node.getFullPath()))
        node.task match {
          case SimpleRoot => node.loadingDone(NodeContent(getChildren(node.getFullPath()), None))
          case Recursive => {
            val children = getChildren(node.getFullPath())
            if (children.isEmpty)
              node.loadingDone(NodeContent(children, getData(node.getFullPath())))
            else
              node.loadingDone(NodeContent(children, None))
          }
          case SimpleLeaf => node.loadingDone(NodeContent(List.empty, getData(node.getFullPath())))
        }
      else
        node
    } onComplete {
      case Success(node) => responses.add(node)
      case Failure(e) => responses.add(node); logger.warn(e.getMessage)
    }


  // not important
  private[playinzoo] def checkIfNodeExists(plainPath: String): Boolean = {
    requestZookeeper(() => {
      logger.debug("Zk: exists is calling, path:" + plainPath)
      zk.exists(plainPath, false)
    }).map(_ != null) getOrElse (false)
  }

  private[playinzoo] def getChildren(plainPath: String): List[String] =
    requestZookeeper(() => {
      logger.debug("Zk: getChildren is calling, path:" + plainPath)
      zk.getChildren(plainPath, false)
    }).getOrElse(List.empty[String].asJava).asScala.toList

  private[playinzoo] def getData(plainPath: String): Option[String] =
    requestZookeeper(() => {
      logger.debug("Zk: getData is calling, path:" + plainPath)
      zk.getData(plainPath, false, null)
    }).map(new String(_)) //todo consider encoding


  def close(): Unit = {
    zk.close()
  }

}

object ZkClient {

  def getNodeNameAndPath(plainPath: String): (String, String) = {
    val path = if (plainPath.endsWith("/")) plainPath dropRight (1)
    else plainPath

    path splitAt (path.lastIndexOf('/') + 1)
  }

  def matches(pattern: String, str: String) = pattern.r.pattern.matcher(str).matches()

  def parsePathForRecursiveness(path: String): (String, Boolean) = {
    if (matches("[^\\*]+\\*\\*$", path)) {
      (path.dropRight(2), true)
    } else if (matches("[^\\*]+\\*$", path)) {
      (path.dropRight(1), false)
    } else (path, false)
  }

  def generateFullPath(path: String, name: String) = if (path.endsWith("/")) path + name else path + "/" + name //consider when there is slash in name
}


sealed case class Node(path: String, name: String, task: NodeTask.Value, var loaded: Boolean, var content: Option[NodeContent]) {

  def getFullPath() = generateFullPath(path, name)

  def loadingDone(c: NodeContent): Node = {
    loaded = true
    content = Some(c)
    this
  }
}

sealed case class NodeContent(children: List[String], value: Option[String]) {
  def isLeaf() = children.isEmpty

  def getAsProperty(name: String) = value.map(name -> _)
}

object NodeTask extends Enumeration {
  type NodeTask = Value
  val SimpleRoot, SimpleLeaf, Recursive = Value

}
