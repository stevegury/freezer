name := "freezer"

version := "0.1-SNAPSHOT"

scalaVersion := "2.9.2"

resolvers += "Twitter" at "http://maven.twttr.com"

resolvers += "Coda Hale" at "http://repo.codahale.com"

libraryDependencies += "com.twitter" % "util-core" % "5.3.7"

libraryDependencies += "com.amazonaws" % "aws-java-sdk" % "1.3.18"

libraryDependencies += "com.codahale" % "jerkson_2.9.1" % "0.5.0"
