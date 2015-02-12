import sbt._
import Keys._

object ApplicationBuild extends Build {

  lazy val PlayInZooProject = Project(
    "play-in-zoo",
    new File("."),
    settings = BuildSettings.buildSettings ++ Seq(
      libraryDependencies := Dependencies.runtime,
      publishMavenStyle := true,
      resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
    )
  )

  object Dependencies {
    val runtime = Seq(
      "com.typesafe.play" %% "play" % "2.2.2",
      "com.typesafe" % "config" % "1.0.2",
      "ch.qos.logback" % "logback-core" % "1.0.13",
      "ch.qos.logback" % "logback-classic" % "1.0.13",
      "org.scala-lang" % "scala-library" % BuildSettings.buildScalaVersion,
      "org.apache.zookeeper" % "zookeeper" % "3.4.6" notTransitive() exclude("org.slf4j", "slf4j-log4j12"),
      "org.scalatest" %% "scalatest" % "1.9.1" % "test",
      "org.specs2" %% "specs2" % "1.12.3" % "test"
    )
  }

  object BuildSettings {
    val buildOrganization = "com.github.agolubev"
    val buildVersion = "0.3"
    val buildScalaVersion = "2.10.3"
    val buildSbtVersion = "0.13"
    val buildSettings = Defaults.defaultSettings ++ Seq(
      organization := buildOrganization,
      version := buildVersion,
      scalaVersion := buildScalaVersion
    )
  }

}
