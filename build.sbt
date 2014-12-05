import AssemblyKeys._

assemblySettings

name := "freezer"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.4"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-glacier" % "1.9.8",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.4.3",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test"
)
