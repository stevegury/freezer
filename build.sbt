import AssemblyKeys._

assemblySettings

name := "freezer"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.4"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-glacier" % "1.9.8",
  "com.twitter" %% "util-core" % "6.22.1",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.4.3",
  "org.specs2" %% "specs2-core" % "2.4.13" % "test",
  "org.specs2" %% "specs2-mock" % "2.4.13" % "test"
)
