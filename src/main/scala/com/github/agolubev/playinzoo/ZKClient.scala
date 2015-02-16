package com.github.agolubev.playinzoo

import java.io.IOException
import java.util.concurrent.{TimeUnit, CountDownLatch}

import org.apache.zookeeper.Watcher.Event.KeeperState
import org.apache.zookeeper.{WatchedEvent, ZooKeeper, Watcher}
import play.api.Logger
import scala.collection.JavaConverters._

/**
 * Created by alexandergolubev.
 */
class ZkClient(hosts: String, root: String, timeout: Int = 3000) {

  var zk: ZooKeeper = null
  val connectedSignal = new CountDownLatch(1)

  def connect(): Boolean = {
    try {
      zk = new ZooKeeper(hosts, timeout, new Watcher() {
        override def process(event: WatchedEvent): Unit =
          if (event.getState() == KeeperState.SyncConnected) {
            connectedSignal.countDown()
          }
      })
      connectedSignal.await(3, TimeUnit.SECONDS)
    } catch {
      case e: IOException => Logger.error(e.getMessage); false
    }
  }

  def loadAttributesFromPaths(paths: String): Map[String, Any] = {
    paths.split(",").foldRight[Map[String, Any]](Map.empty[String, Any])((a, m) => m ++ loadAttributesFromPath(a))
  }

  def loadAttributesFromPath(path: String): Map[String, Any] = {
    if (zk.exists(path, false) != null) {

      val keys = zk.getChildren(path, false).asScala

      keys.map(key => {
        key -> new String(zk.getData(path + "/" + key, false, null))
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
