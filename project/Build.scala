import sbt._
import Keys._

object ApplicationBuild extends Build {

  lazy val PlayInZooProject = Project(
    "play-in-zoo",
    new File("."),
    settings = BuildSettings.buildSettings ++ Publishing.publishSettings ++ Seq(
      libraryDependencies := Dependencies.runtime,
      publishMavenStyle := true,
      resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
      scalacOptions in Test ++= Seq("-Yrangepos")
    )
  )

  lazy val PlayInZooSample = Project("play-in-zoo-sample", new File("./samples/playinzoo-sample")).dependsOn(PlayInZooProject)

  
  object Dependencies {
    val runtime = Seq(
      "com.typesafe.play" %% "play" % "2.2.2",
      "com.typesafe" % "config" % "1.0.2",
    
      "ch.qos.logback" % "logback-core" % "1.0.13",
      "ch.qos.logback" % "logback-classic" % "1.0.13",
    
      "org.scala-lang" % "scala-library" % BuildSettings.buildScalaVersion,
    
      "org.apache.zookeeper" % "zookeeper" % "3.4.6" notTransitive() exclude("org.slf4j", "slf4j-log4j12"),

      "org.scalatest" %% "scalatest" % "1.9.1" % "test",
      "org.specs2" %% "specs2" % "1.12.3" % "test",
      "org.mockito" % "mockito-core" % "1.9.5" % "test "
    )
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
          Some("releases"  at nexus + "service/local/staging/deploy/maven2")
      },
      licenses := Seq("Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
      homepage := Some(url("https://github.com/agolubev/playinzoo")),
      publishArtifact in Test := false,
      pomExtra := (
        <scm>
          <url>git://github.com/agolubev/playinzoo.git</url>
          <connection>scm:git://github.com/agolubev/playinzoo.git</connection>
        </scm>
          <developers>
            <developer>
              <id>agolubev</id>
              <name>Alexander Golubev</name>
              <url>http://github.com/agolubev</url>
            </developer>
          </developers>)
     )
    
  }

  object BuildSettings {
    val buildOrganization = "com.github.agolubev"
    val buildVersion = "0.1"
    val buildScalaVersion = "2.10.3"
    val buildSbtVersion = "0.13"
    val buildSettings = Defaults.defaultSettings ++ Seq(
      organization := buildOrganization,
      version := buildVersion,
      scalaVersion := buildScalaVersion
    )
  }

}
