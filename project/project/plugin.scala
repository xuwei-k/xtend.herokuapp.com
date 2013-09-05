import sbt._

object Plugin extends Build {
  lazy val root = Project("root", file(".")).dependsOn(
    uri("git://github.com/xuwei-k/sbt-start-script")
  )
}
