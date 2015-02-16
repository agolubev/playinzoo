package com.github.agolubev.playinzoo

import java.io.IOException
import java.util.concurrent.{TimeUnit, CountDownLatch}

import org.apache.zookeeper.Watcher.Event.KeeperState
import org.apache.zookeeper.{KeeperException, WatchedEvent, ZooKeeper, Watcher}
import play.api.Logger
import scala.collection.JavaConverters._

/**
 * Created by alexandergolubev.
 */
class ZkClient(hosts: String, root: String, timeout: Int = 3000, schema: Option[String], auth: Option[String]) {

  var zk: ZooKeeper = null
  val connectedSignal = new CountDownLatch(1)
  val CONNECTION_TIMEOUT_SEC: Long = 3

  def connect(): Boolean = {

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
      case e: IOException => Logger.error(e.getMessage); false
    }
  }

  def loadAttributesFromPaths(paths: String): Map[String, Any] = {
    paths.split(",").foldRight[Map[String, Any]](Map.empty[String, Any])((a, m) => m ++ loadAttributesFromPath(a))
  }

  private def requestZookeeper[A](f: () => A): Option[A] = {
    try {
      Some(f())
    } catch {
      case e@(_: KeeperException | _: InterruptedException) => Logger.error(e.getMessage); None
    }
  }

  def loadAttributesFromPath(path: String): Map[String, Any] = {
    if (requestZookeeper(()=>zk.exists(path, false)) != null) {

      val keys = requestZookeeper(() => zk.getChildren(path, false).asScala).getOrElse(List.empty)

      keys.flatMap(key => {
        requestZookeeper(() => zk.getData(path + "/" + key, false, null)) match {
          case Some(k) => key -> new String(zk.getData(path + "/" + key, false, null)) :: Nil
          case None => List.empty
        }
      }).toMap

    } else {

      Logger.warn("Path: " + path + " does not exist in zookeeper")

      Map.empty[String, Any]
    }
  }


  def close(): Unit = {
    zk.close()
  }
}
