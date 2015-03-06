import org.scoverage.coveralls.CoverallsPlugin
import play.PlayScala
import sbt._
import Keys._
import CoverallsPlugin.CoverallsKeys._

object ApplicationBuild extends Build {


  lazy val PlayInZooProject = Project(
    "play-in-zoo",
    new File("."),
    settings = BuildSettings.buildSettings ++ Publishing.publishSettings ++ Seq(
      libraryDependencies <++= scalaVersion(sv => Seq(play(sv)) ++ Dependencies.runtime),
      publishMavenStyle := true,
      resolvers ++= Seq("Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
                       "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"),
      scalacOptions in Test ++= Seq("-Yrangepos"),
      scalacOptions ++= Seq( "-deprecation", "-feature"),
      coverallsTokenFile := "./token.txt"
    )
  )

  lazy val PlayInZooSample = Project("play-in-zoo-sample", new File("./samples/playinzoo-sample")).dependsOn(PlayInZooProject).enablePlugins(PlayScala)

  object Dependencies {
    val runtime = Seq(
      "com.typesafe" % "config" % "1.0.2",

      "ch.qos.logback" % "logback-core" % "1.0.13",
      "ch.qos.logback" % "logback-classic" % "1.0.13",

      "org.apache.zookeeper" % "zookeeper" % "3.4.6" notTransitive() exclude("org.slf4j", "slf4j-log4j12"),
      
      //we need this to avoid workaround of specs2-mockito. it contains rewritten classes of mockito
      "org.mockito" % "mockito-core" % "1.9.5" % "test", 
      "org.specs2" %% "specs2-core" % "2.3.11" % "test" cross CrossVersion.binary,
      "org.specs2" %% "specs2-mock" % "2.3.11" % "test" cross CrossVersion.binary
    )
  }

  def play(scalaVersion: String) =
    scalaVersion match {
      case "2.11.2" => "com.typesafe.play" %% "play" % "2.3.8" % "provided"
      case _ => "com.typesafe.play" %% "play" % "2.2.6" % "provided"
    }

  object Publishing {
    val publishSettings = Seq(
      publishMavenStyle := true,
      publishArtifact := true,
      publishTo := {
        val nexus = "https://oss.sonatype.org/"
        if (isSnapshot.value)
          Some("snapshots" at nexus + "content/repositories/snapshots")
        else
          Some("releases" at nexus + "service/local/staging/deploy/maven2")
      },
      licenses := Seq("Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
      homepage := Some(url("https://github.com/agolubev/playinzoo")),
      publishArtifact in Test := false,
      pomExtra := <scm>
        <url>git://github.com/agolubev/playinzoo.git</url>
        <connection>scm:git://github.com/agolubev/playinzoo.git</connection>
      </scm>
        <developers>
          <developer>
            <id>agolubev</id>
            <name>Alexander Golubev</name>
            <url>http://github.com/agolubev</url>
          </developer>
        </developers>
    )
  }

  object BuildSettings {
    val buildScalaVersion = "2.11.2"

    val buildSettings = Seq(
      organization := "com.github.agolubev",
      version := "0.3",
      scalaVersion in ThisBuild := buildScalaVersion,
      crossScalaVersions := Seq("2.11.2", "2.10.3")
    )
  }

}
