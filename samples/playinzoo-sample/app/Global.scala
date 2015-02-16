import java.io.File

import com.github.agolubev.playinzoo.PlayInZoo
import com.typesafe.config.ConfigFactory
import play.api.Mode.Mode
import play.api._

import scala.collection.immutable

object Global extends GlobalSettings {


  override def onLoadConfig(config: Configuration, path: File, classloader: ClassLoader, mode: Mode): Configuration = {
    config ++ PlayInZoo.loadConfiguration(config)
  }

  override def onStart(app: Application) {
    Logger.info("Application has started")
  }

  override def onStop(app: Application) {
    Logger.info("Application shutdown...")
  }

}