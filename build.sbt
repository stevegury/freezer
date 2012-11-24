import AssemblyKeys._

assemblySettings

name := "freezer"

version := "0.1-SNAPSHOT"

scalaVersion := "2.9.2"

jarName in assembly := "freezer.jar"

mainClass in assembly := Some("com.github.stevegury.freezer.Freezer")

resolvers ++= Seq(
  "Twitter" at "http://maven.twttr.com",
  "Coda Hale" at "http://repo.codahale.com",
  "Sonatype"  at "http://oss.sonatype.org/content/repositories/releases"
)

libraryDependencies ++= Seq(
  "com.twitter" % "util-core" % "5.3.7",
  "com.amazonaws" % "aws-java-sdk" % "1.3.18",
  "com.codahale" % "jerkson_2.9.1" % "0.5.0",
  "org.specs2" %% "specs2" % "1.12.3" % "test",
  "org.mockito" % "mockito-all" % "1.9.5" % "test"
)
