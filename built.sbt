name := "maxmind-geoip2-scala"

organization := "com.sanoma.cda"

version := "1.2.2-SNAPSHOT"

scalaVersion := "2.10.3"

// twitter util doesn't have 2.9.3
crossScalaVersions := Seq("2.9.2", "2.10.3")

libraryDependencies ++= Seq(
  "com.maxmind.geoip2" % "geoip2" % "0.7.0",
  "com.twitter" %% "util-collection" % "6.8.0",
  "org.scalacheck" %% "scalacheck" % "1.10.0" % "test",
  "org.scalatest" %% "scalatest" % "1.9.1" % "test"
)

