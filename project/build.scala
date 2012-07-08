import sbt._,Keys._
import com.typesafe.startscript.StartScriptPlugin._

object build extends Build{

  lazy val buildSettings =
    Defaults.defaultSettings ++ Seq(
      organization := "com.herokuapp.xtend",
      version := "0.1.0-SNAPSHOT",
      scalacOptions := Seq("-deprecation", "-unchecked"),
      scalaVersion := "2.9.2",
      shellPrompt in ThisBuild := { state =>
        Project.extract(state).currentRef.project + "> "
      },
      initialCommands in console := Seq(
        "scalaz","Scalaz","com.herokuapp.xtend"
      ).map{"import " + _ + "._;"}.mkString,
      licenses := Seq("MIT License" -> url("http://www.opensource.org/licenses/mit-license.php"))
    )

  lazy val root = Project(
    "root",
    file("."),
    settings = buildSettings ++ startScriptForClassesSettings ++ Seq(
    )
  )aggregate(server,client,common,jointest)

  lazy val jointest = Project(
    "jointest",
    file("jointest"),
    settings = buildSettings ++ Seq(
    )
  )dependsOn(server,client)

  lazy val common = Project(
    "common",
    file("common"),
    settings = buildSettings ++ Seq(
      libraryDependencies ++= Seq(
        "org.scalaz" %% "scalaz-core" % "6.0.4"
      )
    )
  )

  val u = "0.6.3"
  val xtendVersion = "2.3.0"

  lazy val server = Project(
    "server",
    file("server"),
    settings = buildSettings ++ startScriptForClassesSettings ++ Seq(
      libraryDependencies ++= Seq("filter","jetty","json").map{n=>
        "net.databinder" %% ("unfiltered-"+n) % u
      },
      libraryDependencies ++= Seq(
        "net.databinder" %% "unfiltered-spec" % u % "test",
        "log4j" % "log4j" % "1.2.16" % "compile",
        "org.eclipse.xtend" % "org.eclipse.xtend.lib" % xtendVersion,
        "org.eclipse.xtext" % "org.eclipse.xtext.xbase.lib" % xtendVersion,
        "org.eclipse.xtend" % "org.eclipse.xtend.standalone" % xtendVersion,
        "org.eclipse.emf" % "codegen" % "2.2.3"
      ),
      libraryDependencies <+= sbtDependency,
      resolvers ++= Seq(
        "http://fornax-platform.org/nexus/content/groups/public/",
        "https://oss.sonatype.org/content/repositories/releases/",
        "http://build.eclipse.org/common/xtend/maven/",
        "http://maven.eclipse.org/nexus/content/groups/public/"
      ).map{u => u at u},
      sourceGenerators in Compile <+= (sourceManaged in Compile).map{xtendVersionInfoGenerate},
      retrieveManaged := true
    )
  )dependsOn(common)

  def xtendVersionInfoGenerate(dir:File):Seq[File] = {
    val src =
      """package com.herokuapp.xtend
        |
        |object XtendVersion{
        |  def apply() = "%s"
        |}""".format(xtendVersion).stripMargin
    println(src)
    val file = dir / "XtendVersion.scala"
    IO.write(file,src)
    Seq(file)
  }

  lazy val client = Project(
    "client",
    file("client"),
    settings = buildSettings ++ Seq(
      libraryDependencies ++= Seq(
        "org.scalaj" %% "scalaj-http" % "0.3.1",
        "net.liftweb" % "lift-json_2.9.1" % "2.4"
      )
    )
  )dependsOn(common)

}

